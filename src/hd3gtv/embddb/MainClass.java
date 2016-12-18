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

import java.security.Security;

import org.apache.log4j.Logger;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import hd3gtv.factory.ConfiguredFactory;
import hd3gtv.internaltaskqueue.ITQueue;

public class MainClass {
	
	private static Logger log = Logger.getLogger(MainClass.class);
	
	public static void main(String[] args) throws Exception {
		Security.addProvider(new BouncyCastleProvider());
		
		System.exit(0);
		
		// TODO Create multiway configuration: app default props (ro), local flat file (rw), external updating (rw), with namespace scope and level (debug/prod)
		// TODO found a method for key value[] configuration
		
		ConfiguredFactory cfact = new ConfiguredFactory();
		ITQueue q = cfact.create(ITQueue.class, true);
		System.out.println(q);
		
		/*ITQueue itqueue = new ITQueue(2);
				PoolManager poolmanager = new PoolManager(itqueue, "test");
				poolmanager.setEnableLoopClients(true);
				poolmanager.startServer();*/
		
		// TODO manage white/black range addr list for autodiscover
		
		System.exit(0);
		
		Thread.sleep(50);
		// poolmanager.createClient(new InetSocketAddress("127.0.0.1", poolmanager.getProtocol().getDefaultTCPPort())).doHandCheck();// FIXME manage default list
		
		// poolmanager.startConsole();
	}
	
}
