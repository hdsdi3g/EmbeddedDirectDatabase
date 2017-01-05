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
import java.nio.channels.CompletionHandler;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.NoSuchElementException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;

import hd3gtv.embddb.dialect.Dialog;
import hd3gtv.embddb.dialect.dialogs.ClientList;
import hd3gtv.embddb.dialect.dialogs.DisconnectNode;
import hd3gtv.embddb.dialect.dialogs.HandCheck;
import hd3gtv.embddb.dialect.dialogs.PingPongTime;
import hd3gtv.embddb.network.EDDBClient;
import hd3gtv.embddb.network.EDDBNode;
import hd3gtv.embddb.network.Protocol;
import hd3gtv.embddb.network.RequestBlock;
import hd3gtv.embddb.tools.InteractiveConsoleMode;
import hd3gtv.internaltaskqueue.ActivityScheduler;
import hd3gtv.internaltaskqueue.ITQueue;
import hd3gtv.internaltaskqueue.ParametedProcedure;
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
	private final ScheduledExecutorService scheduled_autodiscover;
	private ScheduledFuture<?> regular_autodiscover;
	private ShutdownHook shutdown_hook;
	
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
		
		connect_handler = new ConnectHandler();
		
		dialogs = new ArrayList<>();
		dialogs.add(new HandCheck(protocol));// TODO better implementation...
		dialogs.add(new PingPongTime());
		dialogs.add(new ClientList(this));
		dialogs.add(new DisconnectNode(this));
		
		dialogs_by_class = new HashMap<>(dialogs.size());
		dialogs.forEach(d -> {
			dialogs_by_class.put(d.getClass(), d);
		});
		
		clients = new ArrayList<>();
		scheduler = new ActivityScheduler<>();
		scheduler.setConsole(console);
		
		scheduled_autodiscover = Executors.newSingleThreadScheduledExecutor();
		shutdown_hook = new ShutdownHook();
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
				autoDiscover();
			}, 1000, 60, TimeUnit.SECONDS);
		}
	}
	
	public void stopRegularAutodiscover() {
		if (regular_autodiscover.isCancelled() == false) {
			log.info("Stop regular autodiscover");
			regular_autodiscover.cancel(false);
		}
	}
	
	/**
	 * Blocking
	 */
	public void closeAll() {
		log.info("Close all functions: clients, server, autodiscover. It's blocking");
		
		stopRegularAutodiscover();
		sayToClientsToDisconnectMe();
		
		try {
			local_server.stop();
		} catch (IOException e) {
			log.error("Can't stop local server");
		}
		
		try {
			Runtime.getRuntime().removeShutdownHook(shutdown_hook);
		} catch (IllegalStateException e) {
		}
	}
	
	/**
	 * For enable the creation of client to the local server.
	 * Not recomended !
	 */
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
	 * @return false if listen == server OR listen all host address & server == me & listen port == server.port
	 */
	public boolean validAddress(InetSocketAddress server) {
		if (enable_loop_clients == true) {
			return true;
		}
		
		if (local_server != null) {
			InetSocketAddress listen = local_server.getListen();
			if (listen.equals(server) | (listen.getAddress().isAnyLocalAddress() & addr_master.isMe(server.getAddress()) & server.getPort() == listen.getPort())) {
				return false;
			}
		}
		
		return true;
	}
	
	/**
	 * Client will be add to current list if can correctly connect to server.
	 * @return null if the client already exists
	 */
	public void createClient(InetSocketAddress server) throws IOException {
		if (validAddress(server) == false) {
			return;
		}
		
		if (clients.stream().map(c -> {
			return c.getConnectedServer();
		}).anyMatch(p -> {
			return p.equals(server);
		}) == false) {
			new ClientUnit(this, server, connect_handler);
		}
	}
	
	private ConnectHandler connect_handler;
	
	class ConnectHandler implements CompletionHandler<Void, ClientUnit> {
		
		private ConnectHandler() {
		}
		
		public void completed(Void result, ClientUnit newclient) {
			newclient.doHandCheck(c -> {
				declareClient(newclient);
			});
		}
		
		public void failed(Throwable exc, ClientUnit attachment) {
			log.error("Can't create TCP Client to " + attachment.getConnectedServer(), exc);
		}
		
	}
	
	public ConnectHandler getConnectHandler() {
		return connect_handler;
	}
	
	synchronized void declareClient(ClientUnit client) {
		log.info("Add valid client " + client);
		
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
		log.info("Remove client " + client);
		
		scheduler.remove(client);
		clients.remove(client);
	}
	
	public synchronized void removeClient(EDDBClient client) {
		log.info("Remove client " + client);
		
		clients.removeIf(p -> {
			if (p.isThisInternalClient(client)) {
				scheduler.remove(p);
				return true;
			}
			return false;
		});
	}
	
	public void removeClient(InetSocketAddress client) {
		log.info("Remove client " + client);
		try {
			clients.stream().filter(p -> {
				return p.getConnectedServer().equals(client);
			}).findFirst().get().close();
		} catch (NoSuchElementException e) {
			log.debug("Can't found client " + client, e);
		}
	}
	
	public ArrayList<InetSocketAddress> getAllCurrentConnected() {
		ArrayList<InetSocketAddress> result = new ArrayList<>(clients.stream().map(c -> {
			return c.getConnectedServer();
		}).collect(Collectors.toList()));
		
		return result;
	}
	
	/**
	 * Search new clients to connect to it.
	 */
	public void autoDiscover() {
		log.debug("Start discover for " + clients.size() + " client(s)");
		
		/**
		 * Direct mode -> check client list == connected to server list
		 * -
		 * Get all current clients
		 */
		ArrayList<InetSocketAddress> actual_list = new ArrayList<>(clients.stream().map(c -> {
			return c.getConnectedServer();
		}).collect(Collectors.toList()));
		
		log.debug("Do the autodiscover distant mode with " + actual_list.size() + " server(s) to check");
		
		/**
		 * Distant mode -> get for each client the full Connected to its server list and the client connected to it.
		 */
		clients.stream().forEach(client -> {
			client.getFromConnectedServerThisActualClientList(actual_list);
		});
	}
	
	public ITQueue getQueue() {
		return queue;
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
		
		console.addOrder("autod", "Autodiscover", "Display the client autodiscover status. Usage autod [start | stop | run]", PoolManager.class, param -> {
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
		});
		
		console.waitActions();
	}
	
	/**
	 * Blocking.
	 */
	public void sayToClientsToDisconnectMe() {
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
	}
	
	private class ShutdownHook extends Thread {
		public void run() {
			closeAll();
		}
	}
	
}
