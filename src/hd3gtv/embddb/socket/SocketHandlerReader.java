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

import hd3gtv.embddb.socket.Node.ChannelBucket;

class SocketHandlerReader implements SocketHandler {
	
	private static final Logger log = Logger.getLogger(SocketHandlerReader.class);
	
	public void completed(Integer size, ChannelBucket bucket) {
		if (size < 1) {
			bucket.close(NodeCloseReason.EMPTY_SOCKET_DATAS, getClass());
			return;
		}
		
		if (log.isTraceEnabled()) {
			log.trace("Recevied from " + bucket + " " + size + " bytes");
		}
		
		if (Protocol.DISPLAY_HEXDUMP) {
			bucket.dump("Crypted recivied content from client");
		}
		
		try {
			bucket.doProcessReceviedDatas();
		} catch (Exception e) {
			failed(e, bucket);
		}
	}
	
	public void failed(Throwable e, ChannelBucket bucket) {
		log.error("Channel " + bucket + " failed", e);
		bucket.close(NodeCloseReason.SOCKET_HANDLER_EXCEPTION, getClass());
	}
	
}