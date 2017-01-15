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
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import hd3gtv.embddb.dialect.RequestHandler;
import hd3gtv.embddb.socket.ConnectionCallback;
import hd3gtv.embddb.socket.Node;
import hd3gtv.embddb.socket.Protocol;
import hd3gtv.embddb.socket.SocketClient;
import hd3gtv.embddb.socket.SocketServer;
import hd3gtv.embddb.tools.InteractiveConsoleMode;
import hd3gtv.internaltaskqueue.ITQueue;
import hd3gtv.mydmam.MyDMAM;
import hd3gtv.tools.AddressMaster;
import hd3gtv.tools.GsonIgnoreStrategy;

public class PoolManager {
	
	private static Logger log = Logger.getLogger(PoolManager.class);
	
	private final Gson simple_gson;
	
	/*private ArrayList<Dialog<?, ?>> dialogs;
	private HashMap<Class<?>, Dialog<?, ?>> dialogs_by_class;*/
	
	private SocketServer local_server;
	private RequestHandler request_handler;
	
	private Protocol protocol;
	private NodeList node_list;
	private ITQueue queue;
	// private ActivityScheduler<ClientUnit> scheduler;
	private final ScheduledExecutorService scheduled_autodiscover;
	private ScheduledFuture<?> regular_autodiscover;
	private ShutdownHook shutdown_hook;
	
	private InteractiveConsoleMode console;
	private AddressMaster addr_master;
	
	private final UUID uuid_ref;
	
	public PoolManager(ITQueue queue, String master_password_key) throws GeneralSecurityException, IOException {
		GsonBuilder builder = new GsonBuilder();
		builder.serializeNulls();
		
		GsonIgnoreStrategy ignore_strategy = new GsonIgnoreStrategy();
		builder.addDeserializationExclusionStrategy(ignore_strategy);
		builder.addSerializationExclusionStrategy(ignore_strategy);
		
		/**
		 * Outside of this package serializers
		 */
		MyDMAM.registerBaseSerializers(builder);
		simple_gson = builder.create();
		
		this.queue = queue;
		if (queue == null) {
			throw new NullPointerException("\"queue\" can't to be null");
		}
		queue.setConsole(console);
		
		node_list = new NodeList(this);
		node_list.setConsole(console);
		
		uuid_ref = UUID.randomUUID();
		addr_master = new AddressMaster();
		console = new InteractiveConsoleMode();
		
		protocol = new Protocol(master_password_key);
		
		// scheduler = new ActivityScheduler<>();//TODO set ActivityScheduler
		// scheduler.setConsole(console);
		
		scheduled_autodiscover = Executors.newSingleThreadScheduledExecutor();
		shutdown_hook = new ShutdownHook();
		
		request_handler = new RequestHandler(this);
	}
	
	public UUID getUUIDRef() {
		return uuid_ref;
	}
	
	public Protocol getProtocol() {
		return protocol;
	}
	
	public AddressMaster getAddressMaster() {
		return addr_master;
	}
	
	public Gson getSimpleGson() {
		return simple_gson;
	}
	
	public void startServer(InetSocketAddress listen) throws IOException {
		local_server = new SocketServer(this);
		
		if (listen != null) {
			local_server.setListen(listen);
		}
		
		if (listen != null) {
			log.info("Start local server on " + listen);
		} else {
			log.info("Start local server on all IP addr and for port " + protocol.getDefaultTCPPort());
		}
		local_server.start();
		
		Runtime.getRuntime().addShutdownHook(shutdown_hook);
	}
	
	public void startServer() throws IOException {
		startServer(null);
	}
	
	/**
	 * @return null if closed
	 */
	public InetSocketAddress getServerListenSocketAddress() {
		if (local_server != null) {
			if (local_server.isOpen()) {
				return local_server.getListen();
			}
		}
		return null;
	}
	
	public void startRegularAutodiscover() {
		if (regular_autodiscover.isCancelled() | regular_autodiscover.isDone()) {
			log.info("Start regular autodiscover");
			regular_autodiscover = scheduled_autodiscover.scheduleAtFixedRate(() -> {
				// TODO autoDiscover();
			}, 1000, 60, TimeUnit.SECONDS);
		}
	}
	
	public void stopRegularAutodiscover() {
		if (regular_autodiscover != null) {
			if (regular_autodiscover.isCancelled() == false) {
				log.info("Stop regular autodiscover");
				regular_autodiscover.cancel(false);
			}
		}
	}
	
