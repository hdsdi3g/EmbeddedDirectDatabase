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
 * Copyright (C) hdsdi3g for hd3g.tv 24 nov. 2016
 * 
*/
package hd3gtv.embddb.network.dialect;

import java.util.ArrayList;

import hd3gtv.embddb.network.Protocol;
import hd3gtv.embddb.network.RequestBlock;
import hd3gtv.embddb.tools.ArrayWrapper;
import hd3gtv.embddb.tools.CallableResponder;

public class Welcome extends ServerSayToClient {
	
	public Welcome(CallableResponder<ArrayList<RequestBlock>> callback) {
		super(callback);
	}
	
	public ArrayList<RequestBlock> getBlocksToSendToClient() {
		long date = System.currentTimeMillis();
		
		return ArrayWrapper.asArrayList(new RequestBlock("welcome", "Welcome from EmbDDB".getBytes(Protocol.UTF8), date),
				new RequestBlock("version", Version.V1.toString().getBytes(Protocol.UTF8), date));
	}
	
}
