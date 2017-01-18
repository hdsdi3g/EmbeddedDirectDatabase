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
 * Copyright (C) hdsdi3g for hd3g.tv 18 janv. 2017
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

public final class RequestEntry {
	
	private static final Logger log = Logger.getLogger(RequestEntry.class);
	
	private String name;
	private byte[] datas;
	private long date;
	private int len;
	
	RequestEntry(String name, byte[] datas, long date) {
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
	
	RequestEntry(ZipEntry entry, ZipInputStream zipdatas) throws IOException {
		ByteArrayOutputStream bias = new ByteArrayOutputStream(Protocol.BUFFER_SIZE);
		IOUtils.copy(zipdatas, bias);
		
		datas = bias.toByteArray();
		len = datas.length;
		date = entry.getTime();
		name = entry.getName();
		
		Hexview.tracelog(datas, 0, len, log, "Get datas from zip: \"" + name + "\"");
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
	
	void toZip(ZipOutputStream zipdatas) throws IOException {
		Hexview.tracelog(datas, 0, len, log, "Add entry to Zip: \"" + name + "\"");
		
		ZipEntry entry = new ZipEntry(name);
		entry.setTime(date);
		// entry.setSize(len);
		zipdatas.putNextEntry(entry);
		zipdatas.write(datas, 0, len);
		zipdatas.flush();
		zipdatas.closeEntry();
	}
	
}
