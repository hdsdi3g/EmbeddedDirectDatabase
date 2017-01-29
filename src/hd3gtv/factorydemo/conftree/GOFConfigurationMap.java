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
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

import org.apache.log4j.Logger;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

public final class GOFConfigurationMap implements GOFConfigurationItem {
	
	private LinkedHashMap<String, GOFConfigurationItem> items;
	
	private static Logger log = Logger.getLogger(GOFConfigurationMap.class);
	
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
	
	/**
	 * @param path like "val1.val2.val3" or "val1/val2/val3", "/v1/v2", "v1/v2/"
	 * @return may be null
	 */
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
		if (pos == -1) {
			return get(path);
		}
		
		GOFConfigurationItem sub_item = get(path.substring(0, pos));
		
		if (sub_item == null) {
			return null;
		} else if (sub_item.isMap()) {
			return sub_item.getAsMap().getPath(path.substring(pos + 1, path.length()));
		} else if (sub_item.isList()) {
			return sub_item.getAsList().getPath(path.substring(pos + 1, path.length()));
		} else if (sub_item.isString()) {
			log.warn("Wan't access to a sub element inside a simple string: " + path);
		}
		
		return null;
	}
	
	public String toString() {
		return items.toString();
	}
	
	public int hashCode() {
		return items.hashCode();
	}
	
	static class MapSerializer implements JsonSerializer<GOFConfigurationMap>, JsonDeserializer<GOFConfigurationMap> {
		
		private GOFConfiguration conf;
		
		MapSerializer(GOFConfiguration conf) {
			this.conf = conf;
		}
		
		public GOFConfigurationMap deserialize(JsonElement arg0, Type arg1, JsonDeserializationContext arg2) throws JsonParseException {
			JsonObject jo = arg0.getAsJsonObject();
			
			GOFConfigurationMap result = new GOFConfigurationMap();
			for (Map.Entry<String, JsonElement> entry : jo.entrySet()) {
				String key = entry.getKey();
				JsonElement item = entry.getValue();
				if (item.isJsonArray()) {
					result.put(key, conf.getGson().fromJson(item, GOFConfigurationList.class));
				} else if (item.isJsonObject()) {
					result.put(key, conf.getGson().fromJson(item, GOFConfigurationMap.class));
				} else {
					result.put(key, new GOFConfigurationString(item.getAsString()));
				}
			}
			
			return result;
		}
		
		public JsonElement serialize(GOFConfigurationMap arg0, Type arg1, JsonSerializationContext arg2) {
			JsonObject jo = new JsonObject();
			arg0.items.forEach((k, v) -> {
				jo.add(k, conf.getGson().toJsonTree(v));
			});
			return jo;
			
		}
		
	}
	
}
