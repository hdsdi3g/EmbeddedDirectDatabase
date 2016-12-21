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
package hd3gtv.factory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import hd3gtv.factory.annotations.PreferedConstructorGOF;

/**
 * An analyst of class parameters.
 */
class GOFItemDefinition {
	
	private Class<?> origin_class;
	
	private Category category;
	
	enum Category {
		Interface, Primitive, Array, Enum, Abstract, Native, Static, NonPublic, Public;
		
		static Category getCategory(Class<?> object_class) {
			if (object_class.isInterface()) {
				return Interface;
			} else if (object_class.isPrimitive()) {
				return Primitive;
			} else if (object_class.isArray()) {
				return Array;
			} else if (object_class.isEnum()) {
				return Enum;
			} else if (Modifier.isAbstract(object_class.getModifiers())) {
				return Abstract;
			} else if (Modifier.isNative(object_class.getModifiers())) {
				return Native;
			} else if (Modifier.isStatic(object_class.getModifiers())) {
				return Static;
			} else if (Modifier.isPublic(object_class.getModifiers()) == false) {
				return NonPublic;
			}
			return Public;
		}
	}
	
	private Constructor<?> constructor;
	private ArrayList<GOFItemDefinitionParameter> constructor_parameters;
	
	GOFItemDefinition(Class<?> origin_class) throws IllegalAccessException {
		this.origin_class = origin_class;
		if (origin_class == null) {
			throw new NullPointerException("\"origin_class\" can't to be null");
		}
		category = Category.getCategory(origin_class);
		if (category != Category.Public) {
			throw new IllegalAccessException("Can't use class " + origin_class + " because it's " + category);
		}
		
		/**
		 * Get constructor without params
		 */
		List<Constructor<?>> potential_constructors = getPotentialConstructors();
		
		Optional<Constructor<?>> opt_constructor = potential_constructors.stream().filter(c -> {
			return c.getParameterCount() == 0;
		}).findFirst();
		if (opt_constructor.isPresent()) {
			constructor = opt_constructor.get();
			constructor_parameters = new ArrayList<>();
		} else {
			/**
			 * Get constructor with params
			 */
			opt_constructor = potential_constructors.stream().filter(c -> {
				/**
				 * Get the first with @PreferedConstructorGOF
				 */
				return c.getAnnotation(PreferedConstructorGOF.class) != null;
			}).findFirst();
			if (opt_constructor.isPresent()) {
				constructor = opt_constructor.get();
			} else {
				/**
				 * Get the first
				 */
				constructor = potential_constructors.get(0);
			}
			
			constructor_parameters = GOFItemDefinitionParameter.importFromList(constructor.getParameters(), origin_class.getName() + "() first valid constructor");
		}
		
		// TODO get init actions (with params)
		// TODO get terminate actions (with params)
		// TODO get update actions (with params)
		
		// TODO get root conf key
		// TODO get default value(s)
	}
	
	private List<Constructor<?>> getPotentialConstructors() throws IllegalAccessException {
		List<Constructor<?>> constructors = Arrays.asList(origin_class.getDeclaredConstructors());
		
		List<Constructor<?>> potential_valid_constructors = constructors.stream().filter(constructor -> {
			if (constructor.isVarArgs()) {
				return false;
			} else if (Modifier.isPublic(constructor.getModifiers()) == false) {
				return false;
			}
			return true;
		}).collect(Collectors.toList());
		
		if (potential_valid_constructors.isEmpty()) {
			throw new IllegalAccessException("Can't found valid constructor for this class: " + origin_class.getName());
		}
		
		return potential_valid_constructors;
	}
	
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("origin_class: ");
		sb.append(origin_class.getName());
		
		if (constructor_parameters.isEmpty() == false) {
			sb.append(", constructor_parameters: ");
			sb.append(constructor_parameters.stream().map(p -> {
				return p.toString();
			}).collect(() -> {
				return new StringBuilder();
			}, (sbc, p) -> {
				sbc.append("(");
				sbc.append(p);
				sbc.append(")");
			}, (sbc, sbc2) -> {
				sbc.append(sbc2.toString());
			}).toString());
		} else {
			sb.append(" with default and empty constructor");
		}
		return sb.toString();
	}
	
}
