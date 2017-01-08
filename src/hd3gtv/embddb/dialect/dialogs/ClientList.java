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
 * Copyright (C) hdsdi3g for hd3g.tv 5 déc. 2016
 * 
*/
package hd3gtv.embddb.dialect.dialogs;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;

import hd3gtv.embddb.ClientUnit;
import hd3gtv.embddb.PoolManager;
import hd3gtv.embddb.socket.RequestBlock;
import hd3gtv.embddb.tools.ArrayWrapper;
import hd3gtv.internaltaskqueue.ParametedWithResultProcedure;

public class ClientList implements Dialog<ArrayList<InetSocketAddress>, ArrayList<InetSocketAddress>> {
	
	private PoolManager pool_manager;
	
	public ClientList(PoolManager pool_manager) {
		this.pool_manager = pool_manager;
		if (pool_manager == null) {
			throw new NullPointerException("\"pool_manager\" can't to be null");
		}
	}
	
	public ClientSayToServer<ArrayList<InetSocketAddress>> getClientSentenceToSendToServer(ClientUnit client, ArrayList<InetSocketAddress> current_connected) {
		return new CLRequest(p -> {
			return deserializing(p.get(0).getDatasAsString());
		}, current_connected);
	}
	
	private class CLRequest extends ClientSayToServer<ArrayList<InetSocketAddress>> {
		
		private ArrayList<InetSocketAddress> current_connected;
		
		public CLRequest(ParametedWithResultProcedure<ArrayList<RequestBlock>, ArrayList<InetSocketAddress>> callback, ArrayList<InetSocketAddress> current_connected) {
			super(callback);
			this.current_connected = current_connected;
			if (current_connected == null) {
				throw new NullPointerException("\"current_connected\" can't to be null");
			}
		}
		
		public ArrayList<RequestBlock> getBlocksToSendToServer() {
			return ArrayWrapper.asArrayList(new RequestBlock("clientlistrequest", serializing(current_connected.stream())));
		}
		
	}
	
	public ServerSayToClient getServerSentenceToSendToClient(InetAddress client, ArrayList<RequestBlock> send) {
		
		return new ServerSayToClient() {
			
			public ArrayList<RequestBlock> getBlocksToSendToClient() {
				ArrayList<InetSocketAddress> addr = deserializing(send.get(0).getDatasAsString());
				
				String response = serializing(pool_manager.getAllCurrentNodes().stream().filter(predicate -> {
					return addr.contains(predicate) == false;
				}));
				
				return ArrayWrapper.asArrayList(new RequestBlock("clientlistresponse", response));
			}
		};
	}
	
	public boolean checkIfClientRequestIsForThisServer(ArrayList<RequestBlock> blocks) {
		return blocks.get(0).getName().equals("clientlistrequest");
	}
	
	public boolean checkIfServerResponseIsForThisClient(ArrayList<RequestBlock> blocks) {
		return blocks.get(0).getName().equals("clientlistresponse");
	}
	
}
