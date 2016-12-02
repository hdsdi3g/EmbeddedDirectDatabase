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
 * Copyright (C) hdsdi3g for hd3g.tv 2 déc. 2016
 * 
*/
package hd3gtv.embddb.tools;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.LinkedHashMap;

import org.apache.log4j.Logger;

import hd3gtv.internaltaskqueue.ParametedProcedure;

public class InteractiveConsoleMode {
	
	private static Logger log = Logger.getLogger(InteractiveConsoleMode.class);
	
	private LinkedHashMap<String, Action> controller;
	
	public InteractiveConsoleMode() throws NullPointerException {
		controller = new LinkedHashMap<>();
		
		Action a = new Action("quit", "Exit application.", InteractiveConsoleMode.class, line -> {
			System.exit(0);
		});
		controller.put("q", a);
		controller.put("quit", a);
		controller.put("exit", a);
		controller.put("bye", a);
		
		a = new Action("help", "Show help.", InteractiveConsoleMode.class, line -> {
			System.out.println("Show help:");
			
			HashSet<Action> actual_actions = new HashSet<>(controller.size());
			controller.forEach((order, action) -> {
				if (actual_actions.contains(action) == false) {
					actual_actions.add(action);
					System.out.print(" - ");
					System.out.print(order);
					System.out.print("\t");
					System.out.print(action.toString());
					System.out.println();
				}
			});
			System.out.println();
		});
		controller.put("h", a);
		controller.put("?", a);
		controller.put("help", a);
		
		// TODO add loop action (do same action each seconds) + stop
	}
	
	private class Action {
		private ParametedProcedure<String> procedure;
		private String name;
		private String description;
		private Class<?> creator;
		
		Action(String name, String description, Class<?> creator, ParametedProcedure<String> procedure) {
			this.procedure = procedure;
			if (procedure == null) {
				throw new NullPointerException("\"procedure\" can't to be null");
			}
			this.name = name;
			if (name == null) {
				throw new NullPointerException("\"name\" can't to be null");
			}
			this.description = description;
			if (description == null) {
				throw new NullPointerException("\"description\" can't to be null");
			}
			this.creator = creator;
			if (creator == null) {
				throw new NullPointerException("\"creator\" can't to be null");
			}
		}
		
		public String toString() {
			StringBuilder sb = new StringBuilder();
			sb.append(name);
			sb.append(", ");
			sb.append(description);
			sb.append(" (by ");
			sb.append(creator.getSimpleName());
			sb.append(")");
			return sb.toString();
		}
		
	}
	
	/**
	 * @param order
	 * @param name
	 * @param description
	 * @param creator
	 * @param procedure callbacked param maybe null.
	 */
	public void addOrder(String order, String name, String description, Class<?> creator, ParametedProcedure<String> procedure) {
		synchronized (controller) {
			if (controller.containsKey(order)) {
				log.warn("Action " + order + " already exists. Added by " + controller.get(order).creator + " and in conflict with " + creator);
				return;
			}
			controller.put(order.toLowerCase(), new Action(name, description, creator, procedure));
		}
	}
	
	/**
	 * Blocking !
	 */
	public void waitActions() {
		BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(System.in));
		String line;
		String order;
		String param;
		
		System.out.println(" ====== Start interactive mode ====== ");
		System.out.println(" == Use q for quit and h for help  ==");
		System.out.println();
		try {
			while (true) {
				System.out.print("> ");
				line = bufferedReader.readLine().trim();
				if (line.equals("")) {
					continue;
				}
				
				if (line.indexOf(" ") > -1) {
					order = line.split(" ")[0].toLowerCase();
					param = line.substring(line.indexOf(" ") + 1);
				} else {
					order = line.toLowerCase();
					param = null;
				}
				
				if (controller.containsKey(order) == false) {
					System.out.println("Unknow action \"" + order + "\"");
					continue;
				}
				
				try {
					controller.get(order).procedure.process(param);
				} catch (Exception e) {
					System.out.println("Error during " + order);
					e.printStackTrace(System.out);
					continue;
				}
			}
		} catch (IOException e) {
			log.error("Exit Console mode", e);
		}
	}
	
}
