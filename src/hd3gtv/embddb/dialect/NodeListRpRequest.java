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
import hd3gtv.embddb.socket.RequestBlock;

public class NodeListRpRequest extends Request<Void> {
	
	private static Logger log = Logger.getLogger(NodeListRpRequest.class);
	
	public NodeListRpRequest(PoolManager pool_manager) {
		super(pool_manager);
	}
	
	public String getHandleName() {
		return "nodelistresponse";
	}
	
	@Override
	public void onRequest(ArrayList<RequestBlock> blocks, Node source_node) {
		// TODO Auto-generated method stub
		
	}
	
	@Override
	public ArrayList<RequestBlock> createRequest(Void options, Node dest_node) {
		// TODO Auto-generated method stub
		return null;
	}
	
}
