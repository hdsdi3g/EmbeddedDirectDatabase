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
package hd3gtv.embddb.dialect.dialogs;

import java.util.ArrayList;

import hd3gtv.embddb.socket.RequestBlock;
import hd3gtv.internaltaskqueue.ParametedWithResultProcedure;

/**
 * Client side.
 * O distant server response somme datas, and this class will converted to O
 */
public abstract class ClientSayToServer<O> {
	
	protected final ParametedWithResultProcedure<ArrayList<RequestBlock>, O> callback;
	
	public ClientSayToServer(ParametedWithResultProcedure<ArrayList<RequestBlock>, O> callback) {
		this.callback = callback;
		if (callback == null) {
			throw new NullPointerException("\"callback\" can't to be null");
		}
	}
	
	public final O clientReceviedBlocksFromServer(ArrayList<RequestBlock> blocks) throws Exception {
		return callback.process(blocks);
	}
	
	public abstract ArrayList<RequestBlock> getBlocksToSendToServer();
	
}
