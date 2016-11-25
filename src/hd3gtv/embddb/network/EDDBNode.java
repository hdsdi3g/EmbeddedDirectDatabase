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
 * Copyright (C) hdsdi3g for hd3g.tv 20 nov. 2016
 * 
*/
package hd3gtv.embddb.network;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.ArrayList;

import org.apache.log4j.Logger;

import hd3gtv.embddb.tools.Hexview;

public class EDDBNode {
	
	private static final Logger log = Logger.getLogger(EDDBNode.class);
	
	private int tcp_server_port;
	private final AsynchronousServerSocketChannel server;
	private Protocol protocol;
	private ServerRequestEntry request_callbacks;
	
	public EDDBNode(Protocol protocol, ServerRequestEntry request_callbacks) throws IOException {
		this.protocol = protocol;
		if (protocol == null) {
			throw new NullPointerException("\"protocol\" can't to be null");
		}
		this.request_callbacks = request_callbacks;
		if (request_callbacks == null) {
			throw new NullPointerException("\"request_callbacks\" can't to be null");
		}
		
		server = AsynchronousServerSocketChannel.open();
		// server.setOption(StandardSocketOptions.SO_REUSEADDR, true);
		tcp_server_port = protocol.getDefaultTCPPort();
		
		server.bind(new InetSocketAddress(tcp_server_port));
	}
	
	public void start() throws IOException {
		server.accept(server, new SocketHandler());
	}
	
	public void setTcpServerPort(int tcp_server_port) {
		this.tcp_server_port = tcp_server_port;
	}
	
	private class SocketHandler implements CompletionHandler<AsynchronousSocketChannel, AsynchronousServerSocketChannel> {
		
		public void completed(AsynchronousSocketChannel client, AsynchronousServerSocketChannel server) {
			try {
				InetSocketAddress clientAddr = (InetSocketAddress) client.getRemoteAddress();
				
				server.accept(server, this);
				
				log.info("Client request " + clientAddr.getHostString());
				
				ByteBuffer buffer = ByteBuffer.allocateDirect(Protocol.BUFFER_SIZE);
				ByteBuffer uncrypted_buffer;
				byte[] uncrypted_content;
				ArrayList<RequestBlock> response_blocks;
				
				while (true) {
					/**
					 * Get raw client request
					 */
					buffer.clear();
					int size = client.read(buffer).get();
					if (size < 1) {
						buffer.clear();
						client.close();
						return;
					}
					buffer.flip();
					
					if (log.isTraceEnabled()) {
						log.trace("Recevied from " + clientAddr + " " + size + " bytes");
					}
					
					if (Protocol.DISPLAY_HEXDUMP) {
						byte[] temp = new byte[buffer.remaining()];
						buffer.get(temp);
						Hexview.tracelog(temp, log, "Crypted recivied content from client");
						buffer.flip();
					}
					
					/**
					 * Decrypt ByteBuffer
					 */
					uncrypted_buffer = protocol.decrypt(buffer);
					buffer.clear();
					
					/**
					 * Transfert uncrypted datas to byte[]
					 */
					uncrypted_buffer.flip();
					uncrypted_content = new byte[uncrypted_buffer.remaining()];
					uncrypted_buffer.get(uncrypted_content, 0, uncrypted_content.length);
					uncrypted_buffer.clear();
					
					try {
						response_blocks = request_callbacks.onRequest(protocol.decompressBlocks(uncrypted_content), clientAddr.getAddress());
					} catch (WantToCloseLink wtcl) {
						client.close();
						return;
					}
					
					if (response_blocks == null) {
						continue;
					} else if (response_blocks.isEmpty()) {
						continue;
					}
					
					buffer.clear();
					buffer.put(protocol.compressBlocks(response_blocks));
					buffer.flip();
					
					/**
					 * Actually, uncrypted_buffer is crypted.
					 */
					uncrypted_buffer = protocol.encrypt(buffer);
					uncrypted_buffer.flip();
					
					if (Protocol.DISPLAY_HEXDUMP) {
						byte[] temp = new byte[uncrypted_buffer.remaining()];
						uncrypted_buffer.get(temp);
						Hexview.tracelog(temp, log, "Crypted sended content to client");
						uncrypted_buffer.flip();
					}
					
					client.write(uncrypted_buffer).get();
				}
			} catch (Exception e) {
				log.error("Socket error", e);
			}
		}
		
		public void failed(Throwable e, AsynchronousServerSocketChannel server) {
			log.error("Socket error", e);
		}
		
	}
	
	public void stop() throws IOException {
		if (server.isOpen()) {
			server.close();
		}
	}
}
