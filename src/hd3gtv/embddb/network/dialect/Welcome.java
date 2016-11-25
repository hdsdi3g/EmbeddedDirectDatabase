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

import java.io.IOException;
import java.util.ArrayList;

import hd3gtv.embddb.network.Protocol;
import hd3gtv.embddb.network.RequestBlock;
import hd3gtv.embddb.tools.ArrayWrapper;

public class Welcome implements ServerSayToClient {
	
	public ArrayList<RequestBlock> getBlocksToSendToClient() {
		return ArrayWrapper.asArrayList(new RequestBlock("welcome", "Welcome from EmbDDB"), new RequestBlock("version", Protocol.VERSION.toString()));
	}
	
	public void checkMatchVersionWithServer(Protocol protocol, ArrayList<RequestBlock> blocks) throws IOException {
		if (blocks.get(0).getName().equals("error")) {
			throw new IOException("Error from server side " + blocks.get(0).getDatasAsString());
		}
		if (blocks.size() != 2) {
			throw new IOException("Bad block count " + blocks.size());
		}
		if (Version.resolveFromString(new String(blocks.get(1).getDatas())) != Protocol.VERSION) {
			throw new IOException("Bad version " + new String(blocks.get(1).getDatas()));
		}
	}
	
}
