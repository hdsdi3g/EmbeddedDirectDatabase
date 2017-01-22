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
	private InetSocketAddress remote_socket_addr;
	private PressureMeasurement pressure_measurement;
	
	ChannelBucket(PoolManager pool_manager, Node referer, AsynchronousSocketChannel channel) throws IOException {
		this.pool_manager = pool_manager;
		if (pool_manager == null) {
			throw new NullPointerException("\"pool_manager\" can't to be null");
		}
		this.referer = referer;
		if (referer == null) {
			throw new NullPointerException("\"referer\" can't to be null");
		}
		this.pressure_measurement = pool_manager.getNodeList().getPressureMeasurement();
		if (pressure_measurement == null) {
			throw new NullPointerException("\"pressure_measurement\" can't to be null");
		}
		this.channel = channel;
		if (channel == null) {
			throw new NullPointerException("\"channel\" can't to be null");
		}
		this.buffer = ByteBuffer.allocateDirect(Protocol.BUFFER_SIZE);
		remote_socket_addr = (InetSocketAddress) channel.getRemoteAddress();
	}
	
	boolean isOpen() {
		return channel.isOpen();
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
		if (remote_socket_addr == null) {
			throw new NullPointerException("\"remote_socket_addr\" can't to be null");
		}
		return remote_socket_addr.getHostString() + "/" + remote_socket_addr.getPort();
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
		pressure_measurement.onReceviedBlock(content.length);
		
		buffer.get(content, 0, content.length);
		buffer.clear();
		
		byte[] result = pool_manager.getProtocol().decrypt(content, 0, content.length);
		if (log.isTraceEnabled()) {
			log.trace("Get " + result.length + " bytes decrypted");
		}
		
		return result;
	}
	
	void encrypt(byte[] data) throws GeneralSecurityException {
		if (log.isTraceEnabled()) {
			log.trace("Prepare " + data.length + " bytes to encrypt");
		}
		
		buffer.clear();
		
		byte[] result = pool_manager.getProtocol().encrypt(data, 0, data.length);
		
		buffer.put(result);
		
		buffer.flip();
		
		if (log.isTraceEnabled()) {
			log.trace("Get " + result.length + " bytes encrypted");
		}
		
		pressure_measurement.onSendedBlock(result.length);
	}
	
	/**
	 * It will add to queue
	 */
	void doProcessReceviedDatas() throws Exception {
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
		}, wtcl -> {
			if (wtcl instanceof WantToCloseLink) {
				log.debug("Handler want to close link");
				close(getClass());
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
		checkIfOpen();
		
		pool_manager.getQueue().addToQueue(() -> {
			if (log.isTraceEnabled()) {
				log.trace("Get from " + toString() + " " + to_send.toString());
			}
			
			encrypt(to_send.getBytes(pool_manager.getProtocol()));
			asyncWrite(close_channel_after_send);
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
