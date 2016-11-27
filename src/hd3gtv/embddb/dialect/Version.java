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
package hd3gtv.embddb.dialect;

public enum Version {
	
	V1;
	
	/**
	 * @return 1
	 */
	public String toString() {
		return String.valueOf(this.ordinal() + 1);
	};
	
	public static Version resolveFromString(String value) {
		return values()[Integer.parseInt(value) - 1];
	}
	
}
