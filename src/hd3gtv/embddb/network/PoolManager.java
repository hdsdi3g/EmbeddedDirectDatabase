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
package hd3gtv.embddb.network;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousChannelGroup;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.log4j.Logger;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import hd3gtv.internaltaskqueue.ActivityScheduledAction;
import hd3gtv.internaltaskqueue.ActivityScheduler;
import hd3gtv.mydmam.MyDMAM;
import hd3gtv.tools.AddressMaster;
import hd3gtv.tools.InteractiveConsoleMode;
import hd3gtv.tools.PressureMeasurement;
import hd3gtv.tools.TableList;

public class PoolManager {
	
	private static Logger log = Logger.getLogger(PoolManager.class);
	
	private ArrayList<SocketServer> local_servers;
	private RequestHandler request_handler;
	
	private Protocol protocol;
	
	private AsynchronousChannelGroup channel_group;
	private BlockingQueue<Runnable> executor_pool_queue;
	private ThreadPoolExecutor executor_pool;
	
	private ShutdownHook shutdown_hook;
	
	private InteractiveConsoleMode console;
	private AddressMaster addr_master;
	
	private final UUID uuid_ref;
	
	private ActivityScheduler<Node> node_scheduler;
	private ActivityScheduler<PoolManager> pool_scheduler;
	
	private PressureMeasurement pressure_measurement_sended;
	private PressureMeasurement pressure_measurement_recevied;
	private List<InetSocketAddress> bootstrap_servers;
	
	private NetDiscover net_discover;
	
	/**
	 * synchronizedList
	 */
	private List<Node> nodes;
	private AtomicBoolean autodiscover_can_be_remake = null;
	
	public static final Type type_InetSocketAddress_String = new TypeToken<ArrayList<InetSocketAddress>>() {
	}.getType();
	