	/**
	 * Blocking
	 */
	public void closeAll() {
		log.info("Close all functions: clients, server, autodiscover. It's blocking");
		
		stopRegularAutodiscover();
		// sayToClientsToDisconnectMe(); TODO
		
		local_server.waitToStop();
		
		try {
			Runtime.getRuntime().removeShutdownHook(shutdown_hook);
		} catch (IllegalStateException e) {
		}
	}
	
	/**
	 * @return false if listen == server OR listen all host address & server == me & listen port == server.port
	 */
	public boolean isNotThisServerAddress(InetSocketAddress server) {
		if (local_server != null) {
			InetSocketAddress listen = local_server.getListen();
			if (listen.equals(server) | (listen.getAddress().isAnyLocalAddress() & addr_master.isMe(server.getAddress()) & server.getPort() == listen.getPort())) {
				return false;
			}
		}
		return true;
	}
	
	/**
	 * @param callback_on_connection Always callback it, even if already exists.
	 */
	public void declareNewPotentialDistantServer(InetSocketAddress server, ConnectionCallback callback_on_connection) throws IOException {
		if (isNotThisServerAddress(server) == false) {
			callback_on_connection.onLocalServerConnection(server);
			return;
		}
		
		Node node = node_list.get(server);
		
		if (node != null) {
			callback_on_connection.alreadyConnectedNode(node);
		} else {
			new SocketClient(this, server, n -> {
				if (node_list.add(n)) {
					callback_on_connection.alreadyConnectedNode(node);
				} else {
					callback_on_connection.onNewConnectedNode(node_list.get(server));
				}
			});
		}
	}
	
	/**
	 * @param callback_on_connection Always callback it, even if already exists.
	 */
	public void declareNewPotentialDistantServer(InetAddress addr, ConnectionCallback callback_on_connection) throws IOException {
		declareNewPotentialDistantServer(new InetSocketAddress(addr, getProtocol().getDefaultTCPPort()), callback_on_connection);
	}
	
	public NodeList getNodeList() {
		return node_list;
	}
	
	public RequestHandler getRequestHandler() {
		return request_handler;
	}
	
	public ITQueue getQueue() {
		return queue;
	}
	
	public InteractiveConsoleMode getConsole() {
		return console;
	}
	
	// TODO create console for server
	// TODO create console for nodelist
	// TODO create console for autodiscover
	
	/**
	 * Blocking !
	 */
	public void startConsole() {
		/*console.addOrder("sl", "Connected servers list", "Display the connected server list (as client)", PoolManager.class, param -> {
			System.out.println("Display " + clients.size() + " connected servers list:");
			clients.forEach(client -> {
				System.out.println(client.getActualStatus());
			});
		});*/
		
		/*console.addOrder("autod", "Autodiscover", "Display the client autodiscover status. Usage autod [start | stop | run]", PoolManager.class, param -> {
			if (param == null | param.equals("")) {
				System.out.println("Autodiscover status:");
				System.out.println("Delay: " + regular_autodiscover.getDelay(TimeUnit.SECONDS) + " seconds");
				System.out.println("Cancelled: " + regular_autodiscover.isCancelled());
				System.out.println("Done: " + regular_autodiscover.isDone());
			} else if (param.equals("start")) {
				System.out.println("Start autodiscover.");
				startRegularAutodiscover();
			} else if (param.equals("stop")) {
				System.out.println("Stop autodiscover.");
				stopRegularAutodiscover();
			} else if (param.equals("run")) {
				System.out.println("Run now autodiscover.");
				autoDiscover();
			} else {
				throw new Exception("Unknow param " + param);
			}
		});*/
		console.waitActions();
	}
	
	/**
	 * Blocking.
	 */
	/*public void sayToClientsToDisconnectMe() { //TODO sayToClientsToDisconnectMe ?
		ParametedProcedure<ClientUnit> process = client -> {
			client.disconnectMe();
		};
		BiConsumer<ClientUnit, Exception> onError = (client, e) -> {
			log.error("Can't do disconnect action for client " + client, e);
			removeClient(client);
		};
		
		clients.forEach(client -> {
			queue.addToQueue(client, process, onError);
		});
		
		try {
			while (clients.isEmpty() == false) {
				Thread.sleep(1);
			}
		} catch (InterruptedException e1) {
		}
	}*/
	
	private class ShutdownHook extends Thread {
		public void run() {
			closeAll();
		}
	}
	
	// TODO do a close all nodes console operation
	
}
