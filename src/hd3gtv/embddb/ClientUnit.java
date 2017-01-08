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
 * Copyright (C) hdsdi3g for hd3g.tv 25 nov. 2016
 * 
*/
package hd3gtv.embddb;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.function.Consumer;

import org.apache.log4j.Logger;

import hd3gtv.embddb.dialect.dialogs.ClientList;
import hd3gtv.internaltaskqueue.ParametedProcedure;

/**
 * @deprecated
 */
public class ClientUnit {
	
	private static Logger log = Logger.getLogger(ClientUnit.class);
	
	private EDDBClient internal;
	private PoolManager pool;
	private InetSocketAddress server;
	
	ClientUnit(PoolManager pool, InetSocketAddress server, ConnectHandler connect_handler) throws IOException {
		this.pool = pool;
		if (pool == null) {
			throw new NullPointerException("\"pool\" can't to be null");
		}
		this.server = server;
		if (server == null) {
			throw new NullPointerException("\"server\" can't to be null");
		}
		
		log.debug("Create client: " + server);
		
		internal = new EDDBClient(pool.getProtocol(), server);
		internal.connect(this, connect_handler);
	}
	
	public String toString() {
		return getClass().getSimpleName() + "_" + server;
	}
	
	boolean isThisInternalClient(EDDBClient client) {
		return internal.equals(client);
	}
	
	public InetSocketAddress getConnectedServer() {
		return server;
	}
	
	/**
	 * Please, do it with protocol for the let to remove the local server references.
	 */
	public void close() {
		try {
			internal.close();
		} catch (Exception e) {
			log.error("Can't close client to " + server, e);
		}
		pool.removeClient(this);
	}
	
	/**
	 * Fully async.
	 */
	private <T, O> void internalRequest(Class<? extends Dialog<T, O>> dialog_class, T request, ParametedProcedure<O> response, ParametedProcedure<Exception> error) {
		@SuppressWarnings("unchecked")
		Dialog<T, O> dialog = (Dialog<T, O>) pool.getByClass(dialog_class);
		
		ClientSayToServer<O> to_send = dialog.getClientSentenceToSendToServer(this, request);
		
		if (to_send == null) {
			log.debug(dialog_class + " is canceled: no requests to do");
			return;
		}
		
		try {
			internal.request(to_send.getBlocksToSendToServer(), (blocks, server) -> {
				try {
					if (blocks.isEmpty()) {
						error.process(new IOException("No blocks returned for " + dialog_class.getName()));
					} else if (blocks.get(0).getName().equalsIgnoreCase("error")) {
						log.warn("Error in server side: " + blocks.get(0).getDatasAsString() + "for " + dialog_class.getName());
					} else if (pool.getServerToClientResponseFirstValid(blocks).equals(dialog)) {
						response.process(to_send.clientReceviedBlocksFromServer(blocks));
					} else {
						log.warn("Can't process response " + blocks + "for " + dialog_class.getName());
					}
				} catch (Exception e) {
					try {
						error.process(e);
					} catch (Exception ee) {
						log.error("Can't process error for " + dialog_class.getName() + ", from " + e.getMessage(), ee);
					}
				}
			});
		} catch (Exception e) {
			try {
				error.process(e);
			} catch (Exception ee) {
				log.error("Can't process error, from " + e.getMessage(), ee);
			}
		}
	}
	
	public void doHandCheck(Consumer<ClientUnit> on_done) {
		internalRequest(HandCheck.class, null, label -> {
			log.debug("HandCheck is correct: " + label);
			on_done.accept(this);
		}, e -> {
			log.error("Can't do HandCheck with server " + server, e);
			pool.removeClient(this);
		});
	}
	
	public String getActualStatus() {
		StringBuilder sb = new StringBuilder();
		sb.append("Server: ");
		sb.append(server.getHostString() + "/" + server.getHostName() + ":" + server.getPort());
		sb.append(", last delta time: ");
		sb.append(server_delta_time);
		sb.append(" ms.");
		return sb.toString();
	}
	
	public void getFromConnectedServerThisActualClientList(ArrayList<InetSocketAddress> all_current_connected) {
		internalRequest(ClientList.class, all_current_connected, distant_list -> {
			distant_list.forEach(s -> {
				try {
					pool.createClient(s);
				} catch (Exception e1) {
					log.warn("Can't to connect to server " + s + ", but it really exists and should be contactable (" + server + " can do it).", e1);
				}
			});
		}, e -> {
			log.error("Can't get client list from server " + server, e);
			pool.removeClient(this);
		});
	}
	
	/**
	 * Say to my connected distant server to remove all references for me. After that, I'll close the connection.
	 */
	public void disconnectMe() {
		log.info("Start the disconnect procedure for " + server);
		internalRequest(DisconnectNode.class, null, thisvoid -> {
			close();
		}, e -> {
			log.error("Can't be polite with this server " + server + ". Anyway, I disconnect to it myself.", e);
			close();
		});
	}
	
}
