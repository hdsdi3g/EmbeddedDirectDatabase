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

import java.io.File;
import java.net.InetSocketAddress;
import java.security.Security;
import java.util.Arrays;
import java.util.Properties;
import java.util.function.Consumer;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import hd3gtv.embddb.socket.ConnectionCallback;
import hd3gtv.embddb.socket.Node;
import hd3gtv.internaltaskqueue.ITQueue;

public class MainClass {
	
	private static Logger log = Logger.getLogger(MainClass.class);
	
	public static void main(String[] args) throws Exception {
		Security.addProvider(new BouncyCastleProvider());
		
		ITQueue itqueue = new ITQueue(2);
		PoolManager poolmanager = new PoolManager(itqueue, "test");
		poolmanager.startServer(InetSocketAddress.createUnresolved("0.0.0.0", poolmanager.getProtocol().getDefaultTCPPort()));
		
		// TODO manage white/black range addr list for autodiscover
		
		Thread.sleep(50);
		
		Properties conf = new Properties();
		conf.load(FileUtils.openInputStream(new File("conf.properties")));
		
		importConf(conf, poolmanager.getProtocol().getDefaultTCPPort(), addr -> {
			try {
				poolmanager.declareNewPotentialDistantServer(addr, new ConnectionCallback() {
					
					public void onNewConnectedNode(Node node) {
						log.info("Connected to node (declared by configuration): " + node);
					}
					
					public void onLocalServerConnection(InetSocketAddress server) {
						log.warn("Can't add local server (" + server.getHostString() + ":" + server.getPort() + ") not node list. Check your configuration.");
					}
					
					public void alreadyConnectedNode(Node node) {
						log.info("Node is already connected: " + node);
					}
				});
			} catch (Exception e) {
				log.error("Can't create node: " + addr, e);
			}
		});
		
		Thread.sleep(50);
		
		poolmanager.startConsole();
	}
	
	private static void importConf(Properties conf, int default_port, Consumer<InetSocketAddress> callback_addr) throws Exception {
		if (conf.containsKey("hosts") == false) {
			throw new NullPointerException("No hosts in configuration");
		}
		String hosts = conf.getProperty("hosts");
		
		Arrays.asList(hosts.split(" ")).stream().map(addr -> {
			log.debug("Found host in configuration: " + addr);
			return new InetSocketAddress(addr, default_port);
		}).forEach(callback_addr::accept);
	}
	
}
