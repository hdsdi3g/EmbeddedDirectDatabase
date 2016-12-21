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
 * Copyright (C) hdsdi3g for hd3g.tv 11 déc. 2016
 * 
*/
package hd3gtv.factory.other;

import java.util.Collections;
import java.util.Map;
import java.util.Properties;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class ObservedProperties {
	
	private Properties props;
	private OnChangeProperties observer;
	
	public ObservedProperties(Properties initial_values, OnChangeProperties observer) {
		props = new Properties(initial_values);
		this.observer = observer;
	}
	
	public ObservedProperties(OnChangeProperties observer) {
		this(null, observer);
	}
	
	public ObservedProperties(Properties initial_values) {
		this(initial_values, null);
	}
	
	public ObservedProperties() {
		this(null, null);
	}
	
	public void dumpContentToLog(Logger log, Level level) {
		log.log(level, "Dump configuration: " + toString());
	}
	
	public String toString() {
		StringBuilder sb = new StringBuilder();
		props.forEach((k, v) -> {
			sb.append(k);
			sb.append(": \"");
			sb.append(v);
			sb.append("\" ");
		});
		return sb.toString().trim();
	}
	
	public String getProperty(String key) {
		return props.getProperty(key);
	}
	
	public String getProperty(String key, String defaultValue) {
		return props.getProperty(key, defaultValue);
	}
	
	public Object setProperty(String key, String value) {
		Object result = props.setProperty(key, value);
		if (observer != null) {
			observer.didUpdate(key, value);
		}
		return result;
	}
	
	public boolean containsKey(String key) {
		return props.containsKey(key);
	}
	
	public boolean isEmpty() {
		return props.isEmpty();
	}
	
	public int size() {
		return props.size();
	}
	
	/**
	 * @return UnmodifiableMap
	 */
	public Map<Object, Object> keySet() {
		return Collections.unmodifiableMap(props);
	}
	
}
