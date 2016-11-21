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
import java.util.ArrayList;

public class MainClass {
	
	public static void main(String[] args) throws Exception {
		Protocol protocol = new Protocol("test");
		
		EDDBNode node = new EDDBNode(protocol, (list, addr) -> {
			// TODO
			return new ArrayList<>();
		});
		node.start();
		Thread.sleep(50);
		
		EDDBClient client = new EDDBClient(protocol, InetAddress.getByName("127.0.0.1"));
		client.start();
		
		while (true) {
			Thread.sleep(10);
		}
	}
	
}
