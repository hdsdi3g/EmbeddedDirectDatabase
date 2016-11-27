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

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.log4j.Logger;

import hd3gtv.embddb.PoolManager;
import hd3gtv.embddb.tools.Hexview;

public class EDDBClient {
	
	private static final Logger log = Logger.getLogger(EDDBClient.class);
	
	private InetSocketAddress server;
	private AsynchronousSocketChannel channel;
	private Protocol protocol;
	private PoolManager pool;
	
	public EDDBClient(Protocol protocol, InetSocketAddress server) throws Exception {
		this.server = server;
		if (server == null) {
			throw new NullPointerException("\"server\" can't to be null");
		}
		this.protocol = protocol;
		if (protocol == null) {
			throw new NullPointerException("\"protocol\" can't to be null");
		}
		open();
	}
	
	public String toString() {
		return getClass().getSimpleName() + "_" + server;
	}
	
	public void open() throws Exception {
		channel = AsynchronousSocketChannel.open();
	}
	
	public void setPool(PoolManager pool) {
		this.pool = pool;
	}
	
	/**
	 * Blocking.
	 */
	public void connect() throws InterruptedException, ExecutionException {
		log.debug("Try to connect to server " + server);
		channel.connect(server).get();
	}
	
	/**
	 * Do a first connect() before requests.
	 */
	public void request(ArrayList<RequestBlock> request, ServerResponse response) throws Exception {
		if (log.isTraceEnabled()) {
			AtomicInteger all_size = new AtomicInteger(0);
			request.forEach(block -> {
				all_size.addAndGet(block.getLen());
			});
			log.trace("Request to server " + server + " " + all_size.get() + " bytes of datas on " + request.size() + " block(s).");
		}
		
		byte[] raw = protocol.compressBlocks(request);
		ByteBuffer send_buffer = protocol.encrypt(ByteBuffer.wrap(raw));
		send_buffer.flip();
		
		if (Protocol.DISPLAY_HEXDUMP) {
			byte[] temp = new byte[send_buffer.remaining()];
			send_buffer.get(temp);
			Hexview.tracelog(temp, log, "Crypted sended content to server");
			send_buffer.flip();
		}
		
		EDDBClient this_client = this;
		
		channel.write(send_buffer, null, new CompletionHandler<Integer, Void>() {
			
			@Override
			public void completed(Integer result, Void attachment) {
				try {
					if (log.isTraceEnabled()) {
						log.trace("Server " + server + " as correctly recevied datas. Now, wait its response...");
					}
					
					ByteBuffer read_buffer = ByteBuffer.allocateDirect(Protocol.BUFFER_SIZE);
					int size = channel.read(read_buffer).get();
					if (size < 1) {
						log.trace("No datas sended by the server " + server + " on the response");
						return;
					}
					read_buffer.flip();
					
					if (log.isTraceEnabled()) {
						log.trace("Response recevied from the server " + server + " " + size + " bytes");
					}
					
					if (Protocol.DISPLAY_HEXDUMP) {
						byte[] temp = new byte[read_buffer.remaining()];
						read_buffer.get(temp);
						Hexview.tracelog(temp, log, "Crypted recivied content from server");
						read_buffer.flip();
					}
					
					/**
					 * Decrypt ByteBuffer
					 */
					ByteBuffer uncrypted_buffer = protocol.decrypt(read_buffer);
					
					/**
					 * Transfert uncrypted datas to byte[]
					 */
					uncrypted_buffer.flip();
					byte[] uncrypted_content = new byte[uncrypted_buffer.remaining()];
					uncrypted_buffer.get(uncrypted_content, 0, uncrypted_content.length);
					uncrypted_buffer.clear();
					
					/**
					 * Decompress and callback items
					 */
					ArrayList<RequestBlock> blocks = protocol.decompressBlocks(uncrypted_content);
					
					if (log.isTraceEnabled()) {
						AtomicInteger all_size = new AtomicInteger(0);
						blocks.forEach(block -> {
							all_size.addAndGet(block.getLen());
						});
						log.trace("The response from the server " + server + "  is extracted. Total size: " + all_size.get() + " bytes on " + blocks.size() + " block(s).");
					}
					
					response.onServerRespond(blocks, server.getAddress());
				} catch (Exception e) {
					log.error("Can't communicate with the server " + server, e);
					if (pool != null) {
						pool.removeClient(this_client);
					}
				}
			}
			
			public void failed(Throwable e, Void attachment) {
				log.error("Can't send to server", e);
				if (pool != null) {
					pool.removeClient(this_client);
				}
			}
			
		});
	}
	
	public void close() throws Exception {
		if (channel.isOpen()) {
			// TODO disconnect with protocol
			channel.close();
		}
	}
	
}
