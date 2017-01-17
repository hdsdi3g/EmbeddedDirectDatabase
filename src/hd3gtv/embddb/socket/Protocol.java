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
package hd3gtv.embddb.socket;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
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

import hd3gtv.embddb.dialect.Version;
import hd3gtv.embddb.tools.Hexview;

public class Protocol {
	private static final Logger log = Logger.getLogger(Protocol.class);
	
	public static final Version VERSION = Version.V1;
	public static final Charset UTF8 = Charset.forName("UTF-8");
	public static final int BUFFER_SIZE = 0xFFFF;
	
	public static boolean DUMP_ZIP_BINARIES_TO_FILES = Boolean.parseBoolean(System.getProperty(Protocol.class.getName().toLowerCase() + ".dump.hex.file", "false"));
	
	/**
	 * It Needs trace log enabled.
	 */
	public static boolean DISPLAY_HEXDUMP = Boolean.parseBoolean(System.getProperty(Protocol.class.getName().toLowerCase() + ".dump.hex", "false"));
	
	private IvParameterSpec salt;
	private SecretKey skeySpec;
	
	private SocketHandlerReader handler_reader;
	private SocketHandlerWriter handler_writer;
	private SocketHandlerWriterCloser handler_writer_closer;
	
	public Protocol(String master_password_key) throws NoSuchAlgorithmException, NoSuchProviderException, UnsupportedEncodingException {
		if (master_password_key == null) {
			throw new NullPointerException("\"master_password_key\" can't to be null");
		}
		if (master_password_key.isEmpty()) {
			throw new NullPointerException("\"master_password_key\" can't to be empty");
		}
		handler_reader = new SocketHandlerReader();
		handler_writer = new SocketHandlerWriter();
		handler_writer_closer = new SocketHandlerWriterCloser();
		
		MessageDigest md = MessageDigest.getInstance("SHA-256", "BC");
		byte[] key = md.digest(master_password_key.getBytes("UTF-8"));
		
		skeySpec = new SecretKeySpec(key, "AES");
		salt = new IvParameterSpec(key, 0, 16);
	}
	
	public int getDefaultTCPPort() {
		return 9160;
	}
	
	public SocketHandlerReader getHandlerReader() {
		return handler_reader;
	}
	
	public SocketHandlerWriter getHandlerWriter(boolean close_channel_after_send) {
		if (close_channel_after_send) {
			return handler_writer_closer;
		} else {
			return handler_writer;
		}
	}
	
	public byte[] encrypt(byte[] cleared_datas, int pos, int len) throws GeneralSecurityException {
		return encryptDecrypt(cleared_datas, pos, len, Cipher.ENCRYPT_MODE);
	}
	
	public byte[] decrypt(byte[] crypted_datas, int pos, int len) throws GeneralSecurityException {
		return encryptDecrypt(crypted_datas, pos, len, Cipher.DECRYPT_MODE);
	}
	
	private byte[] encryptDecrypt(byte[] datas, int pos, int len, int mode) throws GeneralSecurityException {
		Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding", "BC");
		cipher.init(mode, skeySpec, salt);
		return cipher.doFinal(datas, pos, len);
	}
	
	public ArrayList<RequestBlock> decompressBlocks(byte[] uncrypted_content) throws IOException {
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
	public byte[] compressBlocks(ArrayList<RequestBlock> response_blocks) throws IOException {
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
	
}
