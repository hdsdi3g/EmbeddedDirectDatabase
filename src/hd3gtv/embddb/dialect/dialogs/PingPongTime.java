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
 * Copyright (C) hdsdi3g for hd3g.tv 27 nov. 2016
 * 
*/
package hd3gtv.embddb.dialect.dialogs;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.UUID;

import hd3gtv.embddb.ClientUnit;
import hd3gtv.embddb.dialect.ClientSayToServer;
import hd3gtv.embddb.dialect.Dialog;
import hd3gtv.embddb.dialect.ServerSayToClient;
import hd3gtv.embddb.socket.RequestBlock;
import hd3gtv.embddb.tools.ArrayWrapper;
import hd3gtv.internaltaskqueue.ParametedWithResultProcedure;

public class PingPongTime implements Dialog<UUID, Long> {
	
	public ServerSayToClient getServerSentenceToSendToClient(InetAddress client, ArrayList<RequestBlock> send) {
		UUID client_reference = UUID.fromString(send.get(0).getDatasAsString());
		
		return () -> {
			return ArrayWrapper.asArrayList(new RequestBlock("pong", client_reference.toString()), new RequestBlock("time", String.valueOf(System.currentTimeMillis())));
		};
	}
	
	public boolean checkIfClientRequestIsForThisServer(ArrayList<RequestBlock> blocks) {
		return blocks.get(0).getName().equals("ping");
	}
	
	public boolean checkIfServerResponseIsForThisClient(ArrayList<RequestBlock> blocks) {
		return blocks.get(0).getName().equals("pong") & blocks.get(1).getName().equals("time");
	}
	
	public ClientSayToServer<Long> getClientSentenceToSendToServer(ClientUnit client, UUID request) {
		Ping p = new Ping(c -> {
			UUID response = UUID.fromString(c.get(0).getDatasAsString());
			if (request.equals(response) == false) {
				return null;
			}
			return Long.parseLong(c.get(1).getDatasAsString());
		}, request);
		return p;
	}
	
	private class Ping extends ClientSayToServer<Long> {
		
		private UUID request;
		
		public Ping(ParametedWithResultProcedure<ArrayList<RequestBlock>, Long> callback, UUID request) {
			super(callback);
			this.request = request;
			if (request == null) {
				throw new NullPointerException("\"request\" can't to be null");
			}
		}
		
		public ArrayList<RequestBlock> getBlocksToSendToServer() {
			return ArrayWrapper.asArrayList(new RequestBlock("ping", request.toString()));
		}
		
	}
	
}
