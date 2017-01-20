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
import java.lang.reflect.Type;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.UUID;
import java.util.stream.Stream;

import org.apache.log4j.Logger;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import hd3gtv.embddb.dialect.DisconnectRequest;
import hd3gtv.embddb.dialect.RequestHandler;
import hd3gtv.embddb.socket.ConnectionCallback;
import hd3gtv.embddb.socket.Node;
import hd3gtv.embddb.socket.Protocol;
import hd3gtv.embddb.socket.SocketClient;
import hd3gtv.embddb.socket.SocketServer;
import hd3gtv.embddb.tools.InteractiveConsoleMode;
import hd3gtv.internaltaskqueue.ActivityScheduler;
import hd3gtv.internaltaskqueue.ITQueue;
import hd3gtv.mydmam.MyDMAM;
import hd3gtv.tools.AddressMaster;
import hd3gtv.tools.GsonIgnoreStrategy;
import hd3gtv.tools.TableList;

public class PoolManager {
	
	private static Logger log = Logger.getLogger(PoolManager.class);
	
	private final Gson simple_gson;
	
	private ArrayList<SocketServer> local_servers;
	private RequestHandler request_handler;
	
	private Protocol protocol;
	private NodeList node_list;
	private ITQueue queue;
	private ShutdownHook shutdown_hook;
	
	private InteractiveConsoleMode console;
	private AddressMaster addr_master;
	
	private final UUID uuid_ref;
	
	private ActivityScheduler<Node> node_scheduler;
	private ActivityScheduler<NodeList> nodelist_scheduler;
	
	public static final Type type_InetSocketAddress_String = new TypeToken<ArrayList<InetSocketAddress>>() {
	}.getType();
	
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
		
		local_servers = new ArrayList<>();
		console = new InteractiveConsoleMode();
		
		this.queue = queue;
		if (queue == null) {
			throw new NullPointerException("\"queue\" can't to be null");
		}
		
		node_list = new NodeList(this);
		uuid_ref = UUID.randomUUID();
		addr_master = new AddressMaster();
		protocol = new Protocol(master_password_key);
		shutdown_hook = new ShutdownHook();
		request_handler = new RequestHandler(this);
		
		nodelist_scheduler = new ActivityScheduler<>();
		nodelist_scheduler.add(node_list, node_list.getScheduledAction());
		node_scheduler = new ActivityScheduler<>();
		
		console.addOrder("ql", "Queue list", "Display actual queue list", getClass(), param -> {
			if (queue.isEmpty()) {
				System.out.println("No waiting task to display in queue.");
			} else {
				System.out.println("Display " + queue.size() + " waiting tasks");
				queue.getAllpendingTaskToString().forEach(t -> {
					System.out.println(t);
				});
			}
			queue.getAllExecutorsStatus().forEach(ex -> {
				System.out.println(ex);
			});
		});
		
		console.addOrder("nl", "Node list", "Display actual connected node", getClass(), param -> {
			TableList table = new TableList(5);
			node_list.getAllNodes().forEach(node -> {
				node.addToActualStatus(table);
			});
			table.print();
		});
		
		console.addOrder("node", "Node action", "Do action to a node", getClass(), param -> {
			if (param == null) {
				System.out.println("Usage:");
				System.out.println("node add address [port]");
				System.out.println("   for add a new node (after a valid connection)");
				System.out.println("node rm address [port]");
				System.out.println("   remove a node with protocol (to a disconnect request)");
				System.out.println("node close address [port]");
				System.out.println("   for disconnect directly a node");
				System.out.println("node isopen address [port]");
				System.out.println("   for force to check the socket state (open or close)");
				return;
			}
			
			InetSocketAddress addr = parseAddressFromCmdConsole(param);
			
			if (addr == null) {
				System.out.println("Can't get address from ”" + param + "”");
				return;
			}
			
			if (param.startsWith("add")) {
				declareNewPotentialDistantServer(addr, new ConnectionCallback() {
					
					public void onNewConnectedNode(Node node) {
						System.out.println("Node " + node + " is added sucessfully");
					}
					
					public void onLocalServerConnection(InetSocketAddress server) {
						System.out.println("You can't add local server to new node: " + server);
					}
					
					public void alreadyConnectedNode(Node node) {
						System.out.println("You can't add this node " + node + " because it's already added");
					}
				});
			} else if (node_list.contains(addr)) {
				Node node = node_list.get(addr);
				if (param.startsWith("rm")) {
					node.sendRequest(DisconnectRequest.class, null);
				} else if (param.startsWith("close")) {
					node.close(getClass());
					node_list.remove(node);
				} else if (param.startsWith("isopen")) {
					System.out.println("Is now open: " + node.isOpenSocket());
				} else {
					System.out.println("Order ”" + param + "” is unknow");
				}
			} else {
				System.out.println("Can't found node " + addr + " in current list. Please check with nl command");
			}
		});
		
