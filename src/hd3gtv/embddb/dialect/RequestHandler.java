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
 * Copyright (C) hdsdi3g for hd3g.tv 21 nov. 2016
 * 
*/
package hd3gtv.embddb.dialect;

import java.util.HashMap;
import java.util.Optional;

import org.apache.log4j.Logger;

import hd3gtv.embddb.PoolManager;
import hd3gtv.embddb.socket.Node;
import hd3gtv.embddb.socket.RequestBlock;

public class RequestHandler {
	
	private static final Logger log = Logger.getLogger(RequestHandler.class);
	private PoolManager pool_manager;
	private HashMap<String, Request<?>> requests;
	
	public RequestHandler(PoolManager pool_manager) {
		this.pool_manager = pool_manager;
		if (pool_manager == null) {
			throw new NullPointerException("\"pool_manager\" can't to be null");
		}
		requests = new HashMap<>();
		
		addRequest(new ErrorRequest(this));
		addRequest(new HelloRequest(this));
		addRequest(new DisconnectRequest(this));
		addRequest(new NodelistRequest(this));
		addRequest(new PokeRequest(this));
	}
	
	public PoolManager getPoolManager() {
		return pool_manager;
	}
	
	private void addRequest(Request<?> r) {
		String name = r.getHandleName();
		if (name == null) {
			throw new NullPointerException("Request getHandleName can't to be null");
		}
		if (name.isEmpty()) {
			throw new NullPointerException("Request getHandleName can't to be empty");
		}
		if (requests.containsKey(name)) {
			throw new IndexOutOfBoundsException("Another Request was loaded with name " + name + " (" + r.getClass() + " and " + requests.get(name).getClass() + ")");
		}
		requests.put(name, r);
	}
	
	@SuppressWarnings("unchecked")
	public <T extends Request<?>> T getRequestByClass(Class<T> request_class) {
		Optional<Request<?>> o_r = requests.values().stream().filter(r -> {
			return r.getClass().equals(request_class);
		}).findFirst();
		
		if (o_r.isPresent()) {
			return (T) o_r.get();
		}
		
		try {
			log.info("Can't found class " + request_class + " in current class list");
			Request<?> r = request_class.getConstructor(PoolManager.class).newInstance(pool_manager);
			addRequest(r);
			return (T) r;
		} catch (Exception e) {
			log.error("Can't instance class " + request_class.getName(), e);
		}
		
		return null;
	}
	
	public void onReceviedNewBlock(RequestBlock block, Node node) throws WantToCloseLink {
		if (log.isTraceEnabled()) {
			log.trace("Get " + block.toString() + " from " + node);
		}
		
		if (requests.containsKey(block.getRequestName()) == false) {
			log.error("Can't handle block name \"" + block.getRequestName() + "\" from " + node);
			return;
		}
		
		requests.get(block.getRequestName()).onRequest(block, node);
	}
	
}
