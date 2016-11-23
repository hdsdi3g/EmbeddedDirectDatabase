/*
 * This file is part of MyDMAM.
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * Copyright (C) hdsdi3g for hd3g.tv 21 nov. 2016
 * 
*/
package hd3gtv.embddb;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;

public class Protocol { // TODO rename to low level
	
	public static final int BUFFER_SIZE = 0xFFFF;
	
	public static boolean DUMP_ZIP_BINARIES_TO_FILES = Boolean.parseBoolean(System.getProperty(Protocol.class.getName().toLowerCase() + ".dump.hex.file", "false"));
	
	/**
	 * It Needs trace log enabled.
	 */
	public static boolean DISPLAY_HEXDUMP = Boolean.parseBoolean(System.getProperty(Protocol.class.getName().toLowerCase() + ".dump.hex", "true"));
	
	private IvParameterSpec salt;
	private SecretKey skeySpec;
	private static final Logger log = Logger.getLogger(Protocol.class);
	
	public Protocol(String master_password_key) throws NoSuchAlgorithmException, NoSuchProviderException, UnsupportedEncodingException {
		if (master_password_key == null) {
			throw new NullPointerException("\"master_password_key\" can't to be null");
		}
		if (master_password_key.isEmpty()) {
			throw new NullPointerException("\"master_password_key\" can't to be empty");
		}
		
		MessageDigest md = MessageDigest.getInstance("SHA-256", "BC");
		byte[] key = md.digest(master_password_key.getBytes("UTF-8"));
		
		skeySpec = new SecretKeySpec(key, "AES");
		salt = new IvParameterSpec(key, 0, 16);
	}
	
	ByteBuffer encrypt(ByteBuffer buffer_source) throws GeneralSecurityException {
		return encryptDecrypt(buffer_source, Cipher.ENCRYPT_MODE);
	}
	
	ByteBuffer decrypt(ByteBuffer buffer_source) throws GeneralSecurityException {
		return encryptDecrypt(buffer_source, Cipher.DECRYPT_MODE);
	}
	
	private ByteBuffer encryptDecrypt(ByteBuffer buffer_source, int mode) throws GeneralSecurityException {
		Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding", "BC");
		cipher.init(mode, skeySpec, salt);
		
		ByteBuffer dest = ByteBuffer.allocateDirect(cipher.getOutputSize(buffer_source.remaining()));
		
		// TODO Note: this method should be copy-safe, which means the input and output buffers can reference the same block of memory and no unprocessed input data is overwritten when the result is
		// copied into the output buffer.
		cipher.doFinal(buffer_source, dest);
		return dest;
	}
	
	ArrayList<RequestBlock> decompressBlocks(byte[] uncrypted_content) throws IOException {
		if (DISPLAY_HEXDUMP) {
			Hexview.tracelog(uncrypted_content, log, "Uncrypted zipped content");
		}
		
		if (DUMP_ZIP_BINARIES_TO_FILES) {
			File temp = new File("Protocol-Decmpr-" + (new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS")).format(new Date()) + ".zip");
			log.info("Write zip item to file " + temp.getAbsolutePath());
			FileUtils.writeByteArrayToFile(temp, uncrypted_content);
		}
		
		ArrayList<RequestBlock> result = new ArrayList<>();
		ByteArrayInputStream inputstream_client_request = new ByteArrayInputStream(uncrypted_content);
		ZipInputStream request_zip = new ZipInputStream(inputstream_client_request);
		
		ZipEntry entry;
		while ((entry = request_zip.getNextEntry()) != null) {
			result.add(RequestBlock.importFromZip(entry, request_zip));
		}
		request_zip.close();
		
		return result;
	}
	
	/**
	 * @throws IOException if size is > EDDBNode.BUFFER_SIZE
	 */
	byte[] compressBlocks(ArrayList<RequestBlock> response_blocks) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream(BUFFER_SIZE);
		
		ZipOutputStream zos = new ZipOutputStream(baos);
		zos.setComment("EmbDB");
		zos.setLevel(3);
		
		response_blocks.forEach(block -> {
			try {
				block.exportToZip(zos);
			} catch (IOException e) {
				log.error("Can't add to zip", e);
			}
		});
		zos.flush();
		zos.finish();
		zos.close();
		
		if (baos.size() > BUFFER_SIZE) {
			throw new IOException("Max size for message (" + baos.size() + " bytes > " + BUFFER_SIZE + " bytes)");
		}
		
		if (DISPLAY_HEXDUMP) {
			Hexview.tracelog(baos.toByteArray(), log, "Uncrypted zipped content");
		}
		
		if (DUMP_ZIP_BINARIES_TO_FILES) {
			File temp = new File("Protocol-Cmpr-" + (new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS")).format(new Date()) + ".zip");
			log.info("Write zip item to file " + temp.getAbsolutePath());
			FileUtils.writeByteArrayToFile(temp, baos.toByteArray());
		}
		
		return baos.toByteArray();
	}
	
	public static final Charset UTF8 = Charset.forName("UTF-8");
	
	// TODO add and check protocol version, with another block.
	RequestBlock createHello() {
		return new RequestBlock("hello", "Hello from X".getBytes(UTF8), System.currentTimeMillis()); // TODO set X...
	}
	
	RequestBlock createWelcome() {
		return new RequestBlock("welcome", "Welcome from X".getBytes(UTF8), System.currentTimeMillis()); // TODO set X...
	}
	
}
