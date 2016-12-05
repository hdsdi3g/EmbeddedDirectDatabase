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
package hd3gtv.embddb.dialect;

import java.net.InetAddress;
import java.util.ArrayList;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import hd3gtv.embddb.ClientUnit;
import hd3gtv.embddb.PoolManager;
import hd3gtv.embddb.network.RequestBlock;
import hd3gtv.embddb.tools.ArrayWrapper;
import hd3gtv.internaltaskqueue.ParametedWithResultProcedure;

public class ClientList implements Dialog<Void, Void> {
	// ArrayList<InetSocketAddress>
	
	private PoolManager pool_manager;
	private CLRequest request;
	
	public ClientList(PoolManager pool_manager) {
		this.pool_manager = pool_manager;
		if (pool_manager == null) {
			throw new NullPointerException("\"pool_manager\" can't to be null");
		}
		
		/*request = new CLRequest(p -> {
			
			return ArrayWrapper.asArrayList(new RequestBlock("clientaddrlist", datas));
		}, null);*/
	}
	
	private class CLRequest extends ClientSayToServer<Void> {
		
		public CLRequest(ParametedWithResultProcedure<ArrayList<RequestBlock>, Void> callback) {
			super(callback);
		}
		
		public ArrayList<RequestBlock> getBlocksToSendToServer() {
			return ArrayWrapper.asArrayList(new RequestBlock("clientlistrequest", "[]"));// TODO set to empty data
		}
		
	}
	
	@Override
	public ClientSayToServer<Void> getClientSentenceToSendToServer(ClientUnit client, Void request) {
		// TODO Auto-generated method stub
		return null;
	}
	
	public ServerSayToClient getServerSentenceToSendToClient(InetAddress client, ArrayList<RequestBlock> send) {
		
		return new ServerSayToClient() {
			
			public ArrayList<RequestBlock> getBlocksToSendToClient() {
				RequestBlock rb = new RequestBlock("clientlistresponse", pool_manager.getAllCurrentConnected().stream().map(a -> {
					JsonObject jo = new JsonObject();
					jo.addProperty("ip", a.getAddress().getHostAddress());
					jo.addProperty("port", a.getPort());
					return jo;
				}).collect(() -> {
					return new JsonArray();
				}, (jsonarray, jsonobject) -> {
					jsonarray.add(jsonobject);
				}, (jsonarray1, jsonarray2) -> {
					jsonarray1.addAll(jsonarray2);
				}).toString());
				
				return ArrayWrapper.asArrayList(rb);
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
