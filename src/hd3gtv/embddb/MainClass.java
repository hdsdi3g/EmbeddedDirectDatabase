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
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import hd3gtv.embddb.network.PoolManager;

public class MainClass {
	
	private static Logger log = Logger.getLogger(MainClass.class);
	
	public static void main(String[] args) throws Exception {
		Security.addProvider(new BouncyCastleProvider());
		
		PoolManager poolmanager = new PoolManager("test");
		poolmanager.startLocalServers();
		
		// TODO manage white/black range addr list for autodiscover
		
		Thread.sleep(50);
		
		Properties conf = new Properties();
		conf.load(FileUtils.openInputStream(new File("conf.properties")));
		
		poolmanager.setBootstrapPotentialNodes(importConf(poolmanager, conf, poolmanager.getProtocol().getDefaultTCPPort()));
		poolmanager.connectToBootstrapPotentialNodes("Startup");
		
		Thread.sleep(50);
		
		poolmanager.startConsole();
	}
	
	private static List<InetSocketAddress> importConf(PoolManager poolmanager, Properties conf, int default_port) throws Exception {
		if (conf.containsKey("hosts") == false) {
			throw new NullPointerException("No hosts in configuration");
		}
		String hosts = conf.getProperty("hosts");
		
		return Arrays.asList(hosts.split(" ")).stream().map(addr -> {
			if (addr.isEmpty() == false) {
				log.debug("Found host in configuration: " + addr);
				return new InetSocketAddress(addr, default_port);
			} else {
				return null;
			}
		}).filter(addr -> {
			return addr != null;
		}).collect(Collectors.toList());
	}
	
}
