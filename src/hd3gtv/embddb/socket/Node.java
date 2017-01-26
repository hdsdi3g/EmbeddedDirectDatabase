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
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ReadPendingException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.log4j.Logger;

import com.google.gson.JsonObject;

import hd3gtv.embddb.PoolManager;
import hd3gtv.embddb.dialect.ErrorReturn;
import hd3gtv.embddb.dialect.PokeRequest;
import hd3gtv.embddb.dialect.Request;
import hd3gtv.embddb.dialect.WantToCloseLinkException;
import hd3gtv.internaltaskqueue.ActivityScheduledAction;
import hd3gtv.internaltaskqueue.Procedure;
import hd3gtv.tools.PressureMeasurement;
import hd3gtv.tools.TableList;

public class Node {
	
	private static final Logger log = Logger.getLogger(Node.class);
	
	private PoolManager pool_manager;
	private ArrayList<InetSocketAddress> local_server_node_addr;
	
	private UUID uuid_ref;
	private long server_delta_time;
	private InetSocketAddress socket_addr;
	
	private final SocketProvider provider;
	private final ByteBuffer read_buffer;
	private final ByteBuffer write_buffer;
	private final AsynchronousSocketChannel channel;
	private final PressureMeasurement pressure_measurement_sended;
	private final PressureMeasurement pressure_measurement_recevied;
	private final AtomicLong last_activity;
	
	public Node(SocketProvider provider, PoolManager pool_manager, AsynchronousSocketChannel channel) {
		this.provider = provider;
		if (provider == null) {
			throw new NullPointerException("\"provider\" can't to be null");
		}
		this.pool_manager = pool_manager;
		if (pool_manager == null) {
			throw new NullPointerException("\"pool_manager\" can't to be null");
		}
		this.channel = channel;
		if (channel == null) {
			throw new NullPointerException("\"channel\" can't to be null");
		}
		
		this.read_buffer = ByteBuffer.allocateDirect(Protocol.BUFFER_SIZE);
		this.write_buffer = ByteBuffer.allocateDirect(Protocol.BUFFER_SIZE);
		
		this.pressure_measurement_recevied = pool_manager.getPressureMeasurementRecevied();
		if (pressure_measurement_recevied == null) {
			throw new NullPointerException("\"pressure_measurement_recevied\" can't to be null");
		}
		this.pressure_measurement_sended = pool_manager.getPressureMeasurementSended();
		if (pressure_measurement_sended == null) {
			throw new NullPointerException("\"pressure_measurement_sended\" can't to be null");
		}
		last_activity = new AtomicLong(System.currentTimeMillis());
		
		try {
			socket_addr = (InetSocketAddress) channel.getRemoteAddress();
		} catch (IOException e) {
		}
		server_delta_time = 0;
	}
	
	public InetSocketAddress getSocketAddr() {
		if (socket_addr == null) {
			if (provider instanceof SocketClient) {
				socket_addr = ((SocketClient) provider).getDistantServerAddr();
			} else
				try {
					socket_addr = (InetSocketAddress) channel.getRemoteAddress();
				} catch (IOException e) {
					log.debug("Can't get addr", e);
				}
		}
		return socket_addr;
	}
	
	public boolean isOpenSocket() {
		return isOpen();
	}
	
	/**
	 * Plot twist: this current host may be out of time and the node can be at the right date.
	 * @return false the case if you should not communicate with it if you needs date accuracy.
	 */
	public boolean isOutOfTime(long min_delta_time, long max_delta_time) {
		if (server_delta_time < min_delta_time | server_delta_time > max_delta_time) {
			return true;
		}
		return false;
	}
	
	public String toString() {
		if (uuid_ref == null) {
			return getSocketAddr().getHostString() + "/" + getSocketAddr().getPort() + " (no uuid), " + " [" + provider.getTypeName() + "]";
		} else {
			return getSocketAddr().getHostString() + "/" + getSocketAddr().getPort() + " " + uuid_ref.toString().substring(0, 6) + " [" + provider.getTypeName() + "]";
		}
	}
	
	/**
	 * Via SocketAddr and uuid
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
		
		if (this.uuid_ref != null) {
			if (this.uuid_ref.equals(other.uuid_ref) == false) {
				return false;
			}
		}
		
		return this.getSocketAddr().equals(other.getSocketAddr());
	}
	
	public void onErrorReturnFromNode(ErrorReturn error) {
		if (error.isDisconnectme()) {
			log.warn("Node (" + error.getNode() + ") say: \"" + error.getMessage() + "\"" + " by " + error.getCaller() + " at " + new Date(error.getDate()) + " and want to disconnect");
			close(getClass());
		} else {
			log.info("Node (" + error.getNode() + ") say: \"" + error.getMessage() + "\"" + " by " + error.getCaller() + " at " + new Date(error.getDate()));
		}
	}
	
	/**
	 * It will add to queue
	 */
	public <O, T extends Request<O>> void sendRequest(Class<T> request_class, O options) {
		T request = pool_manager.getRequestHandler().getRequestByClass(request_class);
		if (request == null) {
			throw new NullPointerException("No requests to send");
		}
		request.sendRequest(options, this);
	}
	
