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

import hd3gtv.embddb.socket.Node;
import hd3gtv.embddb.socket.NodeCloseReason;
import hd3gtv.embddb.socket.RequestBlock;
import hd3gtv.embddb.tools.ArrayWrapper;

public class DisconnectRequest extends Request<Void> {
	
	private static Logger log = Logger.getLogger(DisconnectRequest.class);
	
	DisconnectRequest(RequestHandler request_handler) {
		super(request_handler);
	}
	
	public String getHandleName() {
		return "disconnectme";
	}
	
	public void onRequest(ArrayList<RequestBlock> blocks, Node node) {
		log.info("Distant node " + node + " ask to to close");
		node.getChannelbucket().close(NodeCloseReason.EXTERNAL_REQUEST_DISCONNECT, getClass());
	}
	
	public ArrayList<RequestBlock> createRequest(Void options) {
		return ArrayWrapper.asArrayList(new RequestBlock(getHandleName(), ""));
	}
	
	protected boolean isCloseChannelRequest(Void options) {
		return true;
	}
	
}
