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
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.apache.log4j.Logger;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import hd3gtv.embddb.socket.Node;
import hd3gtv.internaltaskqueue.ITQueue;

public class NodeList {
	
	private static Logger log = Logger.getLogger(NodeList.class);
	
	private final Object lock = new Object();
	
	private List<Node> nodes;
	private HashMap<InetSocketAddress, Node> nodes_by_addr;
	private HashMap<UUID, Node> nodes_by_uuid;
	private JsonArray autodiscover_current_list = null;
	
	private ITQueue queue;
	
	NodeList(ITQueue queue) {
		this.queue = queue;
		if (queue == null) {
			throw new NullPointerException("\"queue\" can't to be null");
		}
		
		nodes = new ArrayList<>();
		nodes_by_addr = new HashMap<>();
		nodes_by_uuid = new HashMap<>();
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
	
	public void purgeClosedNodes() {// TODO regular call
		synchronized (lock) {
			nodes.removeIf(n -> {
				if (n.isOpenSocket()) {
					return false;
				}
				nodes_by_addr.remove(n.getSocketAddr());
				nodes_by_uuid.remove(n.getUUID());
				autodiscover_current_list = null;
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
	
	public void remove(Node node) {// TODO set to package
		synchronized (lock) {
			log.info("Remove node " + node);
			
			autodiscover_current_list = null;
			nodes.remove(node);
			nodes_by_uuid.remove(node.getUUID());
			nodes_by_addr.remove(node.getSocketAddr());
		}
	}
	
	public boolean add(Node node) {// TODO set to package
		if (nodes.contains(node)) {
			if (node.isOpenSocket()) {
				return false;
			}
		}
		synchronized (lock) {
			log.debug("Add node " + node);
			
			autodiscover_current_list = null;
			nodes_by_addr.put(node.getSocketAddr(), node);
			if (node.getUUID() != null) {
				nodes_by_uuid.put(node.getUUID(), node);
			}
			return nodes.add(node);
		}
	}
	
	public void updateUUID(Node node) {// TODO set to package
		if (node.getUUID() == null) {
			throw new NullPointerException("Node uuid can't to be null for " + node);
		}
		UUID newuuid = node.getUUID();
		if (nodes_by_uuid.containsKey(newuuid) == false) {
			synchronized (lock) {
				nodes_by_uuid.put(newuuid, node);
			}
		}
	}
	
	/**
	 * @return array of objects (Node.getAutodiscoverIDCard())
	 */
	public JsonArray makeAutodiscoverList() {
		if (autodiscover_current_list == null) {
			synchronized (lock) {
				autodiscover_current_list = new JsonArray();
				nodes.forEach(n -> {
					JsonObject jo = n.getAutodiscoverIDCard();
					if (jo != null) {
						autodiscover_current_list.add(jo);
					}
				});
			}
		}
		
		return autodiscover_current_list;
	}
	
}
