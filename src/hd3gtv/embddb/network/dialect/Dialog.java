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

import java.net.InetAddress;
import java.util.ArrayList;

import hd3gtv.embddb.network.RequestBlock;

/**
 * All new Dialogs must be declared to PoolManager
 */
public interface Dialog {
	
	public ClientSayToServer getClientSentenceToSendToServer();
	
	public ServerSayToClient getServerSentenceToSendToClient(InetAddress client, ArrayList<RequestBlock> send);
	
	public boolean checkIfClientToServerRequestIsForThis(ArrayList<RequestBlock> blocks);
	
	public boolean checkIfServerToClientResponseIsForThis(ArrayList<RequestBlock> blocks);
	
}
