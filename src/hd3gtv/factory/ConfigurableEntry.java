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

abstract class ConfigurableEntry<T> {
	
	private ConfigurableEntries parent;
	private T value;
	
	ConfigurableEntry(ConfigurableEntries parent) {
		this.parent = parent;
	}
	
	public T get() {
		return value;
	}
	
	public synchronized ConfigurableEntry<T> set(T value) {
		if (this.value == null ^ value == null) {
			// TODO callback change !
			this.value = value;
		} else if (value.equals(value) == false) {
			// TODO callback change !
			this.value = value;
		}
		return this;
	}
	
	// TODO impex
	
}
