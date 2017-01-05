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
 * Copyright (C) hdsdi3g for hd3g.tv 10 déc. 2016
 * 
*/
package hd3gtv.factorydemo.other;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Create object based on current configuration.
 * Can update created objects if the configuration is modified.
 */
public final class _ConfiguredFactory {
	// TODO create personalized factories ex: gson -> gson (+declarations) / simple / pretty
	
	private static Logger log = Logger.getLogger(_ConfiguredFactory.class);
	private static JsonParser parser = new JsonParser();
	
	private HashMap<Class<?>, Object> created_objects_by_classes;
	private ObservedProperties properties;
	private ChangeProperties change_properties;
	
	public _ConfiguredFactory() {
		created_objects_by_classes = new HashMap<>();
		
		change_properties = new ChangeProperties();
		properties = new ObservedProperties(change_properties); // TODO if I modify properties, it must modify created objects
		properties.setProperty("queue.execcount", "2");// TODO remove...
	}
	
	/**
	 * @param object_class only public class, with a public constructor with parameters annoted with ConfFactKey, or no constructor.
	 */
	public synchronized <T> T create(Class<T> object_class, boolean reuse_previousely_created_classes) throws ReflectiveOperationException {
		if (reuse_previousely_created_classes == false) {
			return createAndInstanciate(object_class);
		}
		
		if (created_objects_by_classes.containsKey(object_class) == false) {
			T object = createAndInstanciate(object_class);
			if (object != null) {
				created_objects_by_classes.put(object_class, object);
			}
		}
		
		@SuppressWarnings("unchecked")
		T object = (T) created_objects_by_classes.get(object_class);
		return object;
	}
	
