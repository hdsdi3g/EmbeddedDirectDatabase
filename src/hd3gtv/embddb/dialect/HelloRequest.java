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
 * Copyright (C) hdsdi3g for hd3g.tv 8 janv. 2017
 * 
*/
package hd3gtv.embddb.dialect;

import java.io.IOException;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;

import hd3gtv.embddb.PoolManager;
import hd3gtv.embddb.socket.Node;
import hd3gtv.embddb.socket.RequestBlock;

public class HelloRequest extends Request<Void> {
	
	private static Logger log = Logger.getLogger(HelloRequest.class);
	
	public HelloRequest(RequestHandler request_handler) {
		super(request_handler);
	}
	
	public String getHandleName() {
		return "hello";
	}
	
	public void onRequest(RequestBlock block, Node source_node) {
		try {
			source_node.setDistantDate(Long.valueOf(block.getByName("now").getDatasAsString()));
			source_node.setUUIDRef(UUID.fromString(block.getByName("uuid").getDatasAsString()));
			source_node.setLocalServerNodeAddresses(pool_manager.getSimpleGson().fromJson(block.getByName("listen_on").getDatasAsString(), PoolManager.type_InetSocketAddress_String));
			
			pool_manager.getNode_scheduler().add(source_node, source_node.getScheduledAction());
		} catch (IOException e) {
			log.error("Node " + source_node + " return invalid hello request... disconnect it", e);
			source_node.close(getClass());
		}
	}
	
	public RequestBlock createRequest(Void options) {
		RequestBlock b = new RequestBlock(getHandleName());
		b.createEntry("now", String.valueOf(System.currentTimeMillis()));
		b.createEntry("uuid", pool_manager.getUUIDRef().toString());
		b.createEntry("listen_on", pool_manager.getSimpleGson().toJson(pool_manager.getListenedServerAddress().collect(Collectors.toList())));
		return b;
	}
	
	protected boolean isCloseChannelRequest(Void options) {
		return false;
	}
	
}
