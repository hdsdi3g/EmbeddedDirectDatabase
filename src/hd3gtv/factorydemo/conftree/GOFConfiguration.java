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

import org.apache.commons.lang.StringUtils;

public final class GOFConfiguration {
	
	private GOFConfigurationMap root;
	
	public GOFConfiguration() {
		root = new GOFConfigurationMap();
		// TODO import from file config
		// TODO create onChange callback caller
		
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
		
		DebugWalker dw = new DebugWalker(System.out);
		root.deepWalk(dw);
	}
	
	/**
	 * +/- YAML exporter
	 */
	private class DebugWalker implements ConfigurationWalker {
		
		int deep;
		PrintStream print;
		
		DebugWalker(PrintStream print) {
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
		
		public void onListEntry(GOFConfigurationItem value) {
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
	
}
