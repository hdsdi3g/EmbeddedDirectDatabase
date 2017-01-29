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

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Consumer;
import java.util.stream.Stream;

public final class GOFConfigurationList implements GOFConfigurationItem {
	
	private ArrayList<GOFConfigurationItem> items;
	
	GOFConfigurationList() {
		items = new ArrayList<>();
	}
	
	public int size() {
		return items.size();
	}
	
	public boolean isEmpty() {
		return items.isEmpty();
	}
	
	public boolean contains(GOFConfigurationItem o) {
		return items.contains(o);
	}
	
	/**
	 * Never add null items
	 */
	boolean add(GOFConfigurationItem e) {
		if (e == null) {
			return false;
		}
		return items.add(e);
	}
	
	/**
	 * Never add null items
	 */
	boolean add(String value) {
		if (value == null) {
			return false;
		}
		return items.add(new GOFConfigurationString(value));
	}
	
	boolean remove(GOFConfigurationItem o) {
		if (o == null) {
			return false;
		}
		return items.remove(o);
	}
	
	/**
	 * Never add null items
	 */
	void addAll(Collection<GOFConfigurationItem> c) {
		if (c == null) {
			return;
		}
		items.ensureCapacity(items.size() + c.size());
		c.forEach(item -> {
			add(item);
		});
	}
	
	void removeAll(Collection<GOFConfigurationItem> c) {
		c.forEach(item -> {
			remove(item);
		});
	}
	
	void clear() {
		items.clear();
	}
	
	public Stream<GOFConfigurationItem> get() {
		return items.stream();
	}
	
	public void forEach(Consumer<GOFConfigurationItem> consumer) {
		items.forEach(consumer);
	}
	
	public boolean isMap() {
		return false;
	}
	
	public boolean isList() {
		return true;
	}
	
	public boolean isString() {
		return false;
	}
	
	public void deepWalk(ConfigurationWalker walker) {
		items.forEach(item -> {
			walker.onListEntry(item);
		});
	}
	
}
