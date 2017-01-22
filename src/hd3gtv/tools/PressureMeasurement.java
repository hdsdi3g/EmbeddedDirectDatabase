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
 * Copyright (C) hdsdi3g for hd3g.tv 22 janv. 2017
 * 
*/
package hd3gtv.tools;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

public class PressureMeasurement {
	
	private long start_time;
	private AtomicLong recevied_datas;
	private AtomicLong sended_datas;
	private AtomicLong recevied_blocks;
	private AtomicLong sended_blocks;
	
	public PressureMeasurement() {
		start_time = System.currentTimeMillis();
		recevied_datas = new AtomicLong();
		sended_datas = new AtomicLong();
		recevied_blocks = new AtomicLong();
		sended_blocks = new AtomicLong();
	}
	
	public void onReceviedBlock(long size) {
		recevied_blocks.incrementAndGet();
		recevied_datas.addAndGet(size);
	}
	
	public void onSendedBlock(long size) {
		sended_blocks.incrementAndGet();
		sended_datas.addAndGet(size);
	}
	
	public CollectedData getActualStats(boolean reset_after) {
		CollectedData r = new CollectedData();
		if (reset_after) {
			reset();
		}
		return r;
	}
	
	public void reset() {
		recevied_datas.set(0);
		sended_datas.set(0);
		recevied_blocks.set(0);
		sended_blocks.set(0);
		start_time = System.currentTimeMillis();
	}
	
	public class CollectedData {
		
		/**
		 * In sec
		 */
		private double duration;
		
		private double last_recevied_datas;
		private double last_sended_datas;
		private double last_recevied_blocks;
		private double last_sended_blocks;
		
		private CollectedData() {
			duration = (System.currentTimeMillis() - start_time) / 1000d;
			last_recevied_datas = recevied_datas.get();
			last_sended_datas = sended_datas.get();
			last_recevied_blocks = recevied_blocks.get();
			last_sended_blocks = sended_blocks.get();
		}
		
		/**
		 * @return blk/sec
		 */
		public double getReceviedBlocksSpeed() {
			return last_recevied_blocks / duration;
		}
		
		/**
		 * @return bytes/sec
		 */
		public double getReceviedDatasSpeed() {
			return last_recevied_datas / duration;
		}
		
		/**
		 * @return blk/sec
		 */
		public double getSendedBlocksSpeed() {
			return last_sended_blocks / duration;
		}
		
		/**
		 * @return bytes/sec
		 */
		public double getSendedDatasSpeed() {
			return last_sended_datas / duration;
		}
		
		public String toString() {
			NumberFormat nf = NumberFormat.getNumberInstance(Locale.getDefault());
			DecimalFormat df = (DecimalFormat) nf;
			df.applyPattern("###,###,###.#");
			
			StringBuilder sb = new StringBuilder();
			sb.append("The last ");
			sb.append(df.format(duration));
			sb.append(" sec: ");
			sb.append("recevied ");
			sb.append(df.format(getReceviedBlocksSpeed() / 1000d));
			sb.append(" blk/sec for ");
			sb.append(df.format(getReceviedDatasSpeed()));
			sb.append(" kB and sended ");
			sb.append(df.format(getSendedBlocksSpeed() / 1000d));
			sb.append(" blk/sec for ");
			sb.append(df.format(getSendedDatasSpeed()));
			sb.append(" kB");
			return sb.toString();
		}
		
	}
	
}
