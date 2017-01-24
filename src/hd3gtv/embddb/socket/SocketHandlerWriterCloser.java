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
 * Copyright (C) hdsdi3g for hd3g.tv 6 janv. 2017
 * 
*/
package hd3gtv.embddb.socket;

import org.apache.log4j.Logger;

class SocketHandlerWriterCloser extends SocketHandlerWriter {
	
	private static Logger log = Logger.getLogger(SocketHandlerWriterCloser.class);
	
	public void completed(Integer size, ChannelBucket bucket) {
		showLogs(size, bucket);
		log.info("Manual close socket after send datas to other node " + bucket.toString());
		bucket.close(getClass());
	}
	
}