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
 * Copyright (C) hdsdi3g for hd3g.tv 8 déc. 2016
 * 
*/
package hd3gtv.embddb.dialect.dialogs;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;

import org.jfree.util.Log;

import hd3gtv.embddb.ClientUnit;
import hd3gtv.embddb.PoolManager;
import hd3gtv.embddb.socket.RequestBlock;
import hd3gtv.embddb.tools.ArrayWrapper;
import hd3gtv.tools.AddressMaster;

public class DisconnectNode implements Dialog<Void, Void> {
	
	private PoolManager pool;
	
	public DisconnectNode(PoolManager pool) {
		this.pool = pool;
		if (pool == null) {
			throw new NullPointerException("\"pool\" can't to be null");
		}
	}
	
	public ClientSayToServer<Void> getClientSentenceToSendToServer(ClientUnit client, Void request) {
		InetSocketAddress server_addr = pool.getServerListenSocketAddress();
		if (server_addr == null) {
			return null;
		}
		
		final ArrayList<InetSocketAddress> addr = new ArrayList<>(1);
		if (server_addr.getAddress().isAnyLocalAddress()) {
			/**
			 * Server listen in all addresses.
			 */
			pool.getAddressMaster().getAddresses().forEach(a -> {
				addr.add(new InetSocketAddress(a, server_addr.getPort()));
			});
		} else {
			addr.add(server_addr);
		}
		
		return new ClientSayToServer<Void>(thisvoid -> {
			return null;
		}) {
			public ArrayList<RequestBlock> getBlocksToSendToServer() {
				return ArrayWrapper.asArrayList(new RequestBlock("disconnectme", ClientList.serializing(addr.stream())));
			}
		};
	}
	
	public ServerSayToClient getServerSentenceToSendToClient(InetAddress client, ArrayList<RequestBlock> send) {
		ArrayList<InetSocketAddress> client_local_server_addr = ClientList.deserializing(send.get(0).getDatasAsString());
		
		AddressMaster addressmaster = pool.getAddressMaster();
		
		client_local_server_addr.forEach(addr -> {
			if (addressmaster.isMe(addr.getAddress())) {
				/**
				 * Client want disconnect a local addr.
				 */
				if (addressmaster.isMe(client)) {
					/**
					 * Ok, client is really localhost.
					 */
					pool.removeClient(addr);
				} else {
					Log.error("Distant client ask to this server to close a local client: " + client + " want close " + addr);
				}
			} else if (AddressMaster.isLocalAddress(addr.getAddress())) {
				Log.error("Distant client ask to this server to close a local address: " + client + " want close " + addr);
			} else {
				pool.removeClient(addr);
			}
		});
		
		return () -> {
			return ArrayWrapper.asArrayList(new RequestBlock("disconnectyou", "ok, bye"));
		};
	}
	
	public boolean checkIfClientRequestIsForThisServer(ArrayList<RequestBlock> blocks) {
		return blocks.get(0).getName().equals("disconnectme");
	}
	
	public boolean checkIfServerResponseIsForThisClient(ArrayList<RequestBlock> blocks) {
		return blocks.get(0).getName().equals("disconnectyou");
	}
	
}
