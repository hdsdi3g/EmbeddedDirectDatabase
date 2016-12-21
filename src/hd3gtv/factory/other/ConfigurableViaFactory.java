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
 * Copyright (C) hdsdi3g for hd3g.tv 17 déc. 2016
 * 
*/
package hd3gtv.factory.other;

import java.util.concurrent.Callable;

public interface ConfigurableViaFactory<T extends ConfigurableParam> {
	
	/**
	 * Factory startup this object with clean configuration.
	 */
	public void initializationViaFactory(_ConfiguredFactory factory, T value);
	
	/**
	 * Factory want to destroy this object.
	 */
	public void terminateViaFactory();
	
	/**
	 * Factory want to change the previously sended object.
	 */
	public void updateViaFactory(T value);
	
	/**
	 * This object has updated its internal configuration, and want to update to rest of app.
	 */
	public void internalUpdate(Callable<T> value);
	
	/**
	 * @return a new and empty configuration. Used for the case if the configuration is empty.
	 */
	public T defaultConfiguration();
	
}
