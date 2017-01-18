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

import org.jfree.util.Log;

import hd3gtv.embddb.socket.Node;
import hd3gtv.embddb.socket.RequestBlock;

public class ErrorRequest extends Request<ErrorReturn> {
	
	public ErrorRequest(RequestHandler request_handler) {
		super(request_handler);
	}
	
	public String getHandleName() {
		return "error";
	}
	
	public void onRequest(RequestBlock blocks, Node source_node) {
		try {
			ErrorReturn error = ErrorReturn.fromJsonString(pool_manager, blocks.getByName("message").getDatasAsString());
			source_node.onErrorReturnFromNode(error);
		} catch (IOException e) {
			Log.error("Can't get error message from " + source_node, e);
		}
	}
	
	public RequestBlock createRequest(ErrorReturn options) {
		return new RequestBlock(getHandleName()).createEntry("message", ErrorReturn.toJsonString(pool_manager, options));
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
