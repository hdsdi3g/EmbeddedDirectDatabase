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
import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousChannelGroup;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.apache.log4j.Logger;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import hd3gtv.embddb.dialect.DisconnectRequest;
import hd3gtv.embddb.dialect.PokeRequest;
import hd3gtv.embddb.dialect.RequestHandler;
import hd3gtv.embddb.socket.ConnectionCallback;
import hd3gtv.embddb.socket.Node;
import hd3gtv.embddb.socket.Protocol;
import hd3gtv.embddb.socket.SocketClient;
import hd3gtv.embddb.socket.SocketServer;
import hd3gtv.internaltaskqueue.ActivityScheduler;
import hd3gtv.mydmam.MyDMAM;
import hd3gtv.tools.AddressMaster;
import hd3gtv.tools.GsonIgnoreStrategy;
import hd3gtv.tools.InteractiveConsoleMode;
import hd3gtv.tools.PressureMeasurement;
import hd3gtv.tools.TableList;

public class PoolManager {
	
	private static Logger log = Logger.getLogger(PoolManager.class);
	
	private final Gson simple_gson;
	
	private ArrayList<SocketServer> local_servers;
	private RequestHandler request_handler;
	
	private Protocol protocol;
	private NodeList node_list;
	
	private AsynchronousChannelGroup channel_group;
	private BlockingQueue<Runnable> executor_pool_queue;
	private ThreadPoolExecutor executor_pool;
	
	private ShutdownHook shutdown_hook;
	
	private InteractiveConsoleMode console;
	private AddressMaster addr_master;
	
	private final UUID uuid_ref;
	
	private ActivityScheduler<Node> node_scheduler;
	private ActivityScheduler<NodeList> nodelist_scheduler;
	
	private PressureMeasurement pressure_measurement_sended;
	private PressureMeasurement pressure_measurement_recevied;
	
	public static final Type type_InetSocketAddress_String = new TypeToken<ArrayList<InetSocketAddress>>() {
	}.getType();
	
	public PoolManager(String master_password_key) throws GeneralSecurityException, IOException {
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
		
		executor_pool_queue = new LinkedBlockingQueue<Runnable>(100);
		executor_pool = new ThreadPoolExecutor(1, Runtime.getRuntime().availableProcessors(), 100, TimeUnit.MILLISECONDS, executor_pool_queue);
		executor_pool.setRejectedExecutionHandler((r, executor) -> {
			log.warn("Too many task to be executed at the same time ! This will not proceed: " + r);
		});
		channel_group = AsynchronousChannelGroup.withThreadPool(executor_pool);
		
		pressure_measurement_sended = new PressureMeasurement();
		pressure_measurement_recevied = new PressureMeasurement();
		
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
			System.out.println("Executor status:");
			TableList table = new TableList(2);
			table.addRow("Active", String.valueOf(executor_pool.getActiveCount()));
			table.addRow("Max capacity", String.valueOf(executor_pool_queue.remainingCapacity()));
			table.addRow("Completed", String.valueOf(executor_pool.getCompletedTaskCount()));
			table.addRow("Core pool", String.valueOf(executor_pool.getCorePoolSize()));
			table.addRow("Pool", String.valueOf(executor_pool.getPoolSize()));
			table.addRow("Largest pool", String.valueOf(executor_pool.getLargestPoolSize()));
			table.addRow("Maximum pool", String.valueOf(executor_pool.getMaximumPoolSize()));
			table.print();
			System.out.println();
			
			if (executor_pool_queue.isEmpty()) {
				System.out.println("No waiting task to display in queue.");
			} else {
				System.out.println("Display " + executor_pool_queue.size() + " waiting tasks.");
				executor_pool_queue.stream().forEach(r -> {
					System.out.println(" * " + r.toString() + " in " + r.getClass().getName());
				});
			}
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
			} else {
				Node node = node_list.get(addr);
				if (node == null) {
					List<Node> search_nodes = node_list.get(addr.getAddress());
					if (search_nodes.isEmpty()) {
						System.out.println("Can't found node " + addr + " in current list. Please check with nl command");
					} else if (search_nodes.size() > 1) {
						System.out.println("Too many nodes on the " + addr + " in current list. Please check with nl command and enter TCP port");
					} else {
						node = search_nodes.get(0);
					}
				}
				
				if (node != null) {
					if (param.startsWith("rm")) {
						node.sendRequest(DisconnectRequest.class, "Manual via console");
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
			}
		});
		
		console.addOrder("gcnodes", "Garbage collector node list", "Purge closed nodes", getClass(), param -> {
			node_list.purgeClosedNodes();
		});
		console.addOrder("closenodes", "Close all nodes", "Force to disconnect all connected nodes", getClass(), param -> {
			node_list.sayToAllNodesToDisconnectMe(false);
		});
		
