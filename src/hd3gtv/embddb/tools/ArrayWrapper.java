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
 * Copyright (C) hdsdi3g for hd3g.tv 24 nov. 2016
 * 
*/
package hd3gtv.embddb.tools;

import java.util.ArrayList;

public class ArrayWrapper {
	
	private ArrayWrapper() {
	}
	
	/**
	 * @param items skip null entries
	 * @return null if items == null.
	 */
	@SafeVarargs
	public static <T> ArrayList<T> asArrayList(T... items) {
		if (items == null) {
			return null;
		}
		if (items.length == 0) {
			return new ArrayList<T>(1);
		}
		
		ArrayList<T> result = new ArrayList<>(items.length);
		for (int pos = 0; pos < items.length; pos++) {
			if (items[pos] == null) {
				continue;
			}
			result.add(items[pos]);
		}
		
		return result;
	}
	
}
