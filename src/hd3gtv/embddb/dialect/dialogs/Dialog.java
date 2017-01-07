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

import java.net.InetAddress;
import java.util.ArrayList;

import hd3gtv.embddb.ClientUnit;
import hd3gtv.embddb.socket.RequestBlock;

/**
 * All new Dialogs must be declared to PoolManager
 * R Object to sent do Dialog for create request (client side)
 * O server response sended to client (also client side)
 */
public interface Dialog<R, O> {
	
	/**
	 * Executed in client side.
	 * @param client this client do the request
	 * @param request the data to send to distant server
	 * @return the converter which does the convertion R to raw data (sended) and raw datas (recevied) to O.
	 * @see checkIfServerResponseIsForThisClient for the response routing
	 */
	public ClientSayToServer<O> getClientSentenceToSendToServer(ClientUnit client, R request);
	
	/**
	 * Executed server side.
	 * @see checkIfClientRequestIsForThisServer for the request routing
	 * @param client the distant client which does the request.
	 * @param send the raw data sended (the ClientSayToServer<O> made it, in client side, from R).
	 * @return the raw data to responds to the client (the ClientSayToServer<O> in client side will open it to O).
	 */
	public ServerSayToClient getServerSentenceToSendToClient(InetAddress client, ArrayList<RequestBlock> send);
	
	public boolean checkIfClientRequestIsForThisServer(ArrayList<RequestBlock> blocks);
	
	public boolean checkIfServerResponseIsForThisClient(ArrayList<RequestBlock> blocks);
	
}
