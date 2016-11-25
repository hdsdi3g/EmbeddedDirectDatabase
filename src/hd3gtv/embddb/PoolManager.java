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
 * Copyright (C) hdsdi3g for hd3g.tv 25 nov. 2016
 * 
*/
package hd3gtv.embddb;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.NoSuchElementException;

import org.apache.log4j.Logger;

import hd3gtv.embddb.network.EDDBNode;
import hd3gtv.embddb.network.Protocol;
import hd3gtv.embddb.network.RequestBlock;
import hd3gtv.embddb.network.dialect.Dialog;
import hd3gtv.embddb.network.dialect.HandCheck;

public class PoolManager {
	
	private static Logger log = Logger.getLogger(PoolManager.class);
	
	private ArrayList<Dialog> dialogs;
	private HashMap<Class<? extends Dialog>, Dialog> dialogs_by_class;
	
	private EDDBNode local_server;
	private ArrayList<ClientUnit> clients;
	private Protocol protocol;
	
	public PoolManager() throws GeneralSecurityException, UnsupportedEncodingException {
		protocol = new Protocol("test"); // TODO conf
		
		dialogs = new ArrayList<>();
		dialogs.add(new HandCheck(protocol));
		
		dialogs_by_class = new HashMap<>(dialogs.size());
		dialogs.forEach(d -> {
			dialogs_by_class.put(d.getClass(), d);
		});
		
		clients = new ArrayList<>();
	}
	
	Protocol getProtocol() {
		return protocol;
	}
	
	public void startServer() throws IOException {
		local_server = new EDDBNode(protocol, (blocks, source) -> {
			try {
				return getClientToServerRequestFirstValid(blocks).getServerSentenceToSendToClient(source, blocks).getBlocksToSendToClient();
			} catch (Exception e) {
				log.warn("Server error", e);
			}
			return null;
		});
		local_server.start();
	}
	
	// TODO close all
	
	/**
	 * Client will be add to current list if can correctly connect to server.
	 */
	public ClientUnit createClient(InetAddress server_addr) throws Exception {
		return new ClientUnit(this, new InetSocketAddress(server_addr, protocol.getDefaultTCPPort()));
	}
	
	/**
	 * Client will be add to current list if can correctly connect to server.
	 */
	public ClientUnit createClient(InetSocketAddress server) throws Exception {
		return new ClientUnit(this, server);
	}
	
	synchronized void declareClient(ClientUnit client) {
		clients.add(client);
	}
	
	synchronized void removeClient(ClientUnit client) {
		clients.remove(client);
	}
	
	Dialog getByClass(Class<? extends Dialog> dialog_class) {
		return dialogs_by_class.get(dialog_class);
	}
	
	Dialog getClientToServerRequestFirstValid(ArrayList<RequestBlock> blocks) throws NoSuchElementException {
		return dialogs.stream().filter(p -> {
			return p.checkIfClientToServerRequestIsForThis(blocks);
		}).findFirst().get();
	}
	
	Dialog getServerToClientResponseFirstValid(ArrayList<RequestBlock> blocks) throws NoSuchElementException {
		return dialogs.stream().filter(p -> {
			return p.checkIfServerToClientResponseIsForThis(blocks);
		}).findFirst().get();
	}
	
}
