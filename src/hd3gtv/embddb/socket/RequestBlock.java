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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;

import hd3gtv.embddb.tools.Hexview;

public final class RequestBlock {
	
	private static final Logger log = Logger.getLogger(RequestBlock.class);
	private String name;
	private byte[] datas;
	private int len;
	private long date;
	
	private RequestBlock() {
	}
	
	public RequestBlock(String name, byte[] datas, long date) {
		this.name = name;
		if (name == null) {
			throw new NullPointerException("\"name\" can't to be null");
		}
		this.datas = datas;
		if (datas == null) {
			throw new NullPointerException("\"datas\" can't to be null");
		}
		this.date = date;
		len = datas.length;
	}
	
	public RequestBlock(String name, byte[] datas) {
		this(name, datas, System.currentTimeMillis());
	}
	
	public RequestBlock(String name, String datas) {
		this(name, datas.getBytes(Protocol.UTF8), System.currentTimeMillis());
	}
	
	void exportToZip(ZipOutputStream zipdatas) throws IOException {
		ZipEntry entry = new ZipEntry(name);
		entry.setTime(date);
		// entry.setSize(len);
		zipdatas.putNextEntry(entry);
		
		if (Protocol.DISPLAY_HEXDUMP) {
			Hexview.tracelog(datas, 0, len, log, "Raw block entry \"" + name + "\"");
		}
		
		zipdatas.write(datas, 0, len);
		zipdatas.flush();
		zipdatas.closeEntry();
	}
	
	static RequestBlock importFromZip(ZipEntry entry, ZipInputStream zipdatas) throws IOException {
		RequestBlock block = new RequestBlock();
		
		ByteArrayOutputStream bias = new ByteArrayOutputStream(Protocol.BUFFER_SIZE);
		IOUtils.copy(zipdatas, bias);
		
		block.datas = bias.toByteArray();
		block.len = block.datas.length;
		block.date = entry.getTime();
		block.name = entry.getName();
		
		if (Protocol.DISPLAY_HEXDUMP) {
			Hexview.tracelog(block.datas, log, "Raw block entry \"" + block.name + "\"");
		}
		
		return block;
	}
	
	public byte[] getDatas() {
		return datas;
	}
	
	public long getDate() {
		return date;
	}
	
	public int getLen() {
		return len;
	}
	
	public String getName() {
		return name;
	}
	
	public String getDatasAsString() {
		return new String(datas, 0, len, Protocol.UTF8);
	}
}
