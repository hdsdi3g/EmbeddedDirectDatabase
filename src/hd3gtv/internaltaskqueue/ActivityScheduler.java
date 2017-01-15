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
 * Copyright (C) hdsdi3g for hd3g.tv 27 nov. 2016
 * 
*/
package hd3gtv.internaltaskqueue;

import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;

import hd3gtv.embddb.tools.InteractiveConsoleMode;

public class ActivityScheduler<T> {
	
	private static Logger log = Logger.getLogger(ActivityScheduler.class);
	
	private ScheduledExecutorService scheduled_ex_service;
	
	private ArrayList<RegularTask> regular_tasks;
	
	public ActivityScheduler() {
		scheduled_ex_service = Executors.newSingleThreadScheduledExecutor();
		regular_tasks = new ArrayList<>(1);
	}
	
	/**
	 * @param procedure Add only lightweight actions, like "add a Task in a queue". if thrown something, stop regular and remove from internal list.
	 */
	public void add(T reference, Procedure procedure, long initialDelay, long period, TimeUnit unit) {
		RegularTask rt = new RegularTask(reference, procedure, initialDelay, period, unit);
		
		synchronized (regular_tasks) {
			if (regular_tasks.stream().anyMatch(task -> {
				return task.equalsReference(reference);
			}) == false) {
				regular_tasks.add(rt);
			}
		}
	}
	
	/**
	 * @param procedure Add only lightweight actions, like "add a Task in a queue".
	 */
	public void add(T reference, ActivityScheduledAction<T> action) {
		RegularTask rt = new RegularTask(reference, action);
		
		synchronized (regular_tasks) {
			if (regular_tasks.stream().anyMatch(task -> {
				return task.equalsReference(reference);
			}) == false) {
				regular_tasks.add(rt);
			}
		}
	}
	
	public void remove(T reference) {
		synchronized (regular_tasks) {
			regular_tasks.removeIf(f -> {
				if (f.equalsReference(reference)) {
					f.cancel();
					return true;
				}
				return false;
			});
		}
	}
	
	private class RegularTask {
		ScheduledFuture<?> future;
		T reference;
		String descr;
		ActivityScheduledAction<T> action;
		
		RegularTask(T reference, ActivityScheduledAction<T> action) {
			this(reference, action.getRegularScheduledAction(), action.getScheduledActionInitialDelay(), action.getScheduledActionPeriod(), action.getScheduledActionPeriodUnit());
			this.action = action;
		}
		
		RegularTask(T reference, Procedure procedure, long initialDelay, long period, TimeUnit unit) {
			this.reference = reference;
			if (reference == null) {
				throw new NullPointerException("\"reference\" can't to be null");
			}
			if (procedure == null) {
				throw new NullPointerException("\"procedure\" can't to be null");
			}
			if (unit == null) {
				throw new NullPointerException("\"unit\" can't to be null");
			}
			
			RegularTask ref = this;
			
			future = scheduled_ex_service.scheduleAtFixedRate(() -> {
				try {
					procedure.process();
				} catch (Exception e) {
					if (action != null) {
						if (action.onScheduledActionError(e)) {
							return;
						}
					}
					log.error("Can't execute (regular) process", e);
					
					synchronized (regular_tasks) {
						regular_tasks.remove(ref);
					}
				}
			}, initialDelay, period, unit);
			
			StringBuilder sb = new StringBuilder();
			sb.append("Reference " + reference.getClass() + " [" + reference.toString() + "] ");
			sb.append("Procedure " + procedure.getClass() + " ");
			sb.append("Period " + unit.toMillis(period) + " ms");
			descr = sb.toString();
		}
		
		void cancel() {
			future.cancel(false);
		}
		
		boolean equalsReference(T compare) {
			return reference.equals(compare);
		}
		
		public String toString() {
			return descr;
		}
		
	}
	
	public void setConsole(InteractiveConsoleMode console) {
		/*if (console == null) {// TODO handle multiple with console ?!
			throw new NullPointerException("\"console\" can't to be null");
		}
		console.addOrder("scl", "Activity Scheduler List", "Display the activated regular task list", getClass(), param -> {
			
			if (regular_tasks.isEmpty()) {
				System.out.println("No regular tasks to display.");
			} else {
				System.out.println("Display " + regular_tasks.size() + " regular task");
				regular_tasks.forEach(pt -> {
					System.out.println(pt.toString());
				});
			}
		});*/
	}
	
}
