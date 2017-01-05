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
package hd3gtv.factorydemo.other;

import java.util.LinkedHashMap;

public class ConfigurableEntryMap<T extends ConfigurableEntry<T>> extends ConfigurableEntry<LinkedHashMap<String, T>> {
	
	ConfigurableEntryMap(ConfigurableEntries parent) {
		super(parent);
	}
	
	// TODO impex
	
}
