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

public final class GOFConfigurationString implements GOFConfigurationItem {
	
	private String value;
	
	GOFConfigurationString() {
		value = null;
	}
	
	GOFConfigurationString(String value) {
		this.value = value;
	}
	
	public String getValue() {
		return value;
	}
	
	void setValue(String value) {
		this.value = value;
	}
	
	public boolean isMap() {
		return false;
	}
	
	public boolean isList() {
		return false;
	}
	
	public boolean isString() {
		return true;
	}
	
	public void deepWalk(ConfigurationWalker walker) {
		walker.onStringEntry(this);
	}
	
}
