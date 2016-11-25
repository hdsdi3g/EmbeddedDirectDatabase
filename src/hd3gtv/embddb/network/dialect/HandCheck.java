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
package hd3gtv.embddb.network.dialect;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;

import org.apache.log4j.Logger;

import hd3gtv.embddb.network.Protocol;
import hd3gtv.embddb.network.RequestBlock;

public class HandCheck implements Dialog {
	
	private static Logger log = Logger.getLogger(HandCheck.class);
	
	private Hello hello;
	private Welcome welcome;
	private Protocol protocol;
	
	public HandCheck(Protocol protocol) {
		this.protocol = protocol;
		if (protocol == null) {
			throw new NullPointerException("\"protocol\" can't to be null");
		}
		
		hello = new Hello(c -> {
			System.out.println(c.get(0).getName()); // TODO callback if it's ok.
		});
		
		welcome = new Welcome();
	}
	
	public ClientSayToServer getClientSentenceToSendToServer() {
		return hello;
	}
	
	public ServerSayToClient getServerSentenceToSendToClient(InetAddress client, ArrayList<RequestBlock> send) {
		try {
			hello.checkMatchVersionWithClient(protocol, send);
		} catch (IOException e) {
			log.warn("Mismatch protocol versions with distant client " + client, e);
			return new ErrorResponse("Mismatch protocol versions");
		}
		return welcome;
	}
	
	/**
	 * Should match with getClientSentenceToSendToServer
	 */
	public boolean checkIfClientToServerRequestIsForThis(ArrayList<RequestBlock> blocks) {
		return blocks.get(0).getName().equals("hello");
	}
	
	/**
	 * Should match with getServerSentenceToSendToClient
	 */
	public boolean checkIfServerToClientResponseIsForThis(ArrayList<RequestBlock> blocks) {
		String name = blocks.get(0).getName();
		return name.equals("welcome") | name.equals("error");
	}
	
	public Welcome getWelcome() {
		return welcome;
	}
	
}
