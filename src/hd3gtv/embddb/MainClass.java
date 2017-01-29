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
import java.net.URL;

import org.apache.log4j.Logger;

import hd3gtv.factorydemo.GlobalObjectFactory;
import hd3gtv.factorydemo.conftree.GOFConfiguration;
import hd3gtv.factorydemo.dm1.Demo;

public class MainClass {
	
	private static Logger log = Logger.getLogger(MainClass.class);
	
	public static void main(String[] args) throws Exception {
		
		GOFConfiguration g = new GOFConfiguration();
		g.createDummyValues();
		g.getConfigurationInYAML(System.out);
		// g.dumpActualConfigurationInProperties().list(System.out);
		// System.out.println(g.get("test3.3.0"));
		System.out.println(g.getConfigurationInJsonString());
		System.exit(0);
		
		GlobalObjectFactory gof = new GlobalObjectFactory();
		Demo demo = gof.create(Demo.class);
		gof.create(URL.class);// TODO create correct handler for URL
		gof.create(File.class);// TODO create correct handler for File
		System.out.println(gof.toString());
	}
	
}
