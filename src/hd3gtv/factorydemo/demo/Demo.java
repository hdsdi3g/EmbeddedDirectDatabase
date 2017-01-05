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
package hd3gtv.factorydemo.demo;

import java.net.URL;
import java.util.ArrayList;

import hd3gtv.factorydemo.annotations.DefaultGOF;
import hd3gtv.factorydemo.annotations.ListOfGOF;
import hd3gtv.factorydemo.annotations.NameGOF;
import hd3gtv.factorydemo.annotations.PreferedConstructorGOF;

public class Demo {
	
	@PreferedConstructorGOF
	public Demo(@DefaultGOF("AAA") String ma_var, @ListOfGOF(URL.class) @NameGOF("urls") ArrayList<URL> urls) {
	}
	
	public Demo(int nop) {
	}
	
}
