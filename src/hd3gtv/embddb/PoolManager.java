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
import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;

import hd3gtv.embddb.dialect.ClientList;
import hd3gtv.embddb.dialect.Dialog;
import hd3gtv.embddb.dialect.HandCheck;
import hd3gtv.embddb.dialect.PingPongTime;
import hd3gtv.embddb.network.EDDBClient;
import hd3gtv.embddb.network.EDDBNode;
import hd3gtv.embddb.network.Protocol;
import hd3gtv.embddb.network.RequestBlock;
import hd3gtv.embddb.tools.InteractiveConsoleMode;
import hd3gtv.internaltaskqueue.ActivityScheduler;
import hd3gtv.internaltaskqueue.ITQueue;
import hd3gtv.tools.AddressMaster;

public class PoolManager {
	
	private static Logger log = Logger.getLogger(PoolManager.class);
	
	private ArrayList<Dialog<?, ?>> dialogs;
	private HashMap<Class<?>, Dialog<?, ?>> dialogs_by_class;
	
	private EDDBNode local_server;
	private ArrayList<ClientUnit> clients;
	private Protocol protocol;
	
	private ITQueue queue;
	private ActivityScheduler<ClientUnit> scheduler;
	private InteractiveConsoleMode console;
	private AddressMaster addr_master;
	
	private boolean enable_loop_clients;
	
	public PoolManager(ITQueue queue, String master_password_key) throws GeneralSecurityException, IOException {
		enable_loop_clients = false;
		
		addr_master = new AddressMaster();
		console = new InteractiveConsoleMode();
		
		this.queue = queue;
		if (queue == null) {
			throw new NullPointerException("\"queue\" can't to be null");
		}
		queue.setConsole(console);
		
		protocol = new Protocol(master_password_key);
		
		dialogs = new ArrayList<>();
		dialogs.add(new HandCheck(protocol));
		dialogs.add(new PingPongTime());
		dialogs.add(new ClientList(this));
		
		dialogs_by_class = new HashMap<>(dialogs.size());
		dialogs.forEach(d -> {
			dialogs_by_class.put(d.getClass(), d);
		});
		
		clients = new ArrayList<>();
		scheduler = new ActivityScheduler<>();
		scheduler.setConsole(console);
	}
	
	Protocol getProtocol() {
		return protocol;
	}
	
	public AddressMaster getAddressMaster() {
		return addr_master;
	}
	
	public void startServer(InetSocketAddress listen) throws IOException {
		local_server = new EDDBNode(protocol, (blocks, source) -> {
			try {
				return getClientToServerRequestFirstValid(blocks).getServerSentenceToSendToClient(source, blocks).getBlocksToSendToClient();
			} catch (Exception e) {
				log.warn("Server error", e);
			}
			return null;
		});
		if (listen != null) {
			local_server.setListenAddr(listen);
		}
		local_server.setConsole(console);
		local_server.start();
	}
	
	public void startServer() throws IOException {
		startServer(null);
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
	
	public void setEnableLoopClients(boolean enable_loop_clients) {
		this.enable_loop_clients = enable_loop_clients;
		if (enable_loop_clients) {
			if (log.isDebugEnabled()) {
				log.debug("Set enable_loop_clients");
			} else {
				log.warn("Set a dangerous param: enable_loop_clients");
			}
		}
	}
	
	/**
	 * Fail if listen == server OR listen all host address & server == me & listen port == server.port
	 */
	public void validAddress(InetSocketAddress server) throws IOException {
		if (enable_loop_clients == true) {
			return;
		}
		
		if (local_server != null) {
			InetSocketAddress listen = local_server.getListen();
			if (listen.equals(server) | (listen.getAddress().isAnyLocalAddress() & addr_master.isMe(server.getAddress()) & server.getPort() == listen.getPort())) {
				throw new IOException(server + " is this current server.");
			}
		}
	}
	
	/**
	 * Client will be add to current list if can correctly connect to server.
	 */
	public ClientUnit createClient(InetSocketAddress server) throws Exception {
		validAddress(server);
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
	
	public ArrayList<InetSocketAddress> getAllCurrentConnected() {
		ArrayList<InetSocketAddress> result = new ArrayList<>(clients.stream().map(c -> {
			return c.getConnectedServer();
		}).collect(Collectors.toList()));
		
		local_server.addConnectedClientsToList(result);
		
		return result;
	}
	
	/**
	 * Search new clients to connect to it.
	 * TODO regular call
	 */
	public void autoDiscover() {
		/**
		 * Direct mode -> check client list == connected to server list
		 * -
		 * Get all current clients
		 */
		ArrayList<InetSocketAddress> actual_list = new ArrayList<>(clients.stream().map(c -> {
			return c.getConnectedServer();
		}).collect(Collectors.toList()));
		
		validClientAddress(actual_list);
		
		/**
		 * Compare with the current connected to server list, add contact the new connected.
		 */
		local_server.callbackConnectedClientNotInList(actual_list, c -> {
			queue.addToQueue(c, ClientUnit.class, server -> {
				return createClient(server);
			}, (i, cli) -> {
				log.info("Add new server: " + cli);
			}, (i, u) -> {
				log.warn("Can't to connect to server " + i + ", but it really exists and should be contactable.", u);
			});
		});
		
		/**
		 * Distant mode -> get for each client the full Connected to its server list and the client connected to it.
		 */
		clients.stream().forEach(c -> {
			// TODO request
		});
	}
	
	/**
	 * Remove loop client/server and actual connected clients.
	 * @param new_addr_list will be cleaned only if address is in numeric (IP v4/v6) and not the String hostname format.
	 */
	void validClientAddress(ArrayList<InetSocketAddress> new_addr_list) {// TODO call before import a distant server list
		new_addr_list.removeIf(addr -> {
			try {
				validAddress(addr);
				
				/**
				 * For all current clients, select the first == addr -> if exists @return false.
				 */
				return clients.stream().filter(p -> {
					return p.getConnectedServer().equals(addr);
				}).findFirst().isPresent() == false;
			} catch (IOException e) {
				log.trace("Can't valid client address: " + e.getMessage());
			}
			return true;
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
	
	public InteractiveConsoleMode getConsole() {
		return console;
	}
	
	/**
	 * Blocking !
	 */
	public void startConsole() {
		console.addOrder("sl", "Connected servers list", "Display the connected server list (as client)", PoolManager.class, param -> {
			System.out.println("Display " + clients.size() + " connected servers list:");
			clients.forEach(client -> {
				System.out.println(client.getActualStatus());
			});
		});
		
		console.waitActions();
	}
	
}
