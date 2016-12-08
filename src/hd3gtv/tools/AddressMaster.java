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
 * Copyright (C) hdsdi3g for hd3g.tv 3 déc. 2016
 * 
*/
package hd3gtv.tools;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;

public class AddressMaster {
	
	private static final Logger log = Logger.getLogger(AddressMaster.class);
	
	private HashSet<InetAddress> all_host_addr;
	private ArrayList<InetAddress> all_external_addr;
	
	public AddressMaster() throws IOException {
		all_host_addr = new HashSet<>();
		all_external_addr = new ArrayList<>();
		
		Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
		while (interfaces.hasMoreElements()) {
			NetworkInterface currentInterface = interfaces.nextElement();
			Enumeration<InetAddress> addresses = currentInterface.getInetAddresses();
			while (addresses.hasMoreElements()) {
				InetAddress currentAddress = addresses.nextElement();
				if (isLocalAddress(currentAddress) == false) {
					all_external_addr.add(currentAddress);
				}
				all_host_addr.add(currentAddress);
			}
		}
		
		if (all_external_addr.isEmpty()) {
			throw new IOException("No external addresses !");
		}
		log.debug("Add host external address: " + all_external_addr);
	}
	
	public static boolean isLocalAddress(InetAddress addr) {
		return addr.isLoopbackAddress() | addr.isAnyLocalAddress() | addr.isLinkLocalAddress() | addr.isMulticastAddress();
	}
	
	/**
	 * @return unmodifiableList
	 */
	public List<InetAddress> getCurrentExternalAddresses() {
		return Collections.unmodifiableList(all_external_addr);
	}
	
	/**
	 * @param addr can be local and distant
	 */
	public boolean isMe(InetAddress addr) {
		return all_host_addr.contains(addr);
	}
	
	/**
	 * @return local and distant mergued.
	 */
	public List<InetAddress> getAddresses() {
		return Collections.unmodifiableList(all_host_addr.stream().collect(Collectors.toList()));
	}
	
}
