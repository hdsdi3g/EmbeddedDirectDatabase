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
 * Copyright (C) hdsdi3g for hd3g.tv 20 nov. 2016
 * 
*/
package hd3gtv.embddb;

import java.net.InetAddress;
import java.security.Security;

import org.apache.log4j.Logger;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import hd3gtv.internaltaskqueue.ITQueue;

public class MainClass {
	
	private static Logger log = Logger.getLogger(MainClass.class);
	
	public static void main(String[] args) throws Exception {
		Security.addProvider(new BouncyCastleProvider());
		
		ITQueue itqueue = new ITQueue(2);
		PoolManager poolmanager = new PoolManager(itqueue);
		
		if (args.length > 0) {
			if (args[0].equals("server")) {
				poolmanager.startServer();
			}
		}
		
		// TODO do the connection client pool (list all actual clients, and display properties)
		// TODO get the client connected list by server and share the list (auto-discover)
		// TODO get the auto-discover list by client, and update the actual connection pool
		// TODO de propagation action: client -> server ->> all server's clients
		// TODO do the client / server shutdown propagation action
		
		Thread.sleep(50);
		poolmanager.createClient(InetAddress.getByName("127.0.0.1")).doHandCheck();
		
		poolmanager.startConsole();
	}
	
}
