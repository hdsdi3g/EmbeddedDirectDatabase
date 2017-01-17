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
 * Copyright (C) hdsdi3g for hd3g.tv 6 janv. 2017
 * 
*/
package hd3gtv.embddb.socket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.function.Consumer;

import org.apache.log4j.Logger;

import hd3gtv.embddb.PoolManager;

public class SocketClient implements SocketProvider {
	
	private static final Logger log = Logger.getLogger(SocketClient.class);
	
	private InetSocketAddress server;
	private AsynchronousSocketChannel channel;
	private SocketConnect handler_connect;
	private Consumer<Node> callback_on_connection;
	
	public SocketClient(PoolManager pool_manager, InetSocketAddress server, Consumer<Node> callback_on_connection) throws IOException {
		if (pool_manager == null) {
			throw new NullPointerException("\"pool_manager\" can't to be null");
		}
		this.server = server;
		if (server == null) {
			throw new NullPointerException("\"server\" can't to be null");
		}
		this.callback_on_connection = callback_on_connection;
		if (callback_on_connection == null) {
			throw new NullPointerException("\"callback_on_connection\" can't to be null");
		}
		
		handler_connect = new SocketConnect();
		
		channel = AsynchronousSocketChannel.open();
		
		channel.connect(server, new Node(this, pool_manager, server, channel), handler_connect);
	}
	
	private class SocketConnect implements CompletionHandler<Void, Node> {
		
		public void completed(Void result, Node new_node) {
			log.info("Connected to " + new_node);
			new_node.getChannelbucket().asyncRead();
			callback_on_connection.accept(new_node);
		}
		
		public void failed(Throwable e, Node new_node) {
			log.warn("Can't create TCP Client to " + server + " (" + e.getMessage().trim() + ")");
		}
		
	}
	
}
