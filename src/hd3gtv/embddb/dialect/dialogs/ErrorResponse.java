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
 * Copyright (C) hdsdi3g for hd3g.tv 25 nov. 2016
 * 
*/
package hd3gtv.embddb.dialect.dialogs;

import java.util.ArrayList;

import hd3gtv.embddb.dialect.ServerSayToClient;
import hd3gtv.embddb.socket.RequestBlock;
import hd3gtv.embddb.tools.ArrayWrapper;

public class ErrorResponse implements ServerSayToClient {
	
	public String message;
	
	public ErrorResponse(String message) {
		this.message = message;
		if (message == null) {
			throw new NullPointerException("\"message\" can't to be null");
		}
	}
	
	public ArrayList<RequestBlock> getBlocksToSendToClient() {
		return ArrayWrapper.asArrayList(new RequestBlock("error", message));
	}
	
}
