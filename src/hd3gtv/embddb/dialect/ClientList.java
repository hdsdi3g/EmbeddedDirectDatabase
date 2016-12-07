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
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.stream.Stream;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import hd3gtv.embddb.ClientUnit;
import hd3gtv.embddb.PoolManager;
import hd3gtv.embddb.network.RequestBlock;
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
	
	/**
	 * @return JsonArray String
	 */
	private String serializing(Stream<InetSocketAddress> list) {
		return list.map(a -> {
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
		}).toString();
	}
	
	private JsonParser parser = new JsonParser();
	
	private ArrayList<InetSocketAddress> deserializing(String json_array_list) {
		JsonArray ja = parser.parse(json_array_list).getAsJsonArray();
		
		ArrayList<InetSocketAddress> client_list = new ArrayList<>(ja.size() + 1);
		
		ja.forEach(je -> {
			JsonObject jo = je.getAsJsonObject();
			client_list.add(new InetSocketAddress(jo.get("ip").getAsString(), jo.get("port").getAsInt()));
		});
		
		return client_list;
	}
	
	public ServerSayToClient getServerSentenceToSendToClient(InetAddress client, ArrayList<RequestBlock> send) {
		
		return new ServerSayToClient() {
			
			public ArrayList<RequestBlock> getBlocksToSendToClient() {
				// send.get(0) TODO get original distant client list, and discriminate it
				RequestBlock rb = new RequestBlock("clientlistresponse", serializing(pool_manager.getAllCurrentConnected().stream()));
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
