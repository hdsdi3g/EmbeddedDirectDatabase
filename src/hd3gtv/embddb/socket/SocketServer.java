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
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.ClosedChannelException;

import org.apache.log4j.Logger;

import hd3gtv.embddb.PoolManager;
import hd3gtv.tools.StoppableThread;

public class SocketServer extends StoppableThread implements SocketProvider {
	
	private static final Logger log = Logger.getLogger(SocketServer.class);
	
	private AsynchronousServerSocketChannel server;
	private PoolManager pool_manager;
	private InetSocketAddress listen;
	
	public SocketServer(PoolManager pool_manager, InetSocketAddress listen) throws IOException {
		super("SocketServer");
		
		this.pool_manager = pool_manager;
		if (pool_manager == null) {
			throw new NullPointerException("\"pool_manager\" can't to be null");
		}
		this.listen = listen;
		if (listen == null) {
			throw new NullPointerException("\"listen\" can't to be null");
		}
		
		server = null;
	}
	
	public String toString() {
		if (server == null) {
			return listen.getHostString() + " port " + listen.getPort() + " (no init)";
		} else if (server.isOpen()) {
			return listen.getHostString() + " port " + listen.getPort() + " (open)";
		}
		return listen.getHostString() + " port " + listen.getPort() + " (close)";
	}
	
	public boolean isOpen() {
		if (server != null && isWantToRun()) {
			return server.isOpen();
		}
		return false;
	}
	
	/**
	 * @return null if server is closed
	 */
	public InetSocketAddress getListen() {
		try {
			return (InetSocketAddress) server.getLocalAddress();
		} catch (ClosedChannelException e) {
		} catch (IOException e) {
			log.error("Can't get server local address");
		}
		return null;
	}
	
	public void run() {
		try {
			server = AsynchronousServerSocketChannel.open(pool_manager.getChannelGroup());
			server.setOption(StandardSocketOptions.SO_RCVBUF, 4 * 1024);
			server.setOption(StandardSocketOptions.SO_REUSEADDR, true);
			server.bind(listen);
		} catch (BindException e) {
			log.fatal("Socket " + listen.getHostString() + "/" + listen.getPort() + ", TCP, is already in use: can't open channel server");
			return;
		} catch (IOException e) {
			log.fatal("Can't open channel server " + toString(), e);
			return;
		}
		
		while (isWantToRun()) {
			try {
				AsynchronousSocketChannel channel = server.accept().get();
				Node node = new Node(this, pool_manager, channel);
				log.info("Client connect " + node);
				node.getChannelbucket().asyncRead();
				pool_manager.getNodeList().add(node);
				
			} catch (Exception e) {
				if (isWantToRun()) {
					log.warn("Error during socket handling", e);
				}
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
	
	public void wantToStop() {
		super.wantToStop();
		if (server != null) {
			if (server.isOpen()) {
				log.debug("Stop server " + this.listen.getHostString() + "/" + this.listen.getPort());
				try {
					server.close();
				} catch (IOException e) {
					log.error("Can't close local server", e);
				}
			}
		}
	}
	
	public String getTypeName() {
		return "Distant client to local server";
	}
	
}