	private <T> T createAndInstanciate(Class<T> object_class) throws ReflectiveOperationException {
		if (object_class == null) {
			log.warn("Want to create object from null class !");
			return null;
		} else if (object_class.isInterface()) {
			log.warn("Want to create object from an Interface: " + object_class.getName());
			return null;
		} else if (object_class.isPrimitive()) {
			log.warn("Want to create object from a primitive type: " + object_class.getName());
			return null;
		} else if (object_class.isArray()) {
			log.warn("Want to create object from a array type: " + object_class.getName());
			return null;
		} else if (object_class.isEnum()) {
			log.warn("Want to create object from an Enum: " + object_class.getName());
			return null;
		} else if (Modifier.isAbstract(object_class.getModifiers())) {
			log.warn("Want to create object from a abstract class: " + object_class.getName());
			return null;
		} else if (Modifier.isNative(object_class.getModifiers())) {
			log.warn("Want to create object from a native class: " + object_class.getName());
			return null;
		} else if (Modifier.isStatic(object_class.getModifiers())) {
			log.warn("Want to create object from a static class: " + object_class.getName());
			return null;
		} else if (Modifier.isPublic(object_class.getModifiers()) == false) {
			log.warn("Want to create object from a non-public class: " + object_class.getName());
			return null;
		}
		
		List<Constructor<?>> constructors = Arrays.asList(object_class.getDeclaredConstructors());
		
		List<Constructor<?>> potential_valid_constructors = constructors.stream().filter(constructor -> {
			if (constructor.isVarArgs()) {
				return false;
			} else if (Modifier.isPublic(constructor.getModifiers()) == false) {
				return false;
			}
			return true;
		}).collect(Collectors.toList());
		
		if (potential_valid_constructors.isEmpty()) {
			log.warn("Can't found valid constructor for this class: " + object_class.getName());
			return null;
		}
		
		Constructor<?> constructor = potential_valid_constructors.get(0);
		String configuration_key = null;
		
		Optional<Constructor<?>> constructor_with_conf_key = potential_valid_constructors.stream().filter(c -> {
			return c.getAnnotation(ConfFactKey.class) != null;
		}).findFirst();
		
		if (constructor_with_conf_key.isPresent()) {
			constructor = constructor_with_conf_key.get();
			ConfFactKey conf_key = constructor.getAnnotation(ConfFactKey.class);
			if (conf_key.value().equals("") == false) {
				configuration_key = conf_key.value();
			}
		}
		
		List<Parameter> constructor_parameters = Arrays.asList(constructor.getParameters());
		if (constructor_parameters.isEmpty()) {
			@SuppressWarnings("unchecked")
			T result = (T) constructor.newInstance();
			return result;
		}
		
		Class<?> parameter_class;
		Object[] constructor_parameters_values = new Object[constructor_parameters.size()];
		for (int pos = 0; pos < constructor_parameters.size(); pos++) {
			Parameter constructor_parameter = constructor_parameters.get(pos);
			parameter_class = constructor_parameter.getType();
			
			ConfFactParam param_key_conf = constructor_parameter.getAnnotation(ConfFactParam.class);
			if (param_key_conf != null) {
				String key = configuration_key + "." + param_key_conf.value();
				String value = properties.getProperty(key, null);
				if (value == null) {
					constructor_parameters_values[pos] = null; // TODO get default
					continue;
				}
				
				if (log.isTraceEnabled()) {
					log.trace("Get configured value for [" + object_class.getName() + "] " + key + ": \"" + value + "\" -> [" + parameter_class + "]");
				}
				
				if (value.equalsIgnoreCase("null")) { // TODO test me
					constructor_parameters_values[pos] = null;
				} else if (parameter_class.isEnum()) { // TODO test me
					@SuppressWarnings({ "unchecked", "rawtypes" })
					Object o = Enum.valueOf((Class<? extends Enum>) parameter_class, value);
					constructor_parameters_values[pos] = o;
				} else if (Boolean.class.isAssignableFrom(parameter_class) | boolean.class.isAssignableFrom(parameter_class)) {
					constructor_parameters_values[pos] = Boolean.valueOf(value);
				} else if (Character.class.isAssignableFrom(parameter_class) | char.class.isAssignableFrom(parameter_class)) {
					if (value.length() > 0) {
						constructor_parameters_values[pos] = value.charAt(0);
					} else {
						constructor_parameters_values[pos] = null;
					}
				} else if (Byte.class.isAssignableFrom(parameter_class) | byte.class.isAssignableFrom(parameter_class)) {
					constructor_parameters_values[pos] = Byte.valueOf(value);
				} else if (Short.class.isAssignableFrom(parameter_class) | short.class.isAssignableFrom(parameter_class)) {
					constructor_parameters_values[pos] = Short.valueOf(value);
				} else if (Integer.class.isAssignableFrom(parameter_class) | int.class.isAssignableFrom(parameter_class)) {
					constructor_parameters_values[pos] = Integer.valueOf(value);
				} else if (Long.class.isAssignableFrom(parameter_class) | long.class.isAssignableFrom(parameter_class)) {
					constructor_parameters_values[pos] = Long.valueOf(value);
				} else if (Float.class.isAssignableFrom(parameter_class) | float.class.isAssignableFrom(parameter_class)) {
					constructor_parameters_values[pos] = Float.valueOf(value);
				} else if (Double.class.isAssignableFrom(parameter_class) | double.class.isAssignableFrom(parameter_class)) {
					constructor_parameters_values[pos] = Double.valueOf(value);
				} else if (String.class.isAssignableFrom(parameter_class)) { // TODO test me
					constructor_parameters_values[pos] = value;
				} else if (URL.class.isAssignableFrom(parameter_class)) { // TODO test me
					try {
						constructor_parameters_values[pos] = new URL(value);
					} catch (Exception e) {
						log.error("Invalid configured URL " + key + ": " + value, e);
					}
				} else if (InetAddress.class.isAssignableFrom(parameter_class)) { // TODO test me
					try {
						constructor_parameters_values[pos] = InetAddress.getByName(value);
					} catch (Exception e) {
						log.error("Invalid configured InetAddress " + key + ": " + value, e);
					}
				} else if (InetSocketAddress.class.isAssignableFrom(parameter_class)) { // TODO test me
					try {
						String[] so = value.split(":");
						constructor_parameters_values[pos] = InetSocketAddress.createUnresolved(so[0], Integer.parseInt(so[1]));
					} catch (Exception e) {
						log.error("Invalid configured InetSocketAddress " + key + ": " + value, e);
					}
				} else if (JsonArray.class.isAssignableFrom(parameter_class)) { // TODO test me
					try {
						constructor_parameters_values[pos] = parser.parse(value).getAsJsonArray();
					} catch (Exception e) {
						log.error("Invalid configured JsonArray " + key + ": " + value, e);
					}
				} else if (JsonObject.class.isAssignableFrom(parameter_class)) { // TODO test me
					try {
						constructor_parameters_values[pos] = parser.parse(value).getAsJsonObject();
					} catch (Exception e) {
						log.error("Invalid configured JsonObject " + key + ": " + value, e);
					}
				} else {
					Class<?> to_create = null;
					try {
						to_create = Class.forName(value);
					} catch (Exception e) {
					}
					if (to_create != null) {
						if (parameter_class.isAssignableFrom(to_create)) { // TODO test me
							constructor_parameters_values[pos] = create(to_create, true);
							continue;
						}
					}
					log.error("Invalid parameter_class: " + parameter_class + " for " + object_class);
				}
			} else {
				constructor_parameters_values[pos] = create(constructor_parameter.getType(), true);
			}
		}
		
		@SuppressWarnings("unchecked")
		T result = (T) constructor.newInstance(constructor_parameters_values);
		return result;
	}
	
	private class ChangeProperties implements OnChangeProperties {
		
		@Override
		public void didUpdate(String key, String value) {
			// TODO Auto-generated method stub
			
		}
		
		@Override
		public void didUpdate(HashMap<String, String> values) {
			// TODO Auto-generated method stub
			OnChangeProperties.super.didUpdate(values);
		}
	}
}