	/**
	 * It will add to queue
	 */
	public void sendBlock(RequestBlock to_send, boolean close_channel_after_send) {
		try {
			sendData(to_send, close_channel_after_send);
		} catch (IOException e) {
			log.error("Can't send datas to " + toString() + " > " + to_send.getRequestName() + ". Closing connection");
			close(getClass());
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
		Node n = pool_manager.get(uuid);
		if (n == null) {
			throw new IOException("This node " + toString() + " was not declared in node_list");
		} else if (equals(n) == false) {
			throw new IOException("Another node (" + n.toString() + ") was previousely add with this UUID, " + toString());
		}
	}
	
	public boolean isUUIDSet() {
		return uuid_ref != null;
	}
	
	public boolean equalsThisUUID(UUID uuid) {
		if (uuid == null) {
			return false;
		}
		if (uuid_ref == null) {
			return false;
		}
		return uuid.equals(uuid_ref);
	}
	
	public boolean equalsThisUUID(Node node) {
		if (node == null) {
			return false;
		}
		if (uuid_ref == null) {
			return false;
		}
		return node.uuid_ref.equals(uuid_ref);
	}
	
	/**
	 * Use equalsThisUUID, isUUIDSet or check for compare.
	 * @return Maybe null.
	 */
	public UUID getUUID() {
		return uuid_ref;
	}
	
	public void setLocalServerNodeAddresses(ArrayList<InetSocketAddress> local_server_node_addr) throws IOException {
		this.local_server_node_addr = local_server_node_addr;
		if (local_server_node_addr == null) {
			throw new IOException("\"local_server_node_addr\" can't to be null");
		}
		if (local_server_node_addr.isEmpty()) {
			throw new IOException("\"local_server_node_addr\" can't to be empty");
		}
		log.debug("Node " + toString() + " has some sockets addresses for its local server: " + local_server_node_addr);
	}
	
	/**
	 * @return null if not uuid or closed
	 */
	public JsonObject getAutodiscoverIDCard() {
		if (uuid_ref == null | isOpenSocket() == false) {
			return null;
		}
		JsonObject jo = new JsonObject();
		jo.addProperty("uuid", uuid_ref.toString());
		jo.add("server_addr", pool_manager.getSimpleGson().toJsonTree(local_server_node_addr));
		return jo;
	}
	
	public static UUID getUUIDFromAutodiscoverIDCard(JsonObject item) throws NullPointerException {
		if (item.has("uuid")) {
			return UUID.fromString(item.get("uuid").getAsString());
		}
		throw new NullPointerException("Missing uuid item in json " + item.toString());
	}
	
	public static ArrayList<InetSocketAddress> getAddressFromAutodiscoverIDCard(PoolManager pool_manager, JsonObject item) throws NullPointerException, UnknownHostException {
		if (item.has("server_addr") == false) {
			throw new NullPointerException("Missing addr/port items in json " + item.toString());
		}
		return pool_manager.getSimpleGson().fromJson(item.get("server_addr"), PoolManager.type_InetSocketAddress_String);
	}
	
	public void setDistantDate(long server_date) {
		long new_delay = server_date - System.currentTimeMillis();
		
		if (log.isTraceEnabled()) {
			log.trace("Node " + toString() + " delay: " + server_delta_time + " ms before, now is " + new_delay + " ms");
		}
		
		server_delta_time = new_delay;
	}
	
	/**
	 * Console usage.
	 */
	public void addToActualStatus(TableList table) {
		String host = getSocketAddr().getHostString();
		if (getSocketAddr().getPort() != pool_manager.getProtocol().getDefaultTCPPort()) {
			host = host + "/" + getSocketAddr().getPort();
		}
		String provider = this.provider.getClass().getSimpleName();
		String isopen = "open";
		if (isOpenSocket() == false) {
			isopen = "CLOSE";
		}
		String deltatime = server_delta_time + " ms";
		String uuid = "<no uuid>";
		if (uuid_ref != null) {
			uuid = uuid_ref.toString();
		}
		
		table.addRow(host, provider, isopen, deltatime, uuid);
	}
	
	public ActivityScheduledAction<Node> getScheduledAction() {
		Node current_node = this;
		return new ActivityScheduledAction<Node>() {
			
			public String getScheduledActionName() {
				return "Check if the socket is open and do pings";
			}
			
			public boolean onScheduledActionError(Exception e) {
				log.warn("Can't execute node scheduled actions");
				pool_manager.remove(current_node);
				return false;
			}
			
			public TimeUnit getScheduledActionPeriodUnit() {
				return TimeUnit.SECONDS;
			}
			
			public long getScheduledActionPeriod() {
				return 60;
			}
			
			public long getScheduledActionInitialDelay() {
				return 10;
			}
			
			public Procedure getRegularScheduledAction() {
				return () -> {
					current_node.checkIfOpen();
					pool_manager.getRequestHandler().getRequestByClass(PokeRequest.class).sendRequest(null, current_node);
				};
			}
		};
	}
	
	public boolean isOpen() {
		return channel.isOpen();
	}
	
	public long getLastActivityDate() {
		return last_activity.get();
	}
	
	private void checkIfOpen() throws IOException {
		if (isOpen() == false) {
			throw new IOException("Channel for " + toString() + " is closed");
		}
	}
	
	public void asyncRead() {
		read_buffer.clear();
		try {
			channel.read(read_buffer, this, pool_manager.getProtocol().getHandlerReader());
		} catch (ReadPendingException e) {
			log.trace("No two reads at the same time for " + toString());
		}
	}
	
	public void close(Class<?> by) {
		if (log.isDebugEnabled()) {
			log.debug("Want to close node " + toString() + ", asked by " + by.getSimpleName());
		}
		
		read_buffer.clear();
		write_buffer.clear();
		if (channel.isOpen()) {
			try {
				channel.close();
			} catch (ClosedChannelException e) {
				log.debug("Node was closed: " + e.getMessage());
			} catch (IOException e) {
				log.warn("Can't close properly channel " + toString(), e);
			}
		}
		pool_manager.remove(this);
	}
	
	private byte[] decrypt() throws GeneralSecurityException {
		read_buffer.flip();
		byte[] content = new byte[read_buffer.remaining()];
		if (log.isTraceEnabled()) {
			log.trace("Prepare " + content.length + " bytes to decrypt");
		}
		
		read_buffer.get(content, 0, content.length);
		read_buffer.clear();
		
		byte[] result = pool_manager.getProtocol().decrypt(content, 0, content.length);
		if (log.isTraceEnabled()) {
			log.trace("Get " + result.length + " bytes decrypted");
		}
		
		return result;
	}
	
	private int encrypt(byte[] data) throws GeneralSecurityException {
		if (log.isTraceEnabled()) {
			log.trace("Prepare " + data.length + " bytes to encrypt");
		}
		
		write_buffer.clear();
		
		byte[] result = pool_manager.getProtocol().encrypt(data, 0, data.length);
		
		write_buffer.put(result);
		
		write_buffer.flip();
		
		if (log.isTraceEnabled()) {
			log.trace("Set " + result.length + " bytes encrypted");
		}
		
		return result.length;
	}
	
	public void doProcessReceviedDatas() throws Exception {
		final long start_time = System.currentTimeMillis();
		last_activity.set(start_time);
		
		final byte[] datas = decrypt();
		
		try {
			RequestBlock block = new RequestBlock(pool_manager.getProtocol(), datas);
			pool_manager.getRequestHandler().onReceviedNewBlock(block, this);
			pressure_measurement_recevied.onDatas(datas.length, System.currentTimeMillis() - start_time);
		} catch (IOException e) {
			if (e instanceof WantToCloseLinkException) {
				log.debug("Handler want to close link");
				close(getClass());
				pressure_measurement_recevied.onDatas(datas.length, System.currentTimeMillis() - start_time);
				return;
			} else {
				log.error("Can't extract sended blocks " + toString(), e);
				close(getClass());
			}
		}
	}
	
	/**
	 * It will add to queue.
	 * @throws IOException if channel is closed.
	 */
	private void sendData(RequestBlock to_send, boolean close_channel_after_send) throws IOException {
		final long start_time = System.currentTimeMillis();
		
		checkIfOpen();
		
		try {
			if (log.isTraceEnabled()) {
				log.trace("Get from " + toString() + " " + to_send.toString());
			}
			int size = encrypt(to_send.getBytes(pool_manager.getProtocol()));
			
			channel.write(write_buffer, this, pool_manager.getProtocol().getHandlerWriter(close_channel_after_send));
			
			pressure_measurement_sended.onDatas(size, System.currentTimeMillis() - start_time);
		} catch (Exception e) {
			log.error("Can't send datas " + toString(), e);
		}
	}
	
	/**
	 * @return channel.hashCode
	 */
	public int hashCode() {
		return channel.hashCode();
	}
	
}
