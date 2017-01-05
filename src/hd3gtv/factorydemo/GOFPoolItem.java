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
 * Copyright (C) hdsdi3g for hd3g.tv 21 déc. 2016
 * 
*/
package hd3gtv.factorydemo;

/**
 * A created Object by configuration.
 */
class GOFPoolItem<T> {
	
	private GlobalObjectFactory gof;
	private Class<T> origin_class;
	private T object;
	private GOFItemDefinition definition;
	
	GOFPoolItem(GlobalObjectFactory gof, Class<T> origin_class) throws IllegalAccessException {
		this.gof = gof;
		if (gof == null) {
			throw new NullPointerException("\"gof\" can't to be null");
		}
		this.origin_class = origin_class;
		if (origin_class == null) {
			throw new NullPointerException("\"origin_class\" can't to be null");
		}
		definition = gof.getDefinition(origin_class);
	}
	
	T getCreatedObject() {
		return object;
	}
	
	public Class<T> getOriginClass() {
		return origin_class;
	}
	
	boolean isAnInstanceOf(Class<?> comparable_class) {
		return origin_class.isAssignableFrom(comparable_class);
	}
	
	GOFPoolItem<T> makeObject() throws InstantiationException, IllegalAccessException {
		object = origin_class.newInstance();// XXX with definition
		return this;
	}
	
}
