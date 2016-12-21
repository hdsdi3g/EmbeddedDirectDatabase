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
 * Copyright (C) hdsdi3g for hd3g.tv 20 déc. 2016
 * 
*/
package hd3gtv.factory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;

import org.apache.log4j.Logger;

public class GlobalObjectFactory {
	
	private static Logger log = Logger.getLogger(GlobalObjectFactory.class);
	
	private ArrayList<GOFPoolItem<?>> created_objects;
	private HashMap<Class<?>, GOFItemDefinition> definitions;
	
	public GlobalObjectFactory() {
		created_objects = new ArrayList<>();
		definitions = new HashMap<>();
	}
	
	/**
	 * @param class_ref must pointed to a real object (no interface, native, enum...).
	 */
	public <T> T create(Class<T> class_ref) {
		try {
			GOFPoolItem<T> pool_item = new GOFPoolItem<>(this, class_ref);
			
			synchronized (created_objects) {
				T result = pool_item.makeObject().getCreatedObject();
				created_objects.add(pool_item);
				return result;
			}
		} catch (Exception e) {
			log.error("Can't create Object from " + class_ref, e);
			return null;
		}
	}
	
	/**
	 * Or create it if this is not created.
	 */
	public <T> T getCreatedInstance(Class<T> class_ref) {
		Optional<GOFPoolItem<?>> opt_candidate = created_objects.stream().filter(pool_item -> {
			return pool_item.isAnInstanceOf(class_ref);
		}).findFirst();
		
		if (opt_candidate.isPresent() == false) {
			return create(class_ref);
		}
		
		@SuppressWarnings("unchecked")
		T object = (T) opt_candidate.get().getCreatedObject();
		
		return object;
	}
	
	GOFItemDefinition getDefinition(Class<?> ref) throws IllegalAccessException {
		if (definitions.containsKey(ref)) {
			return definitions.get(ref);
		}
		
		GOFItemDefinition def = new GOFItemDefinition(ref);
		synchronized (definitions) {
			definitions.put(ref, def);
		}
		return def;
	}
	
	public String toString() {
		final StringBuilder sb = new StringBuilder();
		
		sb.append("actual definitions [");
		definitions.values().forEach(def -> {
			sb.append(" ");
			sb.append(def.toString());
		});
		sb.append(" ]");
		
		return sb.toString();
	}
	
	// TODO import internal object default conf
	// TODO import external conf
	// TODO API for manipulate internal conf via external calls
	// TODO on change, export external conf, and update objects
	// TODO base namespace (debug/prod)
	
}
