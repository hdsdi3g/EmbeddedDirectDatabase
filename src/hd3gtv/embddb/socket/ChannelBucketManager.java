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
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.ReadPendingException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.log4j.Logger;

import hd3gtv.embddb.dialect.ResponseHandler;
import hd3gtv.embddb.tools.Hexview;
import hd3gtv.internaltaskqueue.ITQueue;

public class ChannelBucketManager {
	
	private static final Logger log = Logger.getLogger(ChannelBucketManager.class);
	
	private List<ChannelBucket> buckets;// TODO replace by a true manager -> catch add/remove, do a for all
	private Protocol protocol;
	private ResponseHandler request_callbacks;
	private ITQueue queue;
	private SocketHandler read_handler;
	private SocketHandler write_handler;
	
	public ChannelBucketManager(Protocol protocol, ResponseHandler request_callbacks, ITQueue queue) {
		buckets = Collections.synchronizedList(new ArrayList<>());
		this.protocol = protocol;
		if (protocol == null) {
			throw new NullPointerException("\"protocol\" can't to be null");
		}
		
		this.request_callbacks = request_callbacks;
		if (request_callbacks == null) {
			throw new NullPointerException("\"request_callbacks\" can't to be null");
		}
		
		this.queue = queue;
		if (queue == null) {
			throw new NullPointerException("\"queue\" can't to be null");
		}
		
		read_handler = protocol.getHandlerReader();
		write_handler = protocol.getHandlerReader();
	}
	
	ChannelBucket create(InetSocketAddress distant_addr, AsynchronousSocketChannel channel) {
		ChannelBucket bucket = new ChannelBucket(distant_addr, channel);
		buckets.add(bucket);
		return bucket;
	}
	
	public class ChannelBucket {
		
		private ByteBuffer buffer;
		private InetSocketAddress distant_addr;// TODO rename var
		private AsynchronousSocketChannel channel;
		
		private ChannelBucket(InetSocketAddress distant_addr, AsynchronousSocketChannel channel) {
			this.buffer = ByteBuffer.allocateDirect(Protocol.BUFFER_SIZE);
			this.distant_addr = distant_addr;
			this.channel = channel;
		}
		
		public boolean isOpen() {
			return channel.isOpen();
		}
		
		public void checkIfOpen() throws IOException {
			if (isOpen() == false) {
				throw new IOException("Channel for " + toString() + " is closed");
			}
		}
		
		void asyncRead() {
			buffer.clear();
			try {
				channel.read(buffer, this, read_handler);
			} catch (ReadPendingException e) {
				log.trace("No two reads at the same time for " + toString());
			}
		}
		
		void asyncWrite() {
			channel.write(buffer, this, write_handler);
		}
		
		/**
		 * @return distant IP address
		 */
		public String toString() {
			return distant_addr.getHostString();
		}
		
		public void close() {
			buffer.clear();
			if (channel.isOpen()) {
				try {
					channel.close();
					log.trace("Close " + toString());
				} catch (IOException e) {
					log.warn("Can't close properly channel " + toString(), e);
				}
			}
			
			buckets.remove(this);
		}
		
		public void dump(String text) {
			buffer.flip();
			byte[] temp = new byte[buffer.remaining()];
			buffer.get(temp);
			Hexview.tracelog(temp, log, text);
		}
		
		private byte[] decrypt() throws GeneralSecurityException {
			buffer.flip();
			protocol.decrypt(buffer);
			buffer.flip();
			byte[] content = new byte[buffer.remaining()];
			buffer.get(content, 0, content.length);
			buffer.clear();
			return content;
		}
		
		private void encrypt(byte[] data) throws GeneralSecurityException {
			buffer.clear();
			buffer.put(data);
			buffer.flip();
			protocol.encrypt(buffer);
		}
		
		/**
		 * It will add to queue
		 */
		public void doProcessReceviedDatas() throws Exception {
			final byte[] datas = decrypt();
			queue.addToQueue(() -> {
				request_callbacks.onReceviedNewBlocks(protocol.decompressBlocks(datas), distant_addr.getAddress(), this);
			}, wtcl -> {
				close();
			});
		}
		
		/**
		 * It will add to queue.
		 * @throws IOException if channel is closed.
		 */
		public void sendDatas(ArrayList<RequestBlock> to_send) throws IOException {
			checkIfOpen();
			
			queue.addToQueue(() -> {
				if (log.isTraceEnabled()) {
					AtomicInteger all_size = new AtomicInteger(0);
					to_send.forEach(block -> {
						all_size.addAndGet(block.getLen());
					});
					if (to_send.size() == 1) {
						log.trace("Send to " + toString() + " " + all_size.get() + " bytes of datas on 1 block.");
					} else {
						log.trace("Get from " + toString() + " " + all_size.get() + " bytes of datas on " + to_send.size() + " blocks.");
					}
				}
				
				byte[] datas = protocol.compressBlocks(to_send);
				encrypt(datas);
				asyncWrite();
			}, e -> {
				log.error("Can't send datas " + toString(), e);
			});
		}
	}
	
}
