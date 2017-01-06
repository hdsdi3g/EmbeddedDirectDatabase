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
 * Copyright (C) hdsdi3g for hd3g.tv 24 nov. 2016
 * 
*/
package hd3gtv.embddb.dialect.dialogs;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;

import org.apache.log4j.Logger;

import hd3gtv.embddb.ClientUnit;
import hd3gtv.embddb.dialect.ClientSayToServer;
import hd3gtv.embddb.dialect.Dialog;
import hd3gtv.embddb.dialect.ServerSayToClient;
import hd3gtv.embddb.dialect.Version;
import hd3gtv.embddb.socket.Protocol;
import hd3gtv.embddb.socket.RequestBlock;
import hd3gtv.embddb.tools.ArrayWrapper;
import hd3gtv.internaltaskqueue.ParametedWithResultProcedure;

public class HandCheck implements Dialog<Void, String> {
	
	private static Logger log = Logger.getLogger(HandCheck.class);
	
	private Hello hello;
	private Protocol protocol;
	
	public HandCheck(Protocol protocol) {
		this.protocol = protocol;
		if (protocol == null) {
			throw new NullPointerException("\"protocol\" can't to be null");
		}
		
		hello = new Hello(blocks -> {
			if (blocks.size() != 2) {
				throw new IOException("Bad block count " + blocks.size());
			}
			if (Version.resolveFromString(new String(blocks.get(1).getDatas())) != Protocol.VERSION) {
				throw new IOException("Bad version " + new String(blocks.get(1).getDatas()));
			}
			
			return blocks.get(0).getDatasAsString();
		});
	}
	
	public ClientSayToServer<String> getClientSentenceToSendToServer(ClientUnit client, Void request) {
		return hello;
	}
	
	public ServerSayToClient getServerSentenceToSendToClient(InetAddress client, ArrayList<RequestBlock> send) {
		try {
			hello.checkMatchVersionWithClient(protocol, send);
		} catch (IOException e) {
			log.warn("Mismatch protocol versions with distant client " + client, e);
			return new ErrorResponse("Mismatch protocol versions");
		}
		return () -> {
			return ArrayWrapper.asArrayList(new RequestBlock("welcome", "Welcome from EmbDDB"), new RequestBlock("version", Protocol.VERSION.toString()));
		};
	}
	
	/**
	 * Should match with getClientSentenceToSendToServer
	 */
	public boolean checkIfClientRequestIsForThisServer(ArrayList<RequestBlock> blocks) {
		return blocks.get(0).getName().equals("hello");
	}
	
	/**
	 * Should match with getServerSentenceToSendToClient
	 */
	public boolean checkIfServerResponseIsForThisClient(ArrayList<RequestBlock> blocks) {
		String name = blocks.get(0).getName();
		return name.equals("welcome") | name.equals("error");
	}
	
	class Hello extends ClientSayToServer<String> {
		
		public Hello(ParametedWithResultProcedure<ArrayList<RequestBlock>, String> callback) {
			super(callback);
		}
		
		public ArrayList<RequestBlock> getBlocksToSendToServer() {
			return ArrayWrapper.asArrayList(new RequestBlock("hello", "Hello from EmbDDB"), new RequestBlock("version", Protocol.VERSION.toString()));
		}
		
		public void checkMatchVersionWithClient(Protocol protocol, ArrayList<RequestBlock> blocks) throws IOException {
			if (blocks.size() != 2) {
				throw new IOException("Bad block count " + blocks.size());
			}
			if (Version.resolveFromString(new String(blocks.get(1).getDatas())) != Protocol.VERSION) {
				throw new IOException("Bad version " + new String(blocks.get(1).getDatas()));
			}
		}
		
	}
	
}
