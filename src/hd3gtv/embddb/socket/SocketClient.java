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

import org.apache.log4j.Logger;

import hd3gtv.embddb.socket.ChannelBucketManager.ChannelBucket;

public class SocketClient {
	
	private static final Logger log = Logger.getLogger(SocketClient.class);
	
	private InetSocketAddress server;
	private AsynchronousSocketChannel channel;
	private ChannelBucket bucket;
	private SocketConnect handler_connect;
	
	public SocketClient(InetSocketAddress server, ChannelBucketManager bucket_manager) throws IOException {
		this.server = server;
		if (server == null) {
			throw new NullPointerException("\"server\" can't to be null");
		}
		if (bucket_manager == null) {
			throw new NullPointerException("\"bucket_manager\" can't to be null");
		}
		handler_connect = new SocketConnect();
		
		channel = AsynchronousSocketChannel.open();
		channel.connect(server, bucket_manager, handler_connect);
	}
	
	private class SocketConnect implements CompletionHandler<Void, ChannelBucketManager> {
		
		public void completed(Void result, ChannelBucketManager bucket_manager) {
			InetSocketAddress addr;
			try {
				addr = (InetSocketAddress) channel.getRemoteAddress();
			} catch (IOException e) {
				failed(e, bucket_manager);
				return;
			}
			
			bucket = bucket_manager.create(addr, channel);
			log.info("Connected to " + bucket);
			
			bucket.asyncRead();
		}
		
		public void failed(Throwable e, ChannelBucketManager bucket_manager) {
			log.error("Can't create TCP Client to " + server, e);
		}
		
	}
	
}
