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
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import hd3gtv.embddb.dialect.RequestHandler;
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
	
	private List<Node> nodes;
	private Protocol protocol;
	
	private ITQueue queue;
	// private ActivityScheduler<ClientUnit> scheduler;
	private final ScheduledExecutorService scheduled_autodiscover;
	private ScheduledFuture<?> regular_autodiscover;
	private ShutdownHook shutdown_hook;
	
	private InteractiveConsoleMode console;
	private AddressMaster addr_master;
	
	private boolean enable_loop_clients;
	
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
		
		uuid_ref = UUID.randomUUID();
		
		enable_loop_clients = false;
		
		addr_master = new AddressMaster();
		console = new InteractiveConsoleMode();
		
		this.queue = queue;
		if (queue == null) {
			throw new NullPointerException("\"queue\" can't to be null");
		}
		queue.setConsole(console);
		
		protocol = new Protocol(master_password_key);
		
		/*dialogs = new ArrayList<>();
		dialogs.add(new HandCheck(protocol));
		dialogs.add(new PingPongTime());
		dialogs.add(new ClientList(this));
		dialogs.add(new DisconnectNode(this));
		
		dialogs_by_class = new HashMap<>(dialogs.size());
		dialogs.forEach(d -> {
			dialogs_by_class.put(d.getClass(), d);
		});*/
		
		nodes = Collections.synchronizedList(new ArrayList<>());
		
		// scheduler = new ActivityScheduler<>();//TODO set
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
				autoDiscover();
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
	public boolean isNotThisServerAddress(InetSocketAddress server) {
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
	 * @param callback_on_connection Always callback it, even if already exists.
	 */
	public void declareNewPotentialDistantServer(InetSocketAddress server, Consumer<Node> callback_on_connection) throws IOException {
		if (isNotThisServerAddress(server) == false) {
			return;
		}
		
		Optional<Node> o_node = nodes.stream().filter(n -> {
			return n.getSocketAddr().equals(server);
		}).findFirst();
		
		if (o_node.isPresent()) {
			callback_on_connection.accept(o_node.get());
		} else {
			new SocketClient(this, server, n -> {
				if (add(n)) {
					callback_on_connection.accept(n);
				} else {
					callback_on_connection.accept(getNodeByAddress(server));
				}
			});
		}
	}
	
	/**
	 * @param callback_on_connection Always callback it, even if already exists.
	 */
	public void declareNewPotentialDistantServer(InetAddress addr, Consumer<Node> callback_on_connection) throws IOException {
		declareNewPotentialDistantServer(new InetSocketAddress(addr, getProtocol().getDefaultTCPPort()), callback_on_connection);
	}
	
	/**
	 * @return null if not exists or if socket is closed.
	 */
	public Node getNodeByAddress(InetSocketAddress addr) {
		Optional<Node> o_node = nodes.stream().filter(n -> {
			return n.getSocketAddr().equals(addr);
		}).findFirst();
		
		if (o_node.isPresent()) {
			Node n = o_node.get();
			if (n.isOpenSocket()) {
				return n;
			} else {
				nodes.remove(n);
			}
		}
		return null;
	}
	
	/*synchronized void declareClient(ClientUnit client) {
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
	}*/
	
	public RequestHandler getRequestHandler() {
		return request_handler;
	}
	
	public ArrayList<InetSocketAddress> getAllCurrentNodes() {
		nodes.removeIf(n -> {
			return n.isOpenSocket() == false;
		});
		
		return new ArrayList<>(nodes.stream().map(n -> {
			return n.getSocketAddr();
		}).collect(Collectors.toList()));
	}
	
	public void remove(Node node) {// TODO set to package
		log.info("Remove node " + node);
		nodes.remove(node);
	}
	
	public boolean add(Node node) {// TODO set to package
		log.debug("Add node " + node);
		if (nodes.contains(node)) {
			if (node.isOpenSocket()) {
				return false;
			} else {
				nodes.remove(node);
			}
		}
		return nodes.add(node);
	}
	
	/**
	 * Search new clients to connect to it.
	 */
	public void autoDiscover() {
		log.debug("Start discover for " + nodes.size() + " nodes(s)");
		
		/**
		 * Direct mode -> check client list == connected to server list
		 * -
		 * Get all current clients
		 */
		// TODO
		// ArrayList<InetSocketAddress> actual_list = new ArrayList<>(clients.stream().map(c -> {
		// return c.getConnectedServer();
		// }).collect(Collectors.toList()));
		
		// log.debug("Do the autodiscover distant mode with " + actual_list.size() + " server(s) to check");
		
		/**
		 * Distant mode -> get for each client the full Connected to its server list and the client connected to it.
		 */
		// TODO
		// clients.stream().forEach(client -> {
		// client.getFromConnectedServerThisActualClientList(actual_list);
		// });
		
		// TODO + search double entries in nodes
	}
	
	public ITQueue getQueue() {
		return queue;
	}
	
	public InteractiveConsoleMode getConsole() {
		return console;
	}
	
	/**
	 * Blocking !
	 */
	/*public void startConsole() {
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
	}*/
	
	/**
	 * Blocking.
	 */
	/*public void sayToClientsToDisconnectMe() {
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
	
	/**
	 * @return JsonArray String
	 */
	/*public static String serializing(Stream<InetSocketAddress> list) {
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
	
	private static JsonParser parser = new JsonParser();
	
	public static ArrayList<InetSocketAddress> deserializing(String json_array_list) {
		JsonArray ja = parser.parse(json_array_list).getAsJsonArray();
		
		ArrayList<InetSocketAddress> client_list = new ArrayList<>(ja.size() + 1);
		
		ja.forEach(je -> {
			JsonObject jo = je.getAsJsonObject();
			client_list.add(new InetSocketAddress(jo.get("ip").getAsString(), jo.get("port").getAsInt()));
		});
		
		return client_list;
	}*/
	
}
