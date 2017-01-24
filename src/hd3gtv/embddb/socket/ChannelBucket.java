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
 * Copyright (C) hdsdi3g for hd3g.tv 18 janv. 2017
 * 
*/
package hd3gtv.embddb.socket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.ReadPendingException;
import java.security.GeneralSecurityException;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.log4j.Logger;

import hd3gtv.embddb.PoolManager;
import hd3gtv.embddb.dialect.WantToCloseLink;
import hd3gtv.tools.PressureMeasurement;

class ChannelBucket {
	
	private static final Logger log = Logger.getLogger(ChannelBucket.class);
	
	private PoolManager pool_manager;
	private final Node referer;
	private final ByteBuffer buffer;
	private final AsynchronousSocketChannel channel;
	private InetSocketAddress socket_addr;
	
	private PressureMeasurement pressure_measurement_sended;
	private PressureMeasurement pressure_measurement_recevied;
	private AtomicLong last_activity;
	
	ChannelBucket(PoolManager pool_manager, Node referer, AsynchronousSocketChannel channel) {
		this.pool_manager = pool_manager;
		if (pool_manager == null) {
			throw new NullPointerException("\"pool_manager\" can't to be null");
		}
		this.referer = referer;
		if (referer == null) {
			throw new NullPointerException("\"referer\" can't to be null");
		}
		this.channel = channel;
		if (channel == null) {
			throw new NullPointerException("\"channel\" can't to be null");
		}
		this.buffer = ByteBuffer.allocateDirect(Protocol.BUFFER_SIZE);
		
		this.pressure_measurement_recevied = pool_manager.getPressureMeasurementRecevied();
		if (pressure_measurement_recevied == null) {
			throw new NullPointerException("\"pressure_measurement_recevied\" can't to be null");
		}
		this.pressure_measurement_sended = pool_manager.getPressureMeasurementSended();
		if (pressure_measurement_sended == null) {
			throw new NullPointerException("\"pressure_measurement_sended\" can't to be null");
		}
		last_activity = new AtomicLong(System.currentTimeMillis());
	}
	
	public InetSocketAddress getRemoteSocketAddr() throws IOException {
		if (socket_addr == null) {
			socket_addr = (InetSocketAddress) channel.getRemoteAddress();
		}
		return socket_addr;
	}
	
	boolean isOpen() {
		return channel.isOpen();
	}
	
	long getLastActivityDate() {
		return last_activity.get();
	}
	
	void checkIfOpen() throws IOException {
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
	
	void asyncWrite(boolean close_channel_after_send) {
		channel.write(buffer, this, pool_manager.getProtocol().getHandlerWriter(close_channel_after_send));
	}
	
	/**
	 * @return distant IP address
	 */
	public String toString() {
		try {
			return getRemoteSocketAddr().getHostString() + "/" + getRemoteSocketAddr().getPort();
		} catch (IOException e) {
			log.error("Can't get Remote socket addr for node " + referer, e);
			return "(Invalid ChBucket)";
		}
	}
	
	void close(Class<?> by) {
		if (log.isDebugEnabled()) {
			log.debug("Want close node, by " + by.getSimpleName());
		}
		
		buffer.clear();
		if (channel.isOpen()) {
			try {
				channel.close();
			} catch (IOException e) {
				log.warn("Can't close properly channel " + toString(), e);
			}
		}
		pool_manager.getNodeList().remove(referer);
	}
	
	byte[] decrypt() throws GeneralSecurityException {
		buffer.flip();
		byte[] content = new byte[buffer.remaining()];
		if (log.isTraceEnabled()) {
			log.trace("Prepare " + content.length + " bytes to decrypt");
		}
		
		buffer.get(content, 0, content.length);
		buffer.clear();
		
		byte[] result = pool_manager.getProtocol().decrypt(content, 0, content.length);
		if (log.isTraceEnabled()) {
			log.trace("Get " + result.length + " bytes decrypted");
		}
		
		return result;
	}
	
	int encrypt(byte[] data) throws GeneralSecurityException {
		if (log.isTraceEnabled()) {
			log.trace("Prepare " + data.length + " bytes to encrypt");
		}
		
		buffer.clear();
		
		byte[] result = pool_manager.getProtocol().encrypt(data, 0, data.length);
		
		buffer.put(result);
		
		buffer.flip();
		
		if (log.isTraceEnabled()) {
			log.trace("Set " + result.length + " bytes encrypted");
		}
		
		return result.length;
	}
	
	/**
	 * It will add to queue
	 */
	void doProcessReceviedDatas() throws Exception {
		final long start_time = System.currentTimeMillis();
		last_activity.set(start_time);
		
		final byte[] datas = decrypt();
		
		pool_manager.getQueue().addToQueue(() -> {
			RequestBlock block = null;
			try {
				block = new RequestBlock(pool_manager.getProtocol(), datas);
			} catch (IOException e) {
				log.error("Can't extract sended blocks " + referer.toString(), e);
				close(getClass());
				return;
			}
			pool_manager.getRequestHandler().onReceviedNewBlock(block, referer);
			
			pressure_measurement_recevied.onDatas(datas.length, System.currentTimeMillis() - start_time);
		}, wtcl -> {
			if (wtcl instanceof WantToCloseLink) {
				log.debug("Handler want to close link");
				close(getClass());
				pressure_measurement_recevied.onDatas(datas.length, System.currentTimeMillis() - start_time);
				return;
			}
			log.error("Can process request handler", wtcl);
			close(getClass());
		});
	}
	
	/**
	 * It will add to queue.
	 * @throws IOException if channel is closed.
	 */
	void sendData(RequestBlock to_send, boolean close_channel_after_send) throws IOException {
		final long start_time = System.currentTimeMillis();
		
		checkIfOpen();
		
		pool_manager.getQueue().addToQueue(() -> {
			if (log.isTraceEnabled()) {
				log.trace("Get from " + toString() + " " + to_send.toString());
			}
			int size = encrypt(to_send.getBytes(pool_manager.getProtocol()));
			asyncWrite(close_channel_after_send);
			
			pressure_measurement_sended.onDatas(size, System.currentTimeMillis() - start_time);
		}, e -> {
			log.error("Can't send datas " + toString(), e);
		});
	}
	
	/**
	 * @return channel.hashCode
	 */
	public int hashCode() {
		return this.channel.hashCode();
	}
}
