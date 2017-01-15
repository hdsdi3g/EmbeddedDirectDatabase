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
import java.util.ArrayList;
import java.util.UUID;

import org.apache.log4j.Logger;

import hd3gtv.embddb.PoolManager;
import hd3gtv.embddb.socket.Node;
import hd3gtv.embddb.socket.RequestBlock;
import hd3gtv.embddb.tools.ArrayWrapper;

public class PongRequest extends Request<Void> {
	
	private static Logger log = Logger.getLogger(PongRequest.class);
	
	public PongRequest(PoolManager pool_manager) {
		super(pool_manager);
	}
	
	public String getHandleName() {
		return "pong";
	}
	
	public void onRequest(ArrayList<RequestBlock> blocks, Node source_node) {
		UUID current_uuid = UUID.fromString(blocks.get(0).getDatasAsString());
		
		try {
			source_node.setUUIDRef(current_uuid);
		} catch (IOException e) {
			log.error("Node has changed... disconnect it", e);
			source_node.getChannelbucket().close();
		}
	}
	
	public ArrayList<RequestBlock> createRequest(Void options, Node dest_node) {
		return ArrayWrapper.asArrayList(new RequestBlock(getHandleName(), pool_manager.getUUIDRef().toString()));
	}
	
	protected boolean isCloseChannelRequest(Void options) {
		return false;
	}
	
}
