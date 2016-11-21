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

import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class RequestBlock {
	
	private String name;
	private byte[] datas;
	private int len;
	private long date;
	
	private RequestBlock() {
	}
	
	void exportToZip(ZipOutputStream zipdatas) throws IOException {
		ZipEntry entry = new ZipEntry(name);
		entry.setTime(date);
		entry.setSize(len);
		zipdatas.putNextEntry(entry);
		zipdatas.write(datas, 0, len);
		zipdatas.closeEntry();
	}
	
	static RequestBlock importFromZip(ZipEntry entry, ZipInputStream zipdatas) throws IOException {
		RequestBlock block = new RequestBlock();
		block.datas = new byte[(int) entry.getSize()];
		zipdatas.read(block.datas);
		block.date = entry.getTime();
		block.len = block.datas.length;
		block.name = entry.getName();
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
	
}
