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
package hd3gtv.factory;

import java.util.LinkedHashMap;

public final class ConfigurableEntries {
	
	private LinkedHashMap<String, ConfigurableEntry<?>> items;
	
	public ConfigurableEntries() {
		items = new LinkedHashMap<>(1);
	}
	
	public synchronized <T> void add(String name, ConfigurableEntry<T> value) {
		// TODO callback
		items.put(name, value);
	}
	
	/*public void add(String name, Boolean value) {
		add(name, new ConfigurableEntryBoolean(this).set(value));
	}
	
	public void add(String name, InetAddress value) {
		add(name, new ConfigurableEntryInetAddr(this).set(value));
	}
	
	public void add(String name, InetSocketAddress value) {
		add(name, new ConfigurableEntryInetSocketAddr(this).set(value));
	}
	
	public void add(String name, String value) {
		add(name, new ConfigurableEntryString(this).set(value));
	}
	
	public void add(String name, ConfigurableEntries values) {
		add(name, new ConfigurableEntryTree(this).set(values));
	}
	
	public void add(String name, URL value) {
		add(name, new ConfigurableEntryURL(this).set(value));
	}*/
	
	public ConfigurableEntry<?> get(String name) {
		return items.get(name);
	}
	
	public ConfigurableEntry<?> get(String name, ConfigurableEntry<?> default_value) {
		return items.getOrDefault(name, default_value);
	}
	
	public boolean has(String name) {
		return items.containsKey(name);
	}
	
	public int size() {
		return items.size();
	}
	
	public boolean isEmpty() {
		return items.isEmpty();
	}
	
	// TODO impex conf: raw String / list raw String / map String->raw String / list sub items / map String->Sub items
	// TODO import default
	
}
