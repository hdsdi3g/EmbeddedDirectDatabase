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
import java.util.Map;

import hd3gtv.factorydemo.annotations.ParameterGOF;
import hd3gtv.factorydemo.annotations.ParameterListGOF;
import hd3gtv.factorydemo.annotations.ParameterMapGOF;

/**
 * Class parameter definition, import @ParameterGOF, @ParameterListGOF and @ParameterMapGOF setup values.
 */
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
	private Class<?> generic_type;
	
	private GOFItemDefinitionParameter(Parameter parameter, int pos, String source) throws IllegalAccessException {
		if (parameter == null) {
			throw new NullPointerException("\"parameter\" can't to be null");
		}
		type = parameter.getType();
		
		ParameterGOF a_name = parameter.getAnnotation(ParameterGOF.class);
		if (a_name == null) {
			name = String.valueOf(pos);
			default_value = "";
		} else {
			name = a_name.value();
			default_value = a_name.default_value();
		}
		
		if (default_value != null) {
			if (default_value.equals("")) {
				default_value = null;
			}
		}
		
		ParameterListGOF a_listof = parameter.getAnnotation(ParameterListGOF.class);
		if (Collection.class.isAssignableFrom(type)) {
			if (a_listof == null) {
				throw new IllegalAccessException("An annotation @ParameterListGOF is missing for param " + name + " [" + type.getName() + "] on " + source);
			}
			name = a_listof.value();
			generic_type = a_listof.generic_type();
		} else if (a_listof != null) {
			throw new IllegalAccessException("An annotation @ParameterListGOF is set for param " + name + " [" + type.getName() + "] on " + source + " but it's not a Collection");
		}
		
		ParameterMapGOF a_mapof = parameter.getAnnotation(ParameterMapGOF.class);
		if (Map.class.isAssignableFrom(type)) {
			if (a_mapof == null) {
				throw new IllegalAccessException("An annotation @ParameterMapGOF is missing for param " + name + " [" + type.getName() + "] on " + source);
			}
			name = a_mapof.value();
			generic_type = a_mapof.generic_type();
		} else if (a_mapof != null) {
			throw new IllegalAccessException("An annotation @ParameterMapGOF is set for param " + name + " [" + type.getName() + "] on " + source + " but it's not a Map");
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
	public Class<?> getGeneric_type() {
		return generic_type;
	}
	
	/**
	 * @return maybe null
	 */
	public String getDefaultValue() {
		return default_value;
	}
	
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(type.getSimpleName());
		if (generic_type != null) {
			sb.append("<");
			sb.append(generic_type.getSimpleName());
			sb.append(">");
		}
		
		if (name != null) {
			sb.append(" ");
			sb.append(name);
		}
		
		if (default_value != null) {
			sb.append(" = ");
			sb.append(default_value);
		}
		
		return sb.toString();
	}
	
}
