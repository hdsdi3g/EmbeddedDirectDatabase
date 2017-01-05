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

import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Collection;

import hd3gtv.factorydemo.annotations.DefaultGOF;
import hd3gtv.factorydemo.annotations.ListOfGOF;
import hd3gtv.factorydemo.annotations.NameGOF;

class GOFItemDefinitionParameter {
	
	static ArrayList<GOFItemDefinitionParameter> importFromList(Parameter[] raw_list, String source) throws IllegalAccessException {
		final ArrayList<GOFItemDefinitionParameter> result = new ArrayList<>();
		
		for (int pos = 0; pos < raw_list.length; pos++) {
			result.add(new GOFItemDefinitionParameter(raw_list[pos], pos, source));
		}
		
		return result;
	}
	
	private Class<?> type;
	private String name;
	private String default_value;
	private Class<?> list_type;
	
	private GOFItemDefinitionParameter(Parameter parameter, int pos, String source) throws IllegalAccessException {
		if (parameter == null) {
			throw new NullPointerException("\"parameter\" can't to be null");
		}
		type = parameter.getType();
		
		NameGOF a_name = parameter.getAnnotation(NameGOF.class);
		if (a_name == null) {
			name = String.valueOf(pos);
		} else {
			name = a_name.value();
		}
		
		DefaultGOF a_default = parameter.getAnnotation(DefaultGOF.class);
		if (a_default != null) {
			default_value = a_default.value();
		}
		
		ListOfGOF a_listof = parameter.getAnnotation(ListOfGOF.class);
		if (Collection.class.isAssignableFrom(type)) {
			if (a_listof == null) {
				throw new IllegalAccessException("An annotation @ListOfGOF is missing for param " + name + " [" + type.getName() + "] on " + source);
			}
			list_type = a_listof.value();
		} else if (a_listof != null) {
			throw new IllegalAccessException("An annotation @ListOfGOF is set for param " + name + " [" + type.getName() + "] on " + source + " but it's not a List");
		}
		
	}
	
	public Class<?> getType() {
		return type;
	}
	
	/**
	 * @return maybe null
	 */
	public String getName() {
		return name;
	}
	
	/**
	 * @return maybe null
	 */
	public Class<?> getListType() {
		return list_type;
	}
	
	/**
	 * @return maybe null
	 */
	public String getDefaultValue() {
		return default_value;
	}
	
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("type: ");
		sb.append(type.getName());
		
		if (name != null) {
			sb.append(", name: ");
			sb.append(name);
		}
		
		if (list_type != null) {
			sb.append(", list_type: ");
			sb.append(list_type.getName());
		}
		
		if (default_value != null) {
			sb.append(", default_value: ");
			sb.append(default_value);
		}
		
		return sb.toString();
	}
	
}
