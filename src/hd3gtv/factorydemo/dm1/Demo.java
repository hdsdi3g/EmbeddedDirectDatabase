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
 * Copyright (C) hdsdi3g for hd3g.tv 20 déc. 2016
 * 
*/
package hd3gtv.factorydemo.dm1;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Map;

import org.apache.log4j.Logger;

import hd3gtv.factorydemo.annotations.ConstructorGOF;
import hd3gtv.factorydemo.annotations.ParameterGOF;
import hd3gtv.factorydemo.annotations.ParameterListGOF;
import hd3gtv.factorydemo.annotations.ParameterMapGOF;

public class Demo {
	
	private static Logger log = Logger.getLogger(Demo.class);
	
	@ConstructorGOF
	public Demo(@ParameterGOF("param1") String ma_var, @ParameterListGOF(value = "param2list", generic_type = URL.class) ArrayList<URL> urls,
			@ParameterMapGOF(value = "param3map", generic_type = File.class) Map<String, File> files, Demo2 demo2, @ParameterGOF(value = "param4int", default_value = "42") int ma_var2) {
		log.info("New " + getClass().getSimpleName() + " is ok, ma_var: " + ma_var + ", urls: " + urls + ", files: " + files + ", demo2: " + demo2.test() + ", ma_var2: " + ma_var2);
	}
	
	// TODO test with List<Demo2>
	
	public Demo(int nop) {
		log.warn("Demo with wrong contruct...");
	}
	
}
