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

import java.util.ArrayList;

import org.apache.log4j.Logger;

import hd3gtv.embddb.PoolManager;
import hd3gtv.embddb.socket.Node;
import hd3gtv.embddb.socket.Protocol;
import hd3gtv.embddb.socket.RequestBlock;
import hd3gtv.embddb.tools.ArrayWrapper;

public class HelloRequest extends Request<Void> {
	
	private static Logger log = Logger.getLogger(DisconnectRequest.class);
	
	public HelloRequest(PoolManager pool_manager) {
		super(pool_manager);
	}
	
	public String getHandleName() {
		return "hello";
	}
	
	public void onRequest(ArrayList<RequestBlock> blocks, Node source_node) {
		Version distant = Version.resolveFromString(blocks.get(0).getDatasAsString());
		
		if (distant.equals(Protocol.VERSION)) {
			request_handler.getRequestByClass(WelcomeRequest.class).sendRequest(Protocol.VERSION, source_node);
		} else {
			log.error("Node " + source_node + " as a version problem. This = " + Protocol.VERSION + " and distant node = " + distant);
			ErrorRequest er = request_handler.getRequestByClass(ErrorRequest.class);
			er.directSendError(source_node, "Version mismatch (" + distant + " vs " + Protocol.VERSION + ")", getClass(), true);
		}
	}
	
	public ArrayList<RequestBlock> createRequest(Void options) {
		return ArrayWrapper.asArrayList(new RequestBlock(getHandleName(), Protocol.VERSION.toString()));
	}
	
	protected boolean isCloseChannelRequest(Void options) {
		return false;
	}
	
}