		console.addOrder("gcnodes", "Garbage collector node list", "Purge closed nodes", getClass(), param -> {
			node_list.purgeClosedNodes();
		});
		console.addOrder("closenodes", "Close all nodes", "Force to disconnect all connected nodes", getClass(), param -> {
			node_list.sayToAllNodesToDisconnectMe(false);
		});
		
		console.addOrder("sch", "Activity scheduler", "Display the activated regular task list", getClass(), param -> {
			if (node_scheduler.isEmpty()) {
				System.out.println("No regular tasks to display for nodes.");
			} else {
				System.out.println("Display " + node_scheduler.size() + " regular nodes task");
				TableList table = new TableList(5);
				node_scheduler.getAllScheduledTasks(table);
				table.print();
			}
			System.out.println("");
			
			if (nodelist_scheduler.isEmpty()) {
				System.out.println("No regular tasks to display for nodelist.");
			} else {
				System.out.println("Display " + nodelist_scheduler.size() + " regular nodelist task");
				TableList table = new TableList(5);
				nodelist_scheduler.getAllScheduledTasks(table);
				table.print();
			}
			System.out.println("");
		});
		
		console.addOrder("srv", "Servers status", "Display all servers status", getClass(), param -> {
			TableList table = new TableList(3);
			local_servers.forEach(local_server -> {
				if (local_server.isOpen()) {
					table.addRow("open", local_server.getListen().getHostString(), String.valueOf(local_server.getListen().getPort()));
				} else {
					table.addRow("CLOSED", local_server.getListen().getHostString(), String.valueOf(local_server.getListen().getPort()));
				}
			});
		});
		
		console.addOrder("closesrv", "Close servers", "Close all opened servers", getClass(), param -> {
			local_servers.forEach(local_server -> {
				if (local_server.isOpen()) {
					System.out.println("Close server " + local_server.getListen().getHostString() + "...");
					local_server.waitToStop();
				}
			});
		});
		
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
	
	public ActivityScheduler<Node> getNode_scheduler() {
		return node_scheduler;
	}
	
	public Gson getSimpleGson() {
		return simple_gson;
	}
	
	public void startLocalServers() throws IOException {
		ArrayList<String> logresult = new ArrayList<>();
		
		addr_master.getAddresses().forEach(addr -> {
			InetSocketAddress listen = new InetSocketAddress(addr, protocol.getDefaultTCPPort());
			try {
				SocketServer local_server = new SocketServer(this, listen);
				local_server.start();
				local_servers.add(local_server);
				logresult.add(listen.getHostString() + ":" + listen.getPort());
			} catch (IOException e) {
				log.error("Can't start server on " + listen.getHostString() + ":" + listen.getPort());
			}
		});
		
		log.info("Start local server on " + logresult);
		
		Runtime.getRuntime().addShutdownHook(shutdown_hook);
	}
	
	/**
	 * Blocking
	 */
	public void closeAll() {
		log.info("Close all functions: clients, server, autodiscover... It's a blocking operation");
		
		nodelist_scheduler.remove(node_list);
		node_list.sayToAllNodesToDisconnectMe(true);
		
		local_servers.forEach(s -> {
			s.wantToStop();
		});
		local_servers.forEach(s -> {
			s.waitToStop();
		});
		
		try {
			Runtime.getRuntime().removeShutdownHook(shutdown_hook);
		} catch (IllegalStateException e) {
		}
	}
	
	/**
	 * @return false if listen == server OR listen all host address & server == me & listen port == server.port
	 */
	public boolean isNotThisServerAddress(InetSocketAddress server) {
		boolean result = local_servers.stream().map(s -> {
			return s.getListen();
		}).anyMatch(a -> {
			if (a == null) {
				return false;
			}
			return a.equals(server);
		});
		// log.trace("Test addr: " + server + " " + result);
		return result == false;
	}
	
	public Stream<InetSocketAddress> getListenedServerAddress() {
		return local_servers.stream().map(s -> {
			return s.getListen();
		});
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
					callback_on_connection.onNewConnectedNode(n);
				} else {
					callback_on_connection.alreadyConnectedNode(node_list.get(server));
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
	
	/**
	 * Blocking !
	 */
	public void startConsole() {
		console.waitActions();
	}
	
	private class ShutdownHook extends Thread {
		public void run() {
			closeAll();
		}
	}
	
	/**
	 * @param param like "action addr" or "action addr port"
	 */
	private InetSocketAddress parseAddressFromCmdConsole(String param) {
		int first_space = param.indexOf(" ");
		if (first_space < 1) {
			return null;
		}
		String full_addr = param.substring(first_space).trim();
		int port = protocol.getDefaultTCPPort();
		
		int port_pos = full_addr.indexOf(" ");
		if (port_pos > 1) {
			try {
				port = Integer.parseInt(full_addr.substring(port_pos + 1));
				full_addr = full_addr.substring(0, port_pos);
			} catch (NumberFormatException e) {
				log.debug("Can't get port value: " + param);
			}
		}
		return new InetSocketAddress(full_addr, port);
	}
	
}
