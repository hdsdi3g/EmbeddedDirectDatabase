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

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

public final class GOFConfigurationMap implements GOFConfigurationItem {
	
	private LinkedHashMap<String, GOFConfigurationItem> items;
	
	GOFConfigurationMap() {
		items = new LinkedHashMap<>();
	}
	
	public int size() {
		return items.size();
	}
	
	public boolean isEmpty() {
		return items.isEmpty();
	}
	
	public boolean containsKey(String key) {
		return items.containsKey(key);
	}
	
	public GOFConfigurationItem get(String key) {
		return items.get(key);
	}
	
	/**
	 * Never add null items
	 */
	GOFConfigurationItem put(String key, GOFConfigurationItem value) {
		if (key == null) {
			return null;
		}
		if (value == null) {
			return null;
		}
		return items.put(key, value);
	}
	
	/**
	 * Never add null items
	 */
	GOFConfigurationItem put(String key, String value) {
		if (key == null) {
			return null;
		}
		if (value == null) {
			return null;
		}
		return items.put(key, new GOFConfigurationString(value));
	}
	
	GOFConfigurationItem remove(String key) {
		if (key == null) {
			return null;
		}
		return items.remove(key);
	}
	
	/**
	 * Never add null items
	 */
	void putAll(Map<String, GOFConfigurationItem> m) {
		if (m == null) {
			return;
		}
		m.forEach((k, v) -> {
			put(k, v);
		});
	}
	
	/**
	 * Never add null items
	 */
	void removeAll(Map<String, GOFConfigurationItem> m) {
		if (m == null) {
			return;
		}
		m.forEach((k, v) -> {
			remove(k);
		});
	}
	
	void clear() {
		items.clear();
	}
	
	public void forEach(BiConsumer<String, GOFConfigurationItem> consumer) {
		items.forEach(consumer);
	}
	
	public Set<String> keySet() {
		return items.keySet();
	}
	
	public Collection<GOFConfigurationItem> values() {
		return items.values();
	}
	
	public boolean isMap() {
		return true;
	}
	
	public boolean isList() {
		return false;
	}
	
	public boolean isString() {
		return false;
	}
	
	public void deepWalk(ConfigurationWalker walker) {
		items.forEach((key, value) -> {
			walker.onMapEntry(key, value);
		});
	}
	
}
