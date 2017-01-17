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

import hd3gtv.embddb.socket.Node;
import hd3gtv.embddb.socket.RequestBlock;
import hd3gtv.embddb.tools.ArrayWrapper;

public class ErrorRequest extends Request<ErrorReturn> {
	
	public ErrorRequest(RequestHandler request_handler) {
		super(request_handler);
	}
	
	public String getHandleName() {
		return "error";
	}
	
	public void onRequest(ArrayList<RequestBlock> blocks, Node source_node) {
		ErrorReturn error = ErrorReturn.fromJsonString(pool_manager, blocks.get(0).getDatasAsString());
		source_node.onErrorReturnFromNode(error);
	}
	
	public ArrayList<RequestBlock> createRequest(ErrorReturn options) {
		return ArrayWrapper.asArrayList(new RequestBlock(getHandleName(), ErrorReturn.toJsonString(pool_manager, options)));
	}
	
	public void directSendError(Node node, String message, Class<?> caller, boolean disconnectme) {
		ErrorRequest er = request_handler.getRequestByClass(ErrorRequest.class);
		ErrorReturn error = new ErrorReturn(node, message, caller, disconnectme);
		er.sendRequest(error, node);
	}
	
	protected boolean isCloseChannelRequest(ErrorReturn options) {
		return false;
	}
	
}