		console.addOrder("sch", "Activity scheduler", "Display the activated regular task list", getClass(), param -> {
			TableList table = new TableList(5);
			
			if (node_scheduler.isEmpty()) {
				System.out.println("No regular tasks to display for nodes.");
			} else {
				node_scheduler.getAllScheduledTasks(table);
			}
			
			if (nodelist_scheduler.isEmpty()) {
				if (node_scheduler.isEmpty()) {
					System.out.println();
				}
				System.out.println("No regular tasks to display for nodelist.");
			} else {
				nodelist_scheduler.getAllScheduledTasks(table);
			}
			
			table.print();
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
		
		console.addOrder("poke", "Poke servers", "Poke all server, or one if specified", getClass(), param -> {
			if (param == null) {
				node_list.getAllNodes().forEach(node -> {
					System.out.println("Poke " + node);
					node.sendRequest(PokeRequest.class, null);
				});
			} else {
				InetSocketAddress addr = parseAddressFromCmdConsole(param);
				if (addr != null) {
					Node node = node_list.get(addr);
					if (node == null) {
						node_list.get(addr.getAddress()).forEach(n -> {
							System.out.println("Poke " + n);
							n.sendRequest(PokeRequest.class, null);
						});
					} else {
						System.out.println("Poke " + node);
						node.sendRequest(PokeRequest.class, null);
					}
				}
			}
		});
		
		console.addOrder("ip", "IP properties", "Show actual network properties", getClass(), param -> {
			TableList table = new TableList(7);
			addr_master.dump(table);
			table.print();
		});
		
		console.addOrder("stats", "Get last pressure measurement", "Get nodelist data stats", getClass(), param -> {
			TableList list = new TableList(10);
			PressureMeasurement.toTableHeader(list);
			pressure_measurement_recevied.getActualStats(false).toTable(list, "Recevied");
			pressure_measurement_sended.getActualStats(false).toTable(list, "Sended");
			list.print();
		});
		
		console.addOrder("resetstats", "Reset pressure measurement", "Get nodelist data stats and reset it", getClass(), param -> {
			TableList list = new TableList(10);
			PressureMeasurement.toTableHeader(list);
			pressure_measurement_recevied.getActualStats(true).toTable(list, "Recevied");
			pressure_measurement_sended.getActualStats(true).toTable(list, "Sended");
			list.print();
		});
		
	}
	
	public PressureMeasurement getPressureMeasurementSended() {
		return pressure_measurement_sended;
	}
	
	public PressureMeasurement getPressureMeasurementRecevied() {
		return pressure_measurement_recevied;
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
	
	public ThreadPoolExecutor getExecutorPool() {
		return executor_pool;
	}
	
	public AsynchronousChannelGroup getChannelGroup() {
		return channel_group;
	}
	
	private List<InetSocketAddress> bootstrap_servers;
	
	public void setBootstrapPotentialNodes(List<InetSocketAddress> servers) {
		this.bootstrap_servers = servers;
	}
	
	public void connectToBootstrapPotentialNodes(String reason) {
		if (bootstrap_servers == null) {
			return;
		}
		bootstrap_servers.forEach(addr -> {
			try {
				declareNewPotentialDistantServer(addr, new ConnectionCallback() {
					
					public void onNewConnectedNode(Node node) {
						log.info("Connected to node (bootstrap): " + node + " by " + reason);
					}
					
					public void onLocalServerConnection(InetSocketAddress server) {
						log.warn("Can't add server (" + server.getHostString() + "/" + server.getPort() + ") not node list, by " + reason);
					}
					
					public void alreadyConnectedNode(Node node) {
						log.debug("Node is already connected: " + node + ", by " + reason);
					}
				});
			} catch (Exception e) {
				log.error("Can't create node: " + addr + ", by " + reason, e);
			}
		});
	}
	
	public void startLocalServers() throws IOException {
		ArrayList<String> logresult = new ArrayList<>();
		
		addr_master.getAddresses().forEach(addr -> {
			InetSocketAddress listen = new InetSocketAddress(addr, protocol.getDefaultTCPPort());
			try {
				SocketServer local_server = new SocketServer(this, listen);
				local_server.start();
				local_servers.add(local_server);
				logresult.add(listen.getHostString() + "/" + listen.getPort());
			} catch (IOException e) {
				log.error("Can't start server on " + listen.getHostString() + "/" + listen.getPort());
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
		
		executor_pool.shutdown();
		
		try {
			executor_pool.awaitTermination(500, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			log.error("Can't wait to stop executor waiting list", e);
			executor_pool.shutdownNow();
		}
		
		try {
			Runtime.getRuntime().removeShutdownHook(shutdown_hook);
		} catch (IllegalStateException e) {
		}
	}
	
	public boolean isListenToThis(InetSocketAddress server) {
		return getListenedServerAddress().anyMatch(addr -> {
			return addr.equals(server);
		});
	}
	
	public Stream<InetSocketAddress> getListenedServerAddress() {
		return local_servers.stream().map(s -> {
			return s.getListen();
		}).filter(addr -> {
			return addr != null;
		});
	}
	
	/**
	 * @param callback_on_connection Always callback it, even if already exists.
	 */
	public void declareNewPotentialDistantServer(InetSocketAddress server, ConnectionCallback callback_on_connection) throws IOException {
		if (isListenToThis(server)) {
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
	
	public NodeList getNodeList() {
		return node_list;
	}
	
	public RequestHandler getRequestHandler() {
		return request_handler;
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
	 * @param param like "action addr" or "action addr port" or "action addr/port" or "action addr:port"
	 */
	private InetSocketAddress parseAddressFromCmdConsole(String param) {
		int first_space = param.indexOf(" ");
		if (first_space < 1) {
			return null;
		}
		String full_addr = param.substring(first_space).trim();
		int port = protocol.getDefaultTCPPort();
		
		int port_pos = full_addr.indexOf(" ");
		if (port_pos == -1) {
			port_pos = full_addr.indexOf("/");
		}
		if (port_pos == -1) {
			port_pos = full_addr.lastIndexOf(":");
		}
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
