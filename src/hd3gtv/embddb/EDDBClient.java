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
package hd3gtv.embddb;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;

import org.apache.log4j.Logger;

public class EDDBClient {
	
	private static final Logger log = Logger.getLogger(EDDBClient.class);
	
	private InetAddress server_addr;
	private int tcp_server_port = 9160;
	private final AsynchronousSocketChannel channel;
	private final ByteBuffer buffer;
	private Protocol protocol;
	
	public EDDBClient(Protocol protocol, InetAddress server_addr) throws Exception {
		this.server_addr = server_addr;
		if (server_addr == null) {
			throw new NullPointerException("\"server_addr\" can't to be null");
		}
		this.protocol = protocol;
		if (protocol == null) {
			throw new NullPointerException("\"protocol\" can't to be null");
		}
		
		channel = AsynchronousSocketChannel.open();
		buffer = ByteBuffer.allocateDirect(50);
	}
	
	public void start() throws Exception {
		buffer.clear();
		
		channel.connect(new InetSocketAddress(server_addr, tcp_server_port), null, new CompletionHandler<Void, Void>() {
			
			public void completed(Void result, Void attach) {
				// TODO an Hello request
				// TODO a ping request (get time ?)
				
				buffer.put("Hello".getBytes());
				buffer.flip();
				try {
					channel.write(buffer).get();
				} catch (Exception e) {
					log.error("Error during write", e);
					return;
				}
				
				buffer.clear();
				int size = -1;
				try {
					size = channel.read(buffer).get();
				} catch (Exception e) {
					log.error("Error during write", e);
					return;
				}
				
				if (size < 1) {
					return;
				}
				buffer.flip();
				byte bytes[] = new byte[size]; // buffer.limit();
				buffer.get(bytes, 0, size);
				System.out.println("S> " + new String(bytes));
				
				buffer.clear();
				buffer.put(".".getBytes());
				buffer.flip();
				try {
					channel.write(buffer).get();
				} catch (Exception e) {
					log.error("Error during write", e);
					return;
				}
			}
			
			public void failed(Throwable e, Void attach) {
				log.error("Can't Client", e);
			}
			
		});
	}
	
	public void setTcpServerPort(int tcp_server_port) {
		this.tcp_server_port = tcp_server_port;
	}
	
}
