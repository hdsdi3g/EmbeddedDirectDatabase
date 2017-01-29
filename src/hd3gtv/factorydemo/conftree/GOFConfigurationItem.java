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
 * Copyright (C) hdsdi3g for hd3g.tv 29 janv. 2017
 * 
*/
package hd3gtv.factorydemo.conftree;

public interface GOFConfigurationItem {
	
	public default GOFConfigurationMap getAsMap() {
		if (isMap() == false) {
			throw new ClassCastException("Item is not a Map: " + getClass());
		}
		return (GOFConfigurationMap) this;
	}
	
	public default GOFConfigurationList getAsList() {
		if (isList() == false) {
			throw new ClassCastException("Item is not a List: " + getClass());
		}
		return (GOFConfigurationList) this;
	}
	
	public default GOFConfigurationString getAsString() {
		if (isString() == false) {
			throw new ClassCastException("Item is not a String: " + getClass());
		}
		return (GOFConfigurationString) this;
	}
	
	public boolean isMap();
	
	public boolean isList();
	
	public boolean isString();
	
	public void deepWalk(ConfigurationWalker walker);
	
}
