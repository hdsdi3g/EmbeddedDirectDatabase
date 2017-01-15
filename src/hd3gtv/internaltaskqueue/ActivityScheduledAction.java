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
 * Copyright (C) hdsdi3g for hd3g.tv 15 janv. 2017
 * 
*/
package hd3gtv.internaltaskqueue;

import java.util.concurrent.TimeUnit;

public interface ActivityScheduledAction<T> {
	
	public Procedure getRegularScheduledAction();
	
	public long getScheduledActionInitialDelay();
	
	public long getScheduledActionPeriod();
	
	public TimeUnit getScheduledActionPeriodUnit();
	
	/**
	 * @return false if this object must don't keep scheduled.
	 */
	public boolean onScheduledActionError(Exception e);
	
}
