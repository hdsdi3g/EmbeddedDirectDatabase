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
 * Copyright (C) hdsdi3g for hd3g.tv 8 janv. 2017
 * 
*/
package hd3gtv.embddb.dialect;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.UUID;

import org.apache.log4j.Logger;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import hd3gtv.embddb.NodeList;
import hd3gtv.embddb.socket.ConnectionCallback;
import hd3gtv.embddb.socket.Node;
import hd3gtv.embddb.socket.RequestBlock;
import hd3gtv.internaltaskqueue.ParametedProcedure;

public class NodelistRequest extends Request<Void> {
	
	private static Logger log = Logger.getLogger(NodelistRequest.class);
	
	public NodelistRequest(RequestHandler request_handler) {
		super(request_handler);
	}
	
	public String getHandleName() {
		return "nodelist";
	}
	
	private static final JsonParser parser = new JsonParser();
	
	public void onRequest(RequestBlock block, Node source_node) {
		try {
			JsonArray list = parser.parse(block.getByName("json").getDatasAsString()).getAsJsonArray();
			if (list.size() == 0) {
				return;
			}
			ArrayList<JsonObject> jo_list = new ArrayList<>(list.size());
			
			list.forEach(je -> {
				jo_list.add(je.getAsJsonObject());
			});
			
			NodeList node_list = pool_manager.getNodeList();
			
			UUID this_uuid = pool_manager.getUUIDRef();
			
			ParametedProcedure<InetSocketAddress> operation_declare = addr -> {
				pool_manager.declareNewPotentialDistantServer(addr, new ConnectionCallback() {
					
					public void onNewConnectedNode(Node node) {
						log.info("Autodiscover allowed to connect to " + node + " (provided by " + source_node + ")");
					}
					
					public void onLocalServerConnection(InetSocketAddress server) {
						log.warn("Autodiscover cant add this server (" + server + ")  as node (provided by " + source_node + ")");
					}
					
					public void alreadyConnectedNode(Node node) {
						log.info("Autodiscover cant add an already connected node (" + node + " provided by " + source_node + ")");
					}
				});
			};
			
			jo_list.stream().filter(jo -> {
				/**
				 * Remove all actual UUID from list
				 */
				try {
					UUID uuid = Node.getUUIDFromAutodiscoverIDCard(jo);
					return uuid.equals(this_uuid) == false && node_list.contains(uuid) == false;
				} catch (Exception e) {
					log.warn("Invalid UUID format in json " + list.toString() + " from " + source_node, e);
					return false;
				}
			}).map(jo -> {
				/**
				 * Get all addr from list
				 */
				try {
					return Node.getAddressFromAutodiscoverIDCard(pool_manager, jo);
				} catch (Exception e) {
					log.warn("Invalid Addr format in json " + list.toString() + " from " + source_node, e);
					return null;
				}
			}).filter(server_node_addr -> {
				/**
				 * Remove null addr (by security)
				 */
				return server_node_addr != null;
			}).forEach(server_node_addr -> {
				/**
				 * 
				 */
				// TODO detect the best addr to connect with the best network to connect it
				// TODO manage localhost connections
				/*pool_manager.getQueue().addToQueue(addr, operation_declare, (error_addr, e) -> {
					log.error("Autodiscover operation can't to connect to node via " + error_addr);
				});*/
			});
		} catch (Exception e) {
			
		}
	}
	
	public RequestBlock createRequest(Void opt) {
		return new RequestBlock(getHandleName()).createEntry("json", pool_manager.getNodeList().makeAutodiscoverList().toString());
	}
	
	protected boolean isCloseChannelRequest(Void options) {
		return false;
	}
	
}
