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
 * Copyright (C) hdsdi3g for hd3g.tv 7 janv. 2017
 * 
*/
package hd3gtv.embddb.socket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.ReadPendingException;
import java.nio.channels.spi.AsynchronousChannelProvider;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;

import hd3gtv.embddb.PoolManager;
import hd3gtv.embddb.dialect.ErrorReturn;
import hd3gtv.embddb.tools.ArrayWrapper;
import hd3gtv.embddb.tools.Hexview;

public class Node {
	
	private static final Logger log = Logger.getLogger(Node.class);
	
	private PoolManager pool_manager;
	private ChannelBucket channelbucket;
	private InetSocketAddress socket_addr;
	
	private UUID uuid_ref;
	private long server_delta_time;
	
	public Node(PoolManager pool_manager, InetSocketAddress socket_addr, AsynchronousSocketChannel channel) {
		this.pool_manager = pool_manager;
		if (pool_manager == null) {
			throw new NullPointerException("\"pool_manager\" can't to be null");
		}
		this.socket_addr = socket_addr;
		if (socket_addr == null) {
			throw new NullPointerException("\"socket_addr\" can't to be null");
		}
		if (channel == null) {
			throw new NullPointerException("\"channel\" can't to be null");
		}
		
		channelbucket = new ChannelBucket(this, channel);
		server_delta_time = 0;
	}
	
	public InetSocketAddress getSocketAddr() {
		return socket_addr;
	}
	
	public boolean isOpenSocket() {
		return channelbucket.channel.isOpen();
	}
	
	// TODO regular check channelbucket state, and if disconnected remove
	
	public String toString() {
		return getSocketAddr().getHostName() + ":" + getSocketAddr().getPort() + " [" + channelbucket.getProviderClass().getSimpleName() + "]";
	}
	
	public ChannelBucket getChannelbucket() {
		return channelbucket;
	}
	
	public class ChannelBucket {
		private Node referer;
		private ByteBuffer buffer;
		private AsynchronousSocketChannel channel;
		
		private ChannelBucket(Node referer, AsynchronousSocketChannel channel) {
			this.buffer = ByteBuffer.allocateDirect(Protocol.BUFFER_SIZE);
			this.channel = channel;
			this.referer = referer;
		}
		
		public Class<? extends AsynchronousChannelProvider> getProviderClass() {
			return channel.provider().getClass();
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
				channel.read(buffer, this, pool_manager.getProtocol().getHandlerReader());
			} catch (ReadPendingException e) {
				log.trace("No two reads at the same time for " + toString());
			}
		}
		
		void asyncWrite() {
			channel.write(buffer, this, pool_manager.getProtocol().getHandlerWriter());
		}
		
		/**
		 * @return distant IP address
		 */
		public String toString() {
			return socket_addr.getHostString();
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
			pool_manager.remove(referer);
		}
		
		public void dump(String text) {
			buffer.flip();
			byte[] temp = new byte[buffer.remaining()];
			buffer.get(temp);
			Hexview.tracelog(temp, log, text);
		}
		
		private byte[] decrypt() throws GeneralSecurityException {
			buffer.flip();
			pool_manager.getProtocol().decrypt(buffer);
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
			pool_manager.getProtocol().encrypt(buffer);
		}
		
		/**
		 * It will add to queue
		 */
		public void doProcessReceviedDatas() throws Exception {
			final byte[] datas = decrypt();
			pool_manager.getQueue().addToQueue(() -> {
				pool_manager.getRequestHandler().onReceviedNewBlocks(pool_manager.getProtocol().decompressBlocks(datas), socket_addr.getAddress(), referer);
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
			
			pool_manager.getQueue().addToQueue(() -> {
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
				
				byte[] datas = pool_manager.getProtocol().compressBlocks(to_send);
				encrypt(datas);
				asyncWrite();
			}, e -> {
				log.error("Can't send datas " + toString(), e);
			});
		}
	}
	
	/**
	 * Via SocketAddr
	 */
	public int hashCode() {
		return this.getSocketAddr().hashCode();
	}
	
	/**
	 * Via SocketAddr
	 */
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		
		Node other = (Node) obj;
		
		return this.getSocketAddr().equals(other.getSocketAddr());
	}
	
