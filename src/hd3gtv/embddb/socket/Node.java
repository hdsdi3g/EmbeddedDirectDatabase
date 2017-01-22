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
import java.nio.channels.AsynchronousSocketChannel;
import java.util.ArrayList;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;

import com.google.gson.JsonObject;

import hd3gtv.embddb.PoolManager;
import hd3gtv.embddb.dialect.ErrorReturn;
import hd3gtv.embddb.dialect.PokeRequest;
import hd3gtv.embddb.dialect.Request;
import hd3gtv.internaltaskqueue.ActivityScheduledAction;
import hd3gtv.internaltaskqueue.Procedure;
import hd3gtv.tools.TableList;

public class Node {
	
	private static final Logger log = Logger.getLogger(Node.class);
	
	private PoolManager pool_manager;
	private final ChannelBucket channelbucket;
	private ArrayList<InetSocketAddress> local_server_node_addr;
	
	private UUID uuid_ref;
	private long server_delta_time;
	private SocketProvider provider;
	private InetSocketAddress socket_addr;
	
	public Node(SocketProvider provider, PoolManager pool_manager, AsynchronousSocketChannel channel) {
		this.provider = provider;
		if (provider == null) {
			throw new NullPointerException("\"provider\" can't to be null");
		}
		this.pool_manager = pool_manager;
		if (pool_manager == null) {
			throw new NullPointerException("\"pool_manager\" can't to be null");
		}
		if (channel == null) {
			throw new NullPointerException("\"channel\" can't to be null");
		}
		
		channelbucket = new ChannelBucket(pool_manager, this, channel);
		try {
			socket_addr = channelbucket.getRemoteSocketAddr();
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
					socket_addr = channelbucket.getRemoteSocketAddr();
				} catch (IOException e) {
					log.debug("Can't get addr", e);
				}
		}
		return socket_addr;
	}
	
	public boolean isOpenSocket() {
		return channelbucket.isOpen();
	}
	
	public void close(Class<?> by) {
		channelbucket.close(by);
	}
	
	public String toString() {
		if (uuid_ref == null) {
			return getSocketAddr().getHostString() + " port " + getSocketAddr().getPort() + " (no uuid), " + provider.getClass().getSimpleName();
		} else {
			return getSocketAddr().getHostString() + " port " + getSocketAddr().getPort() + ", " + uuid_ref + ", " + provider.getClass().getSimpleName();
		}
	}
	
	public ChannelBucket getChannelbucket() {
		return channelbucket;
	}
	
	/**
	 * Via Channelbucket
	 */
	public int hashCode() {
		return this.getChannelbucket().hashCode();
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
			channelbucket.close(getClass());
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
			channelbucket.sendData(to_send, close_channel_after_send);
		} catch (IOException e) {
			log.error("Can't send datas to " + toString() + " > " + to_send.getRequestName() + ". Closing connection");
			channelbucket.close(getClass());
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
			pool_manager.getNodeList().updateUUID(this);
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
	
	public static final long MAX_TOLERANCE_DELTA_TIME_WITH_SERVER = TimeUnit.SECONDS.toMillis(30);
	
	/**
	 * Network jitter filter
	 */
	public static final long MIN_TOLERANCE_DELTA_TIME_WILL_UPDATE_CHANGE = TimeUnit.MILLISECONDS.toMillis(200);
	
	public void setDistantDate(long server_date) throws IOException {
		long new_delay = server_date - System.currentTimeMillis();
		
		if (Math.abs(server_delta_time - new_delay) < MIN_TOLERANCE_DELTA_TIME_WILL_UPDATE_CHANGE) {
			return;
		}
		
		if (log.isTraceEnabled()) {
			log.trace("Node " + toString() + " delay: " + server_delta_time + " ms before, now is " + new_delay + " ms");
		}
		
		server_delta_time = new_delay;
		
		if ((server_delta_time != new_delay) && (Math.abs(server_delta_time) > MAX_TOLERANCE_DELTA_TIME_WITH_SERVER)) {
			log.warn("Big delay with node " + toString() + ": " + server_delta_time + " ms. Please check the NTP setup with this host and the node ! In the meantime, this node will be disconned.");
			throw new IOException("Invalid node delay (" + Math.abs(server_delta_time) + ")");
		}
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
				pool_manager.getNodeList().remove(current_node);
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
					current_node.channelbucket.checkIfOpen();
					pool_manager.getRequestHandler().getRequestByClass(PokeRequest.class).sendRequest(null, current_node);
				};
			}
		};
	}
	
}
