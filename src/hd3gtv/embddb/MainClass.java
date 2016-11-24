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
import java.util.ArrayList;
import java.util.Arrays;

import org.apache.log4j.Logger;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jfree.util.Log;

import hd3gtv.embddb.network.EDDBClient;
import hd3gtv.embddb.network.EDDBNode;
import hd3gtv.embddb.network.Protocol;

public class MainClass {
	
	private static Logger log = Logger.getLogger(MainClass.class);
	
	public static void main(String[] args) throws Exception {
		Security.addProvider(new BouncyCastleProvider());
		
		Protocol protocol = new Protocol("test");
		
		// TODO add Dialog API: list for server and client, add function(Callback) in client.
		
		EDDBNode node = new EDDBNode(protocol, (list, addr) -> {
			list.forEach(block -> {
				log.info("Request block: " + block.getName() + ", size " + block.getLen() + " from " + addr);
			});
			
			return new ArrayList<>(Arrays.asList(protocol.createWelcome()));
		});
		node.start();
		Thread.sleep(50);
		
		EDDBClient client = new EDDBClient(protocol, InetAddress.getByName("127.0.0.1"));
		client.connect(() -> {
			Log.info("Connection is ok");
			return null;
		}, log);
		
		while (true) {
			Thread.sleep(10);
		}
	}
	
}
