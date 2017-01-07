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
 * Copyright (C) hdsdi3g for hd3g.tv 7 janv. 2017
 * 
*/
package hd3gtv.embddb.dialect;

import java.util.ArrayList;

import hd3gtv.embddb.PoolManager;
import hd3gtv.embddb.socket.Node;
import hd3gtv.embddb.socket.RequestBlock;

public abstract class Request {
	
	protected PoolManager pool_manager;
	
	public Request(PoolManager pool_manager) {
		this.pool_manager = pool_manager;
		if (pool_manager == null) {
			throw new NullPointerException("\"pool_manager\" can't to be null");
		}
	}
	
	public abstract String getHandleName();
	
	public abstract void onRequest(ArrayList<RequestBlock> blocks, Node node);
	
}
