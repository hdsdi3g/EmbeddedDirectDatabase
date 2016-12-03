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
import java.util.HashSet;

import org.apache.log4j.Logger;

import hd3gtv.embddb.tools.Hexview;
import hd3gtv.embddb.tools.InteractiveConsoleMode;

public class EDDBNode {
	
	private static final Logger log = Logger.getLogger(EDDBNode.class);
	
	private InetSocketAddress listen;
	private final AsynchronousServerSocketChannel server;
	private Protocol protocol;
	private ServerRequestEntry request_callbacks;
	private volatile HashSet<InetSocketAddress> connected_clients;
	
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
		listen = new InetSocketAddress(protocol.getDefaultTCPPort());
		
		connected_clients = new HashSet<>();
	}
	
	public void setConsole(InteractiveConsoleMode console) {
		if (console == null) {
			throw new NullPointerException("\"console\" can't to be null");
		}
		console.addOrder("cl", "Connected client list", "Display the connected client list (as server)", EDDBNode.class, param -> {
			System.out.println("Display " + connected_clients.size() + " connected client list:");
			connected_clients.forEach(client -> {
				System.out.println("Client: " + client.getHostString() + "/" + client.getHostName() + ":" + client.getPort());
			});
		});
	}
	
	public void start() throws IOException {
		server.bind(listen);
		server.accept(server, new SocketHandler());
	}
	
	public void setListenAddr(InetSocketAddress listen) {
		if (listen == null) {
			throw new NullPointerException("\"listen\" can't to be null");
		}
		this.listen = listen;
	}
	
	public InetSocketAddress getListen() {
		return listen;
	}
	
	private class SocketHandler implements CompletionHandler<AsynchronousSocketChannel, AsynchronousServerSocketChannel> {
		
		public void completed(AsynchronousSocketChannel client, AsynchronousServerSocketChannel server) {
			try {
				InetSocketAddress clientAddr = (InetSocketAddress) client.getRemoteAddress();
				
				server.accept(server, this);
				
				log.info("Client request " + clientAddr.getHostString());
				connected_clients.add(clientAddr);
				
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
						connected_clients.remove(clientAddr);
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
						connected_clients.remove(clientAddr);
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
				try {
					connected_clients.remove((InetSocketAddress) client.getRemoteAddress());
				} catch (IOException e1) {
				}
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
