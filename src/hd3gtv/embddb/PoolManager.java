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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;

import hd3gtv.embddb.dialect.Dialog;
import hd3gtv.embddb.dialect.HandCheck;
import hd3gtv.embddb.dialect.PingPongTime;
import hd3gtv.embddb.network.EDDBClient;
import hd3gtv.embddb.network.EDDBNode;
import hd3gtv.embddb.network.Protocol;
import hd3gtv.embddb.network.RequestBlock;
import hd3gtv.internaltaskqueue.ITQueue;

public class PoolManager {
	
	private static Logger log = Logger.getLogger(PoolManager.class);
	
	private ArrayList<Dialog<?, ?>> dialogs;
	private HashMap<Class<?>, Dialog<?, ?>> dialogs_by_class;
	
	private EDDBNode local_server;
	private ArrayList<ClientUnit> clients;
	private Protocol protocol;
	
	private ITQueue queue;
	private ActivityScheduler<ClientUnit> scheduler;
	
	public PoolManager(ITQueue queue) throws GeneralSecurityException, IOException {
		this.queue = queue;
		if (queue == null) {
			throw new NullPointerException("\"queue\" can't to be null");
		}
		
		protocol = new Protocol("test"); // TODO conf
		
		dialogs = new ArrayList<>();
		dialogs.add(new HandCheck(protocol));
		dialogs.add(new PingPongTime());
		
		dialogs_by_class = new HashMap<>(dialogs.size());
		dialogs.forEach(d -> {
			dialogs_by_class.put(d.getClass(), d);
		});
		
		clients = new ArrayList<>();
		scheduler = new ActivityScheduler<>();
		
		startServer();
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
	
	public void closeAll() {
		try {
			local_server.stop();
		} catch (IOException e) {
			log.error("Can't stop local server");
		}
		clients.forEach(c -> {
			c.close();
		});
	}
	
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
		log.debug("Add client " + client);
		
		scheduler.add(client, () -> {
			queue.addToQueue(() -> {
				client.doPingPong();
			}, e -> {
				log.error("Can't prepare Ping pong request");
			});
		}, 5000, 1000, TimeUnit.MILLISECONDS);
		
		clients.add(client);
	}
	
	synchronized void removeClient(ClientUnit client) {
		log.debug("Remove client " + client);
		scheduler.remove(client);
		clients.remove(client);
	}
	
	public synchronized void removeClient(EDDBClient client) {
		log.debug("Remove client " + client);
		clients.removeIf(p -> {
			if (p.isThisInternalClient(client)) {
				scheduler.remove(p);
				return true;
			}
			return false;
		});
	}
	
	Dialog<?, ?> getByClass(Class<? extends Dialog<?, ?>> dialog_class) {
		return dialogs_by_class.get(dialog_class);
	}
	
	Dialog<?, ?> getClientToServerRequestFirstValid(ArrayList<RequestBlock> blocks) throws NoSuchElementException {
		return dialogs.stream().filter(p -> {
			return p.checkIfClientRequestIsForThisServer(blocks);
		}).findFirst().get();
	}
	
	Dialog<?, ?> getServerToClientResponseFirstValid(ArrayList<RequestBlock> blocks) throws NoSuchElementException {
		return dialogs.stream().filter(p -> {
			return p.checkIfServerResponseIsForThisClient(blocks);
		}).findFirst().get();
	}
	
}
