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
 * Copyright (C) hdsdi3g for hd3g.tv 5 janv. 2017
 * 
*/
package hd3gtv.embddb.socket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;

import org.apache.log4j.Logger;

import hd3gtv.embddb.PoolManager;
import hd3gtv.tools.StoppableThread;

public class SocketServer extends StoppableThread {
	
	private static final Logger log = Logger.getLogger(SocketServer.class);
	
	private InetSocketAddress listen;
	private AsynchronousServerSocketChannel server;
	private PoolManager pool_manager;
	
	public SocketServer(PoolManager pool_manager) throws IOException {
		super("SocketServer", log);
		
		this.pool_manager = pool_manager;
		if (pool_manager == null) {
			throw new NullPointerException("\"pool_manager\" can't to be null");
		}
		
		listen = pool_manager.getServerListenSocketAddress();
		server = null;
	}
	
	public void setListen(InetSocketAddress listen) {
		this.listen = listen;
	}
	
	public boolean isOpen() {
		if (server != null && isWantToRun()) {
			return server.isOpen();
		}
		return false;
	}
	
	public InetSocketAddress getListen() {
		return listen;
	}
	
	public void run() {
		try {
			server = AsynchronousServerSocketChannel.open();
			// server.setOption(StandardSocketOptions.SO_REUSEADDR, true);
			server.bind(listen);
		} catch (IOException e) {
			log.fatal("Can't open channel server", e);
			return;
		}
		
		while (isWantToRun()) {
			try {
				AsynchronousSocketChannel channel = server.accept().get();
				Node node = new Node(pool_manager, (InetSocketAddress) channel.getRemoteAddress(), channel);
				log.info("Client connect " + node);
				node.getChannelbucket().asyncRead();
				pool_manager.add(node);
				
				/*Thread t = new Thread(() -> {
					try {
						while (true) {
							Thread.sleep(1000);
							active_buckets.get(0).buffer.clear();
							active_buckets.get(0).buffer.put("Sauvage".getBytes());
							active_buckets.get(0).buffer.flip();
							active_buckets.get(0).channel.write(active_buckets.get(0).buffer, active_buckets.get(0), handler_writer);
							// active_buckets.get(0).buffer.clear();
							// System.out.println("OK");
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				});
				t.setDaemon(true);
				t.start();*/
				
			} catch (Exception e) {
				log.warn("Error during socket handling", e);
			}
		}
		
		if (server.isOpen()) {
			try {
				server.close();
			} catch (IOException e) {
				log.warn("Can't close server", e);
			}
		}
	}
	
}
