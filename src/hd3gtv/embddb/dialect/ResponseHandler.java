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
package hd3gtv.embddb.dialect;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.log4j.Logger;

import hd3gtv.embddb.PoolManager;
import hd3gtv.embddb.socket.ChannelBucketManager.ChannelBucket;
import hd3gtv.embddb.socket.RequestBlock;

public class ResponseHandler {
	
	private static final Logger log = Logger.getLogger(ResponseHandler.class);
	private PoolManager pool_manager;
	
	public ResponseHandler(PoolManager pool_manager) {
		this.pool_manager = pool_manager;
		if (pool_manager == null) {
			throw new NullPointerException("\"pool_manager\" can't to be null");
		}
	}
	
	public void onReceviedNewBlocks(ArrayList<RequestBlock> blocks, InetAddress source, ChannelBucket bucket) throws WantToCloseLink {
		if (log.isTraceEnabled()) {
			AtomicInteger all_size = new AtomicInteger(0);
			blocks.forEach(block -> {
				all_size.addAndGet(block.getLen());
			});
			if (blocks.size() == 1) {
				log.trace("Get from " + source + " " + all_size.get() + " bytes of datas on 1 block.");
			} else {
				log.trace("Get from " + source + " " + all_size.get() + " bytes of datas on " + blocks.size() + " blocks.");
			}
		}
		
		// TODO Auto-generated method stub
		// pool_manager.getClientToServerRequestFirstValid(blocks);
		// .getServerSentenceToSendToClient(source, blocks).getBlocksToSendToClient();
	}
	
}
