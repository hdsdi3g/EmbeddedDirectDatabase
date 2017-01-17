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

import hd3gtv.mydmam.Loggers;
import hd3gtv.tools.TableList;

public class ActivityScheduler<T> {
	
	private static Logger log = Logger.getLogger(ActivityScheduler.class);
	
	private ScheduledExecutorService scheduled_ex_service;
	
	private ArrayList<RegularTask> regular_tasks;
	
	public ActivityScheduler() {
		scheduled_ex_service = Executors.newSingleThreadScheduledExecutor();
		regular_tasks = new ArrayList<>(1);
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
		ActivityScheduledAction<T> action;
		long last_exec_date = 0;
		long period;
		
		RegularTask(T reference, ActivityScheduledAction<T> action) {
			this.reference = reference;
			if (reference == null) {
				throw new NullPointerException("\"reference\" can't to be null");
			}
			this.action = action;
			if (action == null) {
				throw new NullPointerException("\"action\" can't to be null");
			}
			
			Procedure procedure = action.getRegularScheduledAction();
			if (procedure == null) {
				throw new NullPointerException("\"procedure\" can't to be null");
			}
			
			long initialDelay = action.getScheduledActionInitialDelay();
			
			long period = action.getScheduledActionPeriod();
			
			TimeUnit unit = action.getScheduledActionPeriodUnit();
			if (unit == null) {
				throw new NullPointerException("\"unit\" can't to be null");
			}
			
			RegularTask ref = this;
			
			future = scheduled_ex_service.scheduleAtFixedRate(() -> {
				log.debug("Do regular process \"" + action.getScheduledActionName() + "\" for " + reference.getClass().getSimpleName());
				try {
					procedure.process();
					last_exec_date = System.currentTimeMillis();
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
			
			this.period = unit.toMillis(period);
		}
		
		void cancel() {
			future.cancel(false);
		}
		
		boolean equalsReference(T compare) {
			return reference.equals(compare);
		}
		
	}
	
	public boolean isEmpty() {
		return regular_tasks.isEmpty();
	}
	
	public int size() {
		return regular_tasks.size();
	}
	
	/**
	 * @param table len must equals 5
	 */
	public void getAllScheduledTasks(TableList table) {
		table.addRow("Status", "Name", "Reference", "Period (sec)", "Last executed");
		regular_tasks.forEach(task -> {
			String last = "(never)";
			if (task.last_exec_date > 0) {
				last = Loggers.dateLog(task.last_exec_date);
			}
			if (task.future.isCancelled()) {
				table.addRow("CANCELED", task.action.getScheduledActionName(), task.reference.getClass().getName(), String.valueOf((float) task.period / 1000f), last);
			} else if (task.future.isDone()) {
				table.addRow("DONE", task.action.getScheduledActionName(), task.reference.getClass().getName(), String.valueOf((float) task.period / 1000f), last);
			} else {
				table.addRow("WAIT", task.action.getScheduledActionName(), task.reference.getClass().getName(), String.valueOf((float) task.period / 1000f), last);
			}
		});
	}
	
}