	public PoolManager(String master_password_key) throws GeneralSecurityException, IOException {
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
		
		nodes = Collections.synchronizedList(new ArrayList<>());
		autodiscover_can_be_remake = new AtomicBoolean(true);
		uuid_ref = UUID.randomUUID();
		addr_master = new AddressMaster();
		protocol = new Protocol(master_password_key);
		shutdown_hook = new ShutdownHook();
		request_handler = new RequestHandler(this);
		
		pool_scheduler = new ActivityScheduler<>();
		pool_scheduler.add(this, getScheduledAction());
		node_scheduler = new ActivityScheduler<>();
		
		net_discover = new NetDiscover(this);
		
		console.addOrder("ql", "Queue list", "Display actual queue list", getClass(), param -> {
			System.out.println("Executor status:");
			TableList table = new TableList();
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
			TableList table = new TableList();
			nodes.stream().forEach(node -> {
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
				try {
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
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			} else {
				Node node = get(addr);
				if (node == null) {
					List<Node> search_nodes = get(addr.getAddress());
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
						node.sendRequest(RequestDisconnect.class, "Manual via console");
					} else if (param.startsWith("close")) {
						node.close(getClass());
						remove(node);
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
			purgeClosedNodes();
		});
		console.addOrder("closenodes", "Close all nodes", "Force to disconnect all connected nodes", getClass(), param -> {
			sayToAllNodesToDisconnectMe(false);
		});
		
		console.addOrder("sch", "Activity scheduler", "Display the activated regular task list", getClass(), param -> {
			TableList table = new TableList();
			
			if (node_scheduler.isEmpty()) {
				System.out.println("No regular tasks to display for nodes.");
			} else {
				node_scheduler.getAllScheduledTasks(table);
			}
			
			if (pool_scheduler.isEmpty()) {
				if (node_scheduler.isEmpty()) {
					System.out.println();
				}
				System.out.println("No regular tasks to display for nodelist.");
			} else {
				pool_scheduler.getAllScheduledTasks(table);
			}
			
			table.print();
		});
		
		console.addOrder("srv", "Servers status", "Display all servers status", getClass(), param -> {
			TableList table = new TableList();
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
				nodes.stream().forEach(node -> {
					System.out.println("Poke " + node);
					node.sendRequest(RequestPoke.class, null);
				});
			} else {
				InetSocketAddress addr = parseAddressFromCmdConsole(param);
				if (addr != null) {
					Node node = get(addr);
					if (node == null) {
						get(addr.getAddress()).forEach(n -> {
							System.out.println("Poke " + n);
							n.sendRequest(RequestPoke.class, null);
						});
					} else {
						System.out.println("Poke " + node);
						node.sendRequest(RequestPoke.class, null);
					}
				}
			}
		});
		
		console.addOrder("ip", "IP properties", "Show actual network properties", getClass(), param -> {
			TableList table = new TableList();
			addr_master.dump(table);
			table.print();
		});
		
		console.addOrder("stats", "Get last pressure measurement", "Get nodelist data stats", getClass(), param -> {
			TableList list = new TableList();
			PressureMeasurement.toTableHeader(list);
			pressure_measurement_recevied.getActualStats(false).toTable(list, "Recevied");
			pressure_measurement_sended.getActualStats(false).toTable(list, "Sended");
			list.print();
		});
		
		console.addOrder("resetstats", "Reset pressure measurement", "Get nodelist data stats and reset it", getClass(), param -> {
			TableList list = new TableList();
			PressureMeasurement.toTableHeader(list);
			pressure_measurement_recevied.getActualStats(true).toTable(list, "Recevied");
			pressure_measurement_sended.getActualStats(true).toTable(list, "Sended");
			list.print();
		});
		
	}
	
	PressureMeasurement getPressureMeasurementSended() {
		return pressure_measurement_sended;
	}
	
	PressureMeasurement getPressureMeasurementRecevied() {
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
	
	ActivityScheduler<Node> getNode_scheduler() {
		return node_scheduler;
	}
	
	public Gson getSimpleGson() {
		return MyDMAM.gson_kit.getGsonSimple();
	}
	
	AsynchronousChannelGroup getChannelGroup() {
		return channel_group;
	}
	
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
		
		net_discover.startRegularSend();
		
		Runtime.getRuntime().addShutdownHook(shutdown_hook);
	}
	
	/**
	 * Blocking
	 */
	public void closeAll() {
		log.info("Close all functions: clients, server, autodiscover... It's a blocking operation");
		
		net_discover.close();
		
		pool_scheduler.remove(this);
		sayToAllNodesToDisconnectMe(true);
		
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
		
		Node node = get(server);
		
		if (node != null) {
			callback_on_connection.alreadyConnectedNode(node);
		} else {
			new SocketClient(this, server, n -> {
				if (add(n)) {
					callback_on_connection.onNewConnectedNode(n);
				} else {
					callback_on_connection.alreadyConnectedNode(get(server));
				}
			});
		}
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
	
	/**
	 * Check if node is already open, else close it.
	 * @return null if empty
	 */
	public Node get(InetSocketAddress addr) {
		if (addr == null) {
			throw new NullPointerException("\"addr\" can't to be null");
		}
		
		Optional<Node> o_node = nodes.stream().filter(n -> {
			return addr.equals(n.getSocketAddr());
		}).findFirst();
		
		if (o_node.isPresent() == false) {
			return null;
		}
		
		Node n = o_node.get();
		
		if (n.isOpenSocket() == false) {
			remove(n);
			return null;
		}
		
		return n;
	}
	
	/**
	 * Check if nodes are already open, else close it.
	 * @return empty if emtpy
	 */
	public List<Node> get(InetAddress addr) {
		if (addr == null) {
			throw new NullPointerException("\"addr\" can't to be null");
		}
		
		List<Node> result = nodes.stream().filter(n -> {
			InetSocketAddress n_addr = n.getSocketAddr();
			if (n_addr == null) {
				return false;
			}
			return n_addr.getAddress().equals(addr);
		}).collect(Collectors.toList());
		
		result.removeIf(n -> {
			if (n.isOpenSocket() == false) {
				remove(n);
				return true;
			}
			return false;
		});
		
		return result;
	}
	
	/**
	 * Check if node is already open, else close it.
	 * @return null if empty
	 */
	public Node get(UUID uuid) {
		if (uuid == null) {
			throw new NullPointerException("\"uuid\" can't to be null");
		}
		
		Optional<Node> o_node = nodes.stream().filter(n -> {
			return n.equalsThisUUID(uuid);
		}).findFirst();
		
		if (o_node.isPresent() == false) {
			return null;
		}
		
		Node n = o_node.get();
		
		if (n.isOpenSocket() == false) {
			remove(n);
			return null;
		}
		
		return n;
	}
	
	public boolean isConnectedTo(UUID uuid) {
		if (uuid == null) {
			throw new NullPointerException("\"uuid\" can't to be null");
		}
		
		return nodes.stream().filter(n -> {
			return n.equalsThisUUID(uuid);
		}).findFirst().isPresent();
	}
	
	public void purgeClosedNodes() {
		nodes.removeIf(n -> {
			if (n.isOpenSocket()) {
				return false;
			}
			autodiscover_can_be_remake.set(true);
			return true;
		});
		
		if (nodes.isEmpty()) {
			connectToBootstrapPotentialNodes("Opened nodes list empty after purge");
		}
	}
	
	public void remove(Node node) {
		log.info("Remove node " + node);
		
		autodiscover_can_be_remake.set(true);
		nodes.remove(node);
		
		if (log.isDebugEnabled()) {
			log.debug("Full node list: " + nodes);
		} else {
			if (nodes.isEmpty()) {
				log.info("Now, it's not connected to any nodes");
			}
		}
		
		node_scheduler.remove(node);
		
		if (nodes.isEmpty()) {
			connectToBootstrapPotentialNodes("Opened nodes list empty after purge");
		}
	}
	
	/**
	 * @return false if node is already added
	 */
	public boolean add(Node node) {
		if (nodes.contains(node)) {
			if (node.isOpenSocket()) {
				return false;
			} else {
				remove(node);
			}
		}
		log.info("Add node " + node);
		autodiscover_can_be_remake.set(true);
		nodes.add(node);
		request_handler.getRequestByClass(RequestHello.class).sendRequest(null, node);
		
		if (log.isDebugEnabled()) {
			log.debug("Full node list: " + nodes);
		}
		
		return true;
	}
	
	/**
	 * @return array of objects (Node.getAutodiscoverIDCard())
	 */
	JsonArray makeAutodiscoverList() {
		JsonArray autodiscover_list = new JsonArray();
		nodes.forEach(n -> {
			JsonObject jo = n.getAutodiscoverIDCard();
			if (jo != null) {
				autodiscover_list.add(jo);
			}
		});
		return autodiscover_list;
	}
	
	private ActivityScheduledAction<PoolManager> getScheduledAction() {
		return new ActivityScheduledAction<PoolManager>() {
			
			public String getScheduledActionName() {
				return "Purge closed nodes and send autodiscover requests";
			}
			
			public boolean onScheduledActionError(Exception e) {
				log.error("Can't do reguar scheduled nodelist operations");
				return true;
			}
			
			public TimeUnit getScheduledActionPeriodUnit() {
				return TimeUnit.SECONDS;
			}
			
			public long getScheduledActionPeriod() {
				return 60;
			}
			
			public long getScheduledActionInitialDelay() {
				return 10;
			}
			
			public Runnable getRegularScheduledAction() {
				return () -> {
					purgeClosedNodes();
					if (autodiscover_can_be_remake.compareAndSet(true, false)) {
						DataBlock to_send = request_handler.getRequestByClass(RequestNodelist.class).createRequest(null);
						if (to_send != null) {
							nodes.forEach(n -> {
								n.sendBlock(to_send, false);
							});
						}
					}
				};
			}
		};
	}
	
	public void sayToAllNodesToDisconnectMe(boolean blocking) {
		DataBlock to_send = request_handler.getRequestByClass(RequestDisconnect.class).createRequest("All nodes instance shutdown");
		nodes.forEach(n -> {
			n.sendBlock(to_send, true);
		});
		
		if (blocking) {
			try {
				while (nodes.isEmpty() == false) {
					Thread.sleep(1);
				}
			} catch (InterruptedException e1) {
			}
		}
	}
	
}
