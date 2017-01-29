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

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.apache.log4j.Logger;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

public final class GOFConfigurationList implements GOFConfigurationItem {
	
	private static Logger log = Logger.getLogger(GOFConfigurationList.class);
	
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
		for (int pos = 0; pos < items.size(); pos++) {
			walker.onListEntry(pos, items.get(pos));
		}
	}
	
	public GOFConfigurationItem getPath(String path) {
		if (path == null) {
			throw new NullPointerException("\"path\" can't to be null");
		}
		path = path.replace("/", ".");
		if (path.startsWith(".")) {
			path = path.substring(1);
		}
		if (path.endsWith(".")) {
			path = path.substring(0, path.length() - 1);
		}
		
		int pos = path.indexOf(".");
		
		GOFConfigurationItem sub_item = null;
		try {
			if (pos == -1) {
				sub_item = items.get(Integer.parseInt(path));
			} else {
				sub_item = items.get(Integer.parseInt(path.substring(0, pos)));
			}
		} catch (NumberFormatException e) {
			log.warn("Can't get index value (" + e.getMessage() + ") in " + path);
			return null;
		} catch (IndexOutOfBoundsException e) {
			log.warn("Can't get index position in list entry (" + e.getMessage() + ") in " + path);
			return null;
		}
		
		if (sub_item == null) {
			throw new NullPointerException("\"sub_item\" can't to be null");
		} else if (sub_item.isMap()) {
			return sub_item.getAsMap().getPath(path.substring(pos + 1, path.length()));
		} else if (sub_item.isList()) {
			return sub_item.getAsList().getPath(path.substring(pos + 1, path.length()));
		} else if (sub_item.isString()) {
			return sub_item;
		}
		
		return null;
	}
	
	public String toString() {
		return items.toString();
	}
	
	public int hashCode() {
		return items.hashCode();
	}
	
	static class ListSerializer implements JsonSerializer<GOFConfigurationList>, JsonDeserializer<GOFConfigurationList> {
		
		private GOFConfiguration conf;
		
		ListSerializer(GOFConfiguration conf) {
			this.conf = conf;
		}
		
		public GOFConfigurationList deserialize(JsonElement arg0, Type arg1, JsonDeserializationContext arg2) throws JsonParseException {
			JsonArray ja = arg0.getAsJsonArray();
			
			GOFConfigurationList result = new GOFConfigurationList();
			if (ja.size() == 0) {
				return result;
			}
			result.items.ensureCapacity(ja.size());
			
			for (int pos = 0; pos < ja.size(); pos++) {
				JsonElement item = ja.get(pos);
				if (item.isJsonArray()) {
					result.add(conf.getGson().fromJson(item, GOFConfigurationList.class));
				} else if (item.isJsonObject()) {
					result.add(conf.getGson().fromJson(item, GOFConfigurationMap.class));
				} else {
					result.add(new GOFConfigurationString(item.getAsString()));
				}
			}
			
			return result;
		}
		
		public JsonElement serialize(GOFConfigurationList arg0, Type arg1, JsonSerializationContext arg2) {
			JsonArray ja = new JsonArray();
			arg0.items.forEach(item -> {
				ja.add(conf.getGson().toJsonTree(item));
			});
			return ja;
		}
		
	}
	
}
