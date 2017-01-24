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

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
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
	
	/**
	 * synchronizedList
	 */
	private List<Node> nodes;
	
	private AtomicBoolean autodiscover_can_be_remake = null;
	private PoolManager pool_manager;
	
	NodeList(PoolManager pool_manager) {
		this.pool_manager = pool_manager;
		if (pool_manager == null) {
			throw new NullPointerException("\"pool_manager\" can't to be null");
		}
		
		nodes = Collections.synchronizedList(new ArrayList<>());
		
		autodiscover_can_be_remake = new AtomicBoolean(true);
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
	
	public void purgeClosedNodes() {
		nodes.removeIf(n -> {
			if (n.isOpenSocket()) {
				return false;
			}
			autodiscover_can_be_remake.set(true);
			return true;
		});
		
		if (nodes.isEmpty()) {
			pool_manager.connectToBootstrapPotentialNodes("Opened nodes list empty after purge");
		}
	}
	
	public void remove(Node node) {
		log.info("Remove node " + node);
		
		autodiscover_can_be_remake.set(true);
		nodes.remove(node);
		
		if (log.isDebugEnabled()) {
			log.debug("Full node list: " + nodes);
		}
		
		pool_manager.getNode_scheduler().remove(node);
		
		if (nodes.isEmpty()) {
			pool_manager.connectToBootstrapPotentialNodes("Opened nodes list empty after purge");
		}
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
		log.info("Add node " + node);
		autodiscover_can_be_remake.set(true);
		nodes.add(node);
		pool_manager.getRequestHandler().getRequestByClass(HelloRequest.class).sendRequest(null, node);
		
		if (log.isDebugEnabled()) {
			log.debug("Full node list: " + nodes);
		}
		
		return true;
	}
	
	/**
	 * @return array of objects (Node.getAutodiscoverIDCard())
	 */
	public JsonArray makeAutodiscoverList() {
		JsonArray autodiscover_list = new JsonArray();
		nodes.forEach(n -> {
			JsonObject jo = n.getAutodiscoverIDCard();
			if (jo != null) {
				autodiscover_list.add(jo);
			}
		});
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
