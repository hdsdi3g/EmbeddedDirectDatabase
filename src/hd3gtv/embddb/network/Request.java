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
 * Copyright (C) hdsdi3g for hd3g.tv 7 janv. 2017
 * 
*/
package hd3gtv.embddb.network;

/**
 * @param T Send T to dest_node
 */
public abstract class Request<T> {
	
	protected PoolManager pool_manager;
	protected RequestHandler request_handler;
	
	public Request(RequestHandler request_handler) {
		this.request_handler = request_handler;
		if (request_handler == null) {
			throw new NullPointerException("\"request_handler\" can't to be null");
		}
		this.pool_manager = request_handler.getPoolManager();
		if (pool_manager == null) {
			throw new NullPointerException("\"pool_manager\" can't to be null");
		}
	}
	
	/**
	 * @return must equals to first createRequest RequestBlock.name result
	 */
	public abstract String getHandleName();
	
	public abstract void onRequest(DataBlock block, Node source_node);
	
	/**
	 * @return first RequestBlock.name must equals to getHandleName()
	 */
	public abstract DataBlock createRequest(T options);
	
	public final void sendRequest(T options, Node dest_node) throws NullPointerException, IndexOutOfBoundsException {
		DataBlock block = createRequest(options);
		if (block == null) {
			throw new NullPointerException("No blocks to send");
		}
		block.checkIfNotEmpty();
		dest_node.sendBlock(block, isCloseChannelRequest(options));
	}
	
	protected abstract boolean isCloseChannelRequest(T options);
	
}