	public void onErrorReturnFromNode(ErrorReturn error) {
		if (error.isDisconnectme()) {
			log.warn("Node (" + error.getNode() + ") say: \"" + error.getMessage() + "\"" + " by " + error.getCaller() + " at " + new Date(error.getDate()) + " and want to disconnect");
			channelbucket.close();
		} else {
			log.info("Node (" + error.getNode() + ") say: \"" + error.getMessage() + "\"" + " by " + error.getCaller() + " at " + new Date(error.getDate()));
		}
	}
	
	public void send(RequestBlock to_send) {
		send(ArrayWrapper.asArrayList(to_send));
	}
	
	public void send(String name, String datas) {
		send(new RequestBlock(name, datas));
	}
	
	public void send(String name, byte[] datas) {
		send(new RequestBlock(name, datas));
	}
	
	// TODO create a send & close after send ??
	public void send(ArrayList<RequestBlock> to_send) {
		try {
			channelbucket.sendDatas(to_send);
		} catch (IOException e) {
			List<String> block_names = to_send.stream().map(b -> {
				return b.getName();
			}).collect(Collectors.toList());
			
			log.error("Can't send datas to " + toString() + " > " + block_names);
			channelbucket.close();
		}
	}
	
	public void setUUIDRef(UUID uuid) throws IOException {
		if (uuid == null) {
			throw new NullPointerException("\"uuid_ref\" can't to be null");
		}
		if (uuid.equals(pool_manager.getUUIDRef())) {
			throw new IOException("Invalid UUID for " + toString() + ", it's the same as local manager ! (" + uuid_ref.toString() + ")");
		}
		if (uuid_ref == null) {
			uuid_ref = uuid;
			log.debug("Set UUID for " + toString() + ", " + uuid);
		}
		check(uuid);
	}
	
	public void check(UUID uuid) throws IOException {
		if (this.uuid_ref == null) {
			return;
		}
		if (uuid.equals(uuid_ref) == false) {
			throw new IOException("Invalid UUID for " + toString() + ", you should disconnect now (this = " + uuid_ref.toString() + " and dest = " + uuid.toString() + ")");
		}
		if (uuid.equals(pool_manager.getUUIDRef())) {
			throw new IOException("Invalid UUID for " + toString() + ", it's the same as local manager ! (" + uuid_ref.toString() + ")");
		}
	}
	
	public static final long MAX_TOLERANCE_DELTA_TIME_WITH_SERVER = TimeUnit.SECONDS.toMillis(30);
	
	/**
	 * Network jitter filter
	 */
	public static final long MIN_TOLERANCE_DELTA_TIME_WILL_UPDATE_CHANGE = TimeUnit.MILLISECONDS.toMillis(200);
	
	public boolean setDistantDate(long server_date) {
		long new_delay = server_date - System.currentTimeMillis();
		
		if (Math.abs(server_delta_time - new_delay) < MIN_TOLERANCE_DELTA_TIME_WILL_UPDATE_CHANGE) {
			return true;
		}
		
		if (log.isTraceEnabled()) {
			log.trace("Node " + toString() + " delay: " + server_delta_time + " ms before, now is " + new_delay + " ms");
		}
		
		server_delta_time = new_delay;
		
		if ((server_delta_time != new_delay) && (Math.abs(server_delta_time) > MAX_TOLERANCE_DELTA_TIME_WITH_SERVER)) {
			log.warn(
					"Big delay with server " + toString() + ": " + server_delta_time + " ms. Please check the NTP setup with this host and the server ! In the meantime, this node will be disconned.");
			channelbucket.close();
			return false;
		}
		return true;
	}
	
}
