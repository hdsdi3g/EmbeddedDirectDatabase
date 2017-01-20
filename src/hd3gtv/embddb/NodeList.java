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
 * Copyright (C) hdsdi3g for hd3g.tv 10 janv. 2017
 * 
*/
package hd3gtv.embddb;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import org.apache.log4j.Logger;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import hd3gtv.embddb.dialect.DisconnectRequest;
import hd3gtv.embddb.dialect.HelloRequest;
import hd3gtv.embddb.dialect.NodelistRequest;
import hd3gtv.embddb.socket.Node;
import hd3gtv.embddb.socket.RequestBlock;
import hd3gtv.internaltaskqueue.ActivityScheduledAction;
import hd3gtv.internaltaskqueue.Procedure;

public class NodeList {
	
	private static Logger log = Logger.getLogger(NodeList.class);
	
	private final Object lock = new Object();
	
	private List<Node> nodes;
	private HashMap<InetSocketAddress, Node> nodes_by_addr;
	private HashMap<UUID, Node> nodes_by_uuid;
	private AtomicBoolean autodiscover_can_be_remake = null;
	private PoolManager pool_manager;
	
	NodeList(PoolManager pool_manager) {
		this.pool_manager = pool_manager;
		if (pool_manager == null) {
			throw new NullPointerException("\"pool_manager\" can't to be null");
		}
		
		nodes = new ArrayList<>();
		nodes_by_addr = new HashMap<>();
		nodes_by_uuid = new HashMap<>();
		
		autodiscover_can_be_remake = new AtomicBoolean(true);
	}
	
	/**
	 * Check if node is already open.
	 */
	public Node get(InetSocketAddress addr) {
		if (nodes_by_addr.containsKey(addr) == false) {
			return null;
		}
		Node n = nodes_by_addr.get(addr);
		
		if (n.isOpenSocket() == false) {
			synchronized (lock) {
				remove(n);
			}
			return null;
		}
		
		return n;
	}
	
	/**
	 * Check if node is already open.
	 */
	public boolean contains(InetSocketAddress addr) {
		if (nodes_by_addr.containsKey(addr) == false) {
			return false;
		}
		return get(addr) != null;
	}
	
	/**
	 * Only open and set UUID nodes.
	 */
	public boolean contains(UUID uuid) {
		return nodes_by_uuid.containsKey(uuid);
	}
	
	public void purgeClosedNodes() {
		synchronized (lock) {
			nodes.removeIf(n -> {
				if (n.isOpenSocket()) {
					return false;
				}
				nodes_by_addr.remove(n.getSocketAddr());
				nodes_by_uuid.remove(n.getUUID());
				autodiscover_can_be_remake.set(true);
				return true;
			});
			
			if (nodes.size() != nodes_by_addr.size()) {
				log.debug("Not the same lists size !");
				nodes_by_addr.clear();
				nodes_by_uuid.clear();
				
				nodes.forEach(node -> {
					nodes_by_addr.put(node.getSocketAddr(), node);
					if (node.getUUID() != null) {
						nodes_by_uuid.put(node.getUUID(), node);
					}
				});
			} else {
				nodes.forEach(node -> {
					if (nodes_by_addr.containsKey(node.getSocketAddr()) == false) {
						log.debug("Missing " + node + " in nodes_by_addr");
						nodes_by_addr.put(node.getSocketAddr(), node);
					}
					if (node.getUUID() != null) {
						if (nodes_by_uuid.containsKey(node.getUUID()) == false) {
							log.debug("Missing " + node + " in nodes_by_uuid");
							nodes_by_uuid.put(node.getUUID(), node);
						}
					}
				});
			}
		}
	}
	
	public void remove(Node node) {
		synchronized (lock) {
			log.info("Remove node " + node);
			
			autodiscover_can_be_remake.set(true);
			nodes.remove(node);
			nodes_by_uuid.remove(node.getUUID());
			nodes_by_addr.remove(node.getSocketAddr());
		}
		
		pool_manager.getNode_scheduler().remove(node);
	}
	
	/**
	 * @return false if node is already added
	 */
	public boolean add(Node node) {
		if (nodes.contains(node)) {
			if (node.isOpenSocket()) {
				return false;
			}
		}
		synchronized (lock) {
			log.debug("Add node " + node);
			
			autodiscover_can_be_remake.set(true);
			nodes_by_addr.put(node.getSocketAddr(), node);
			if (node.getUUID() != null) {
				nodes_by_uuid.put(node.getUUID(), node);
			}
			nodes.add(node);
		}
		pool_manager.getRequestHandler().getRequestByClass(HelloRequest.class).sendRequest(null, node);
		
		return true;
	}
	
	public void updateUUID(Node node) throws IOException {
		if (node.getUUID() == null) {
			throw new NullPointerException("Node uuid can't to be null for " + node);
		}
		UUID newuuid = node.getUUID();
		if (nodes_by_uuid.containsKey(newuuid) == false) {
			synchronized (lock) {
				nodes_by_uuid.put(newuuid, node);
			}
		} else if (node.equals(nodes_by_uuid.get(newuuid)) == false) {
			throw new IOException("Node UUID for " + node + " was previousely added to another node (" + nodes_by_uuid.get(newuuid) + ") entry. So it can't declare twice the same node !");
		}
	}
	
	/**
	 * @return array of objects (Node.getAutodiscoverIDCard())
	 */
	public JsonArray makeAutodiscoverList() {
		JsonArray autodiscover_list = new JsonArray();
		synchronized (lock) {
			nodes.forEach(n -> {
				JsonObject jo = n.getAutodiscoverIDCard();
				if (jo != null) {
					autodiscover_list.add(jo);
				}
			});
		}
		return autodiscover_list;
	}
	
	public ActivityScheduledAction<NodeList> getScheduledAction() {
		return new ActivityScheduledAction<NodeList>() {
			
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
			
			public Procedure getRegularScheduledAction() {
				return () -> {
					purgeClosedNodes();
					if (autodiscover_can_be_remake.compareAndSet(true, false)) {
						RequestBlock to_send = pool_manager.getRequestHandler().getRequestByClass(NodelistRequest.class).createRequest(null);
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
		RequestBlock to_send = pool_manager.getRequestHandler().getRequestByClass(DisconnectRequest.class).createRequest("All nodes instance shutdown");
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
	
	public Stream<Node> getAllNodes() {
		return nodes.stream();
	}
	
}
