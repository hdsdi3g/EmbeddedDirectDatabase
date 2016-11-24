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

import java.util.ArrayList;

import hd3gtv.embddb.network.RequestBlock;

public class HandCheck implements Dialog {
	
	// TODO declare all Dialogs in server callback, declare all Dialogs in client functions
	
	public ClientSayToServer getClientSentenceToSendToServer() {
		return new Hello(c -> {
			System.out.println(c.get(0).getName()); // TODO callback if it's ok.
		});
	}
	
	public ServerSayToClient getServerSentenceToSendToClient(ArrayList<RequestBlock> send) {
		return new Welcome(c -> {
			System.out.println(c.get(0).getName()); // TODO do empty
		});
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
	public boolean checkIfServerToClientRequestIsForThis(ArrayList<RequestBlock> blocks) {
		return blocks.get(0).getName().equals("welcome");
	}
	
}
