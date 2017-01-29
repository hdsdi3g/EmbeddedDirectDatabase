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
 * Copyright (C) hdsdi3g for hd3g.tv 29 janv. 2017
 * 
*/
package hd3gtv.factorydemo.conftree;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Properties;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public final class GOFConfiguration {
	
	private static Logger log = Logger.getLogger(GOFConfiguration.class);
	
	private GOFConfigurationMap root;
	private final Gson gson;
	// private final Gson gson_simple;
	
	public GOFConfiguration() {
		root = new GOFConfigurationMap();
		
		GsonBuilder builder = new GsonBuilder();
		builder.setPrettyPrinting();
		// gson_simple = builder.create();
		
		builder.registerTypeAdapter(GOFConfigurationMap.class, new GOFConfigurationMap.MapSerializer(this));
		builder.registerTypeAdapter(GOFConfigurationList.class, new GOFConfigurationList.ListSerializer(this));
		builder.registerTypeAdapter(GOFConfigurationString.class, new GOFConfigurationString.StringSerializer());
		gson = builder.create();
		
		// TODO import from file config
		// TODO create onChange callback caller
	}
	
	Gson getGson() {
		return gson;
	}
	
	public void getConfigurationInYAML(PrintStream print) {
		EntriesWalkerYAML dw = new EntriesWalkerYAML(print);
		root.deepWalk(dw);
	}
	
	public void createDummyValues() {
		root.put("test1", "aaa");
		root.put("test2", "bbb");
		
		GOFConfigurationMap m2 = new GOFConfigurationMap();
		m2.put("test4-1", "i");
		m2.put("test4-2", "j");
		m2.put("test4-3", "k");
		
		GOFConfigurationMap m1 = new GOFConfigurationMap();
		m1.put("test3-1", "fff");
		m1.put("test3-2", "g");
		m1.put("test3-3", m2);
		
		GOFConfigurationList l2 = new GOFConfigurationList();
		l2.add("hhhh");
		l2.add("iii");
		l2.add(m2);
		
		GOFConfigurationList l1 = new GOFConfigurationList();
		l1.add("ccc");
		l1.add("ddd");
		l1.add(m1);
		l1.add(l2);
		l1.add("eee");
		root.put("test3", l1);
	}
	
	public Properties getConfigurationInProperties() {
		EntriesWalkerProperties dw = new EntriesWalkerProperties();
		root.deepWalk(dw);
		return dw.p;
	}
	
	/**
	 * @return in PrettyPrinting
	 */
	public String getConfigurationInJsonString() {
		return gson.toJson(root);
	}
	
	private class EntriesWalkerProperties implements ConfigurationWalker {
		
		ArrayList<String> paths = new ArrayList<>();
		Properties p = new Properties();
		
		public void onStringEntry(GOFConfigurationString value) {
			p.setProperty(paths.stream().collect(Collectors.joining(".")), value.getValue());
		}
		
		public void onMapEntry(String key, GOFConfigurationItem value) {
			paths.add(key);
			value.deepWalk(this);
			paths.remove(paths.size() - 1);
		}
		
		public void onListEntry(int pos, GOFConfigurationItem value) {
			paths.add(String.valueOf(pos));
			value.deepWalk(this);
			paths.remove(paths.size() - 1);
		}
		
	}
	
	/**
	 * +/- YAML exporter
	 */
	private class EntriesWalkerYAML implements ConfigurationWalker {
		
		int deep;
		PrintStream print;
		
		EntriesWalkerYAML(PrintStream print) {
			this.print = print;
			deep = -1;
		}
		
		private void spaces() {
			print.print(StringUtils.repeat("   ", deep));
		}
		
		public void onStringEntry(GOFConfigurationString value) {
			print.print("\"");
			print.print(value.getValue());
			print.println("\"");
		}
		
		public void onMapEntry(String key, GOFConfigurationItem value) {
			deep++;
			spaces();
			print.print(key);
			print.print(": ");
			if (value.isString() == false) {
				print.println();
			}
			
			value.deepWalk(this);
			deep--;
		}
		
		public void onListEntry(int pos, GOFConfigurationItem value) {
			deep++;
			spaces();
			print.print("- ");
			if (value.isString() == false) {
				print.println();
			}
			
			value.deepWalk(this);
			deep--;
		}
	}
	
	/**
	 * @param path like "val1.val2.val3" or "val1/val2/val3", "/v1/v2", "v1/v2/"
	 * @return may be null
	 */
	public GOFConfigurationItem get(String path) {
		if (path == null) {
			throw new NullPointerException("\"path\" can't to be null");
		}
		return root.getPath(path);
	}
	
	/**
	 * @param json_string a Json Object
	 * @param path, the branch to add, can be null or empty (it will be added to root), or must exists.
	 */
	public void mergeWithJsonString(String json_string, String path) {// TODO test
		merge(path, gson.fromJson(json_string, GOFConfigurationMap.class));
	}
	
	void merge(String from_branch_path, GOFConfigurationMap to_add_with) {
		GOFConfigurationMap branch = root;
		if (from_branch_path != null) {
			if (from_branch_path.equals("") == false) {
				GOFConfigurationItem _branch = get(from_branch_path);
				if (branch == null) {
					throw new NullPointerException("Can't found path: " + from_branch_path);
				} else if (branch.isMap() == false) {
					throw new IndexOutOfBoundsException("Selected path is not a Map: " + from_branch_path);
				}
				branch = _branch.getAsMap();
			}
		}
		merge(branch, to_add_with);
	}
	
	void merge(GOFConfigurationMap from_branch, GOFConfigurationMap to_add_with) {
		EntriesWalkerMerger merger = new EntriesWalkerMerger(from_branch);
		to_add_with.deepWalk(merger);
	}
	
	private class EntriesWalkerMerger implements ConfigurationWalker {
		GOFConfigurationMap from_branch;
		ArrayList<String> paths = new ArrayList<>();
		
		EntriesWalkerMerger(GOFConfigurationMap from_branch) {
			this.from_branch = from_branch;
		}
		
		String getActualPath() {
			return paths.stream().collect(Collectors.joining("."));
		}
		
		public void onStringEntry(GOFConfigurationString value) {
			// TODO Auto-generated method stub
			
		}
		
		public void onMapEntry(String key, GOFConfigurationItem value) {
			paths.add(key);
			// TODO
			value.deepWalk(this);
			paths.remove(paths.size() - 1);
		}
		
		public void onListEntry(int pos, GOFConfigurationItem value) {
			paths.add(String.valueOf(pos));
			
			try {
				GOFConfigurationItem item = from_branch.get(getActualPath());
				
				if (item == null) {
					paths.remove(paths.size() - 1);
					item = from_branch.get(getActualPath());
					
					// item = new GOFConfigurationList();
					
					// TODO create list ?
				}
				
				if (item.isList()) {
					// TODO change pos item with value ?
					// TODO else add item ?
				} else {
					// TODO remove it, and create list ?
				}
				
				value.deepWalk(this);
			} catch (Exception e) {
				log.warn("Can't mergue path, invalid " + getActualPath() + ", " + e.getMessage());
			}
			
			paths.remove(paths.size() - 1);
		}
		
	}
	
}
