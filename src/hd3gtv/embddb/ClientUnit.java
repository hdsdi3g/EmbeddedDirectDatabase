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
import java.util.UUID;

import org.apache.log4j.Logger;

import hd3gtv.embddb.dialect.ClientSayToServer;
import hd3gtv.embddb.dialect.Dialog;
import hd3gtv.embddb.dialect.HandCheck;
import hd3gtv.embddb.dialect.PingPongTime;
import hd3gtv.embddb.network.EDDBClient;
import hd3gtv.internaltaskqueue.ParametedProcedure;

public class ClientUnit {
	
	private static Logger log = Logger.getLogger(ClientUnit.class);
	
	private EDDBClient internal;
	private PoolManager pool;
	private InetSocketAddress server;
	
	private long server_delta_time;
	
	ClientUnit(PoolManager pool, InetSocketAddress server) throws IOException {
		this.pool = pool;
		if (pool == null) {
			throw new NullPointerException("\"pool\" can't to be null");
		}
		this.server = server;
		if (server == null) {
			throw new NullPointerException("\"server\" can't to be null");
		}
		server_delta_time = 0;
		
		try {
			internal = new EDDBClient(pool.getProtocol(), server);
			internal.connect();
		} catch (Exception e) {
			throw new IOException("Can't create TCP Client to " + server, e);
		}
		pool.declareClient(this);
	}
	
	public String toString() {
		return getClass().getSimpleName() + "_" + server;
	}
	
	boolean isThisInternalClient(EDDBClient client) {
		return internal.equals(client);
	}
	
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
	
	public void doHandCheck() {
		internalRequest(HandCheck.class, null, label -> {
			log.debug("HandCheck is correct: " + label);
		}, e -> {
			log.error("Can't do HandCheck with server " + server, e);
			pool.removeClient(this);
		});
	}
	
	public void doPingPong() {
		internalRequest(PingPongTime.class, UUID.randomUUID(), server_date -> {
			if (server_date == null) {
				throw new IOException("Error with UUID, network protocol is buggy");
			}
			long new_delay = server_date - System.currentTimeMillis();
			
			if (Math.abs(server_delta_time - new_delay) < 5) {
				return;
			}
			
			if (log.isTraceEnabled()) {
				log.trace("Server " + server + " delay: " + server_delta_time + " ms before, now is " + new_delay + " ms");
			}
			server_delta_time = new_delay;
			
			// TODO add warn if big delay time
		}, e -> {
			log.error("Can't do Ping with server " + server, e);
			pool.removeClient(this);
		});
	}
	
}
