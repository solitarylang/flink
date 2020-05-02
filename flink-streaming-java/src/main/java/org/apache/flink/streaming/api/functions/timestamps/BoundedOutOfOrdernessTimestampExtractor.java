/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.streaming.api.functions.timestamps;

import org.apache.flink.streaming.api.functions.AssignerWithPeriodicWatermarks;
import org.apache.flink.streaming.api.watermark.Watermark;
import org.apache.flink.streaming.api.windowing.time.Time;

/**
 * This is a {@link AssignerWithPeriodicWatermarks} used to emit Watermarks that lag behind the element with
 * the maximum timestamp (in event time) seen so far by a fixed amount of time, <code>t_late</code>. This can
 * help reduce the number of elements that are ignored due to lateness when computing the final result for a
 * given window, in the case where we know that elements arrive no later than <code>t_late</code> units of time
 * after the watermark that signals that the system event-time has advanced past their (event-time) timestamp.
 */
/* 在计算给定窗口最终结果时，减少因为延迟到来而被丢弃的数据 */
public abstract class BoundedOutOfOrdernessTimestampExtractor<T> implements AssignerWithPeriodicWatermarks<T> {
	// 这个T泛指传入数据中的每一个元素，可以使用case class转换
	private static final long serialVersionUID = 1L;

	/* 当前已经进入flink所有数据中最大的event time */
	private long currentMaxTimestamp;

	/* 上一次发送的水印，初始化默认是Long里面最小的数(负的) */
	private long lastEmittedWatermark = Long.MIN_VALUE;

	/* 就是最大延迟时间 = currentMaxTimestamp - lastEmittedWatermark */
	private final long maxOutOfOrderness;

	// 初始化，maxOutOfOrderness是初始化的最大延迟时间
	public BoundedOutOfOrdernessTimestampExtractor(Time maxOutOfOrderness) {
		if (maxOutOfOrderness.toMilliseconds() < 0) {
			throw new RuntimeException("Tried to set the maximum allowed " +
				"lateness to " + maxOutOfOrderness + ". This parameter cannot be negative.");
		}
		this.maxOutOfOrderness = maxOutOfOrderness.toMilliseconds();
		this.currentMaxTimestamp = Long.MIN_VALUE + this.maxOutOfOrderness;
	}

	public long getMaxOutOfOrdernessInMillis() {
		return maxOutOfOrderness;
	}

	/* 从流基本元素(如每一条日志)中提取event time，可使用匿名实现类重写此方法 */
	public abstract long extractTimestamp(T element);

	/* 为当前生成水印，如果最大时间戳不边，那么水印也不会变 */
	@Override
	public final Watermark getCurrentWatermark() {
		// this guarantees that the watermark never goes backwards.
		long potentialWM = currentMaxTimestamp - maxOutOfOrderness;
		if (potentialWM >= lastEmittedWatermark) {
			lastEmittedWatermark = potentialWM;
		}
		return new Watermark(lastEmittedWatermark);
	}

	/* 提取event time来更新当前最大的event time */
	@Override
	public final long extractTimestamp(T element, long previousElementTimestamp) {
		long timestamp = extractTimestamp(element);
		if (timestamp > currentMaxTimestamp) {
			currentMaxTimestamp = timestamp;
		}
		return timestamp;
	}
}
