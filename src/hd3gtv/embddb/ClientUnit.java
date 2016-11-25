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

import org.apache.log4j.Logger;

import hd3gtv.embddb.network.EDDBClient;
import hd3gtv.embddb.network.dialect.ClientSayToServer;
import hd3gtv.embddb.network.dialect.HandCheck;

public class ClientUnit {
	
	private static Logger log = Logger.getLogger(ClientUnit.class);
	
	private EDDBClient internal;
	private PoolManager pool;
	private InetSocketAddress server;
	
	ClientUnit(PoolManager pool, InetSocketAddress server) throws IOException {
		this.pool = pool;
		if (pool == null) {
			throw new NullPointerException("\"pool\" can't to be null");
		}
		if (server == null) {
			throw new NullPointerException("\"server\" can't to be null");
		}
		
		try {
			internal = new EDDBClient(pool.getProtocol(), server);
			internal.connect();
		} catch (Exception e) {
			throw new IOException("Can't create TCP Client to " + server, e);
		}
		pool.declareClient(this);
	}
	
	public void close() {
		try {
			internal.close();
		} catch (Exception e) {
			log.error("Can't close client to " + server, e);
		}
		pool.removeClient(this);
	}
	
	public void doHandCheck() {
		HandCheck hc = (HandCheck) pool.getByClass(HandCheck.class);
		ClientSayToServer to_send = hc.getClientSentenceToSendToServer();
		
		try {
			internal.request(to_send.getBlocksToSendToServer(), (blocks, server) -> {
				if (pool.getServerToClientResponseFirstValid(blocks).equals(hc)) {
					try {
						hc.getWelcome().checkMatchVersionWithServer(pool.getProtocol(), blocks);
						log.debug("HandCheck is correct");
					} catch (Exception e) {
						log.error("Version error with server " + server + " and local client ", e);
						pool.removeClient(this);
					}
				}
			});
		} catch (Exception e) {
			log.error("Can't do HandCheck with server " + server, e);
			pool.removeClient(this);
		}
	}
	
}
