/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/*
 * Licensed to Elasticsearch B.V. under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

/*
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 */

//----------------------------------------------------
// THIS CODE IS GENERATED. MANUAL EDITS WILL BE LOST.
//----------------------------------------------------

package org.opensearch.client.opensearch._types;

import org.opensearch.client.json.JsonpDeserializable;
import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.JsonpSerializable;
import org.opensearch.client.json.ObjectBuilderDeserializer;
import org.opensearch.client.json.ObjectDeserializer;
import org.opensearch.client.util.ApiTypeHelper;
import org.opensearch.client.util.ObjectBuilder;
import org.opensearch.client.util.ObjectBuilderBase;
import jakarta.json.stream.JsonGenerator;
import java.util.function.Function;
import javax.annotation.Nullable;

// typedef: _types.MergesStats

@JsonpDeserializable
public class MergesStats implements JsonpSerializable {
	private final long current;

	private final long currentDocs;

	@Nullable
	private final String currentSize;

	private final long currentSizeInBytes;

	private final long total;

	@Nullable
	private final String totalAutoThrottle;

	private final long totalAutoThrottleInBytes;

	private final long totalDocs;

	@Nullable
	private final String totalSize;

	private final long totalSizeInBytes;

	@Nullable
	private final String totalStoppedTime;

	private final long totalStoppedTimeInMillis;

	@Nullable
	private final String totalThrottledTime;

	private final long totalThrottledTimeInMillis;

	@Nullable
	private final String totalTime;

	private final long totalTimeInMillis;

	// ---------------------------------------------------------------------------------------------

	private MergesStats(Builder builder) {

		this.current = ApiTypeHelper.requireNonNull(builder.current, this, "current");
		this.currentDocs = ApiTypeHelper.requireNonNull(builder.currentDocs, this, "currentDocs");
		this.currentSize = builder.currentSize;
		this.currentSizeInBytes = ApiTypeHelper.requireNonNull(builder.currentSizeInBytes, this, "currentSizeInBytes");
		this.total = ApiTypeHelper.requireNonNull(builder.total, this, "total");
		this.totalAutoThrottle = builder.totalAutoThrottle;
		this.totalAutoThrottleInBytes = ApiTypeHelper.requireNonNull(builder.totalAutoThrottleInBytes, this,
				"totalAutoThrottleInBytes");
		this.totalDocs = ApiTypeHelper.requireNonNull(builder.totalDocs, this, "totalDocs");
		this.totalSize = builder.totalSize;
		this.totalSizeInBytes = ApiTypeHelper.requireNonNull(builder.totalSizeInBytes, this, "totalSizeInBytes");
		this.totalStoppedTime = builder.totalStoppedTime;
		this.totalStoppedTimeInMillis = ApiTypeHelper.requireNonNull(builder.totalStoppedTimeInMillis, this,
				"totalStoppedTimeInMillis");
		this.totalThrottledTime = builder.totalThrottledTime;
		this.totalThrottledTimeInMillis = ApiTypeHelper.requireNonNull(builder.totalThrottledTimeInMillis, this,
				"totalThrottledTimeInMillis");
		this.totalTime = builder.totalTime;
		this.totalTimeInMillis = ApiTypeHelper.requireNonNull(builder.totalTimeInMillis, this, "totalTimeInMillis");

	}

	public static MergesStats of(Function<Builder, ObjectBuilder<MergesStats>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code current}
	 */
	public final long current() {
		return this.current;
	}

	/**
	 * Required - API name: {@code current_docs}
	 */
	public final long currentDocs() {
		return this.currentDocs;
	}

	/**
	 * API name: {@code current_size}
	 */
	@Nullable
	public final String currentSize() {
		return this.currentSize;
	}

	/**
	 * Required - API name: {@code current_size_in_bytes}
	 */
	public final long currentSizeInBytes() {
		return this.currentSizeInBytes;
	}

	/**
	 * Required - API name: {@code total}
	 */
	public final long total() {
		return this.total;
	}

	/**
	 * API name: {@code total_auto_throttle}
	 */
	@Nullable
	public final String totalAutoThrottle() {
		return this.totalAutoThrottle;
	}

	/**
	 * Required - API name: {@code total_auto_throttle_in_bytes}
	 */
	public final long totalAutoThrottleInBytes() {
		return this.totalAutoThrottleInBytes;
	}

	/**
	 * Required - API name: {@code total_docs}
	 */
	public final long totalDocs() {
		return this.totalDocs;
	}

	/**
	 * API name: {@code total_size}
	 */
	@Nullable
	public final String totalSize() {
		return this.totalSize;
	}

	/**
	 * Required - API name: {@code total_size_in_bytes}
	 */
	public final long totalSizeInBytes() {
		return this.totalSizeInBytes;
	}

	/**
	 * API name: {@code total_stopped_time}
	 */
	@Nullable
	public final String totalStoppedTime() {
		return this.totalStoppedTime;
	}

	/**
	 * Required - API name: {@code total_stopped_time_in_millis}
	 */
	public final long totalStoppedTimeInMillis() {
		return this.totalStoppedTimeInMillis;
	}

	/**
	 * API name: {@code total_throttled_time}
	 */
	@Nullable
	public final String totalThrottledTime() {
		return this.totalThrottledTime;
	}

	/**
	 * Required - API name: {@code total_throttled_time_in_millis}
	 */
	public final long totalThrottledTimeInMillis() {
		return this.totalThrottledTimeInMillis;
	}

	/**
	 * API name: {@code total_time}
	 */
	@Nullable
	public final String totalTime() {
		return this.totalTime;
	}

	/**
	 * Required - API name: {@code total_time_in_millis}
	 */
	public final long totalTimeInMillis() {
		return this.totalTimeInMillis;
	}

	/**
	 * Serialize this object to JSON.
	 */
	public void serialize(JsonGenerator generator, JsonpMapper mapper) {
		generator.writeStartObject();
		serializeInternal(generator, mapper);
		generator.writeEnd();
	}

	protected void serializeInternal(JsonGenerator generator, JsonpMapper mapper) {

		generator.writeKey("current");
		generator.write(this.current);

		generator.writeKey("current_docs");
		generator.write(this.currentDocs);

		if (this.currentSize != null) {
			generator.writeKey("current_size");
			generator.write(this.currentSize);

		}
		generator.writeKey("current_size_in_bytes");
		generator.write(this.currentSizeInBytes);

		generator.writeKey("total");
		generator.write(this.total);

		if (this.totalAutoThrottle != null) {
			generator.writeKey("total_auto_throttle");
			generator.write(this.totalAutoThrottle);

		}
		generator.writeKey("total_auto_throttle_in_bytes");
		generator.write(this.totalAutoThrottleInBytes);

		generator.writeKey("total_docs");
		generator.write(this.totalDocs);

		if (this.totalSize != null) {
			generator.writeKey("total_size");
			generator.write(this.totalSize);

		}
		generator.writeKey("total_size_in_bytes");
		generator.write(this.totalSizeInBytes);

		if (this.totalStoppedTime != null) {
			generator.writeKey("total_stopped_time");
			generator.write(this.totalStoppedTime);

		}
		generator.writeKey("total_stopped_time_in_millis");
		generator.write(this.totalStoppedTimeInMillis);

		if (this.totalThrottledTime != null) {
			generator.writeKey("total_throttled_time");
			generator.write(this.totalThrottledTime);

		}
		generator.writeKey("total_throttled_time_in_millis");
		generator.write(this.totalThrottledTimeInMillis);

		if (this.totalTime != null) {
			generator.writeKey("total_time");
			generator.write(this.totalTime);

		}
		generator.writeKey("total_time_in_millis");
		generator.write(this.totalTimeInMillis);

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link MergesStats}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<MergesStats> {
		private Long current;

		private Long currentDocs;

		@Nullable
		private String currentSize;

		private Long currentSizeInBytes;

		private Long total;

		@Nullable
		private String totalAutoThrottle;

		private Long totalAutoThrottleInBytes;

		private Long totalDocs;

		@Nullable
		private String totalSize;

		private Long totalSizeInBytes;

		@Nullable
		private String totalStoppedTime;

		private Long totalStoppedTimeInMillis;

		@Nullable
		private String totalThrottledTime;

		private Long totalThrottledTimeInMillis;

		@Nullable
		private String totalTime;

		private Long totalTimeInMillis;

		/**
		 * Required - API name: {@code current}
		 */
		public final Builder current(long value) {
			this.current = value;
			return this;
		}

		/**
		 * Required - API name: {@code current_docs}
		 */
		public final Builder currentDocs(long value) {
			this.currentDocs = value;
			return this;
		}

		/**
		 * API name: {@code current_size}
		 */
		public final Builder currentSize(@Nullable String value) {
			this.currentSize = value;
			return this;
		}

		/**
		 * Required - API name: {@code current_size_in_bytes}
		 */
		public final Builder currentSizeInBytes(long value) {
			this.currentSizeInBytes = value;
			return this;
		}

		/**
		 * Required - API name: {@code total}
		 */
		public final Builder total(long value) {
			this.total = value;
			return this;
		}

		/**
		 * API name: {@code total_auto_throttle}
		 */
		public final Builder totalAutoThrottle(@Nullable String value) {
			this.totalAutoThrottle = value;
			return this;
		}

		/**
		 * Required - API name: {@code total_auto_throttle_in_bytes}
		 */
		public final Builder totalAutoThrottleInBytes(long value) {
			this.totalAutoThrottleInBytes = value;
			return this;
		}

		/**
		 * Required - API name: {@code total_docs}
		 */
		public final Builder totalDocs(long value) {
			this.totalDocs = value;
			return this;
		}

		/**
		 * API name: {@code total_size}
		 */
		public final Builder totalSize(@Nullable String value) {
			this.totalSize = value;
			return this;
		}

		/**
		 * Required - API name: {@code total_size_in_bytes}
		 */
		public final Builder totalSizeInBytes(long value) {
			this.totalSizeInBytes = value;
			return this;
		}

		/**
		 * API name: {@code total_stopped_time}
		 */
		public final Builder totalStoppedTime(@Nullable String value) {
			this.totalStoppedTime = value;
			return this;
		}

		/**
		 * Required - API name: {@code total_stopped_time_in_millis}
		 */
		public final Builder totalStoppedTimeInMillis(long value) {
			this.totalStoppedTimeInMillis = value;
			return this;
		}

		/**
		 * API name: {@code total_throttled_time}
		 */
		public final Builder totalThrottledTime(@Nullable String value) {
			this.totalThrottledTime = value;
			return this;
		}

		/**
		 * Required - API name: {@code total_throttled_time_in_millis}
		 */
		public final Builder totalThrottledTimeInMillis(long value) {
			this.totalThrottledTimeInMillis = value;
			return this;
		}

		/**
		 * API name: {@code total_time}
		 */
		public final Builder totalTime(@Nullable String value) {
			this.totalTime = value;
			return this;
		}

		/**
		 * Required - API name: {@code total_time_in_millis}
		 */
		public final Builder totalTimeInMillis(long value) {
			this.totalTimeInMillis = value;
			return this;
		}

		/**
		 * Builds a {@link MergesStats}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public MergesStats build() {
			_checkSingleUse();

			return new MergesStats(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link MergesStats}
	 */
	public static final JsonpDeserializer<MergesStats> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			MergesStats::setupMergesStatsDeserializer);

	protected static void setupMergesStatsDeserializer(ObjectDeserializer<MergesStats.Builder> op) {

		op.add(Builder::current, JsonpDeserializer.longDeserializer(), "current");
		op.add(Builder::currentDocs, JsonpDeserializer.longDeserializer(), "current_docs");
		op.add(Builder::currentSize, JsonpDeserializer.stringDeserializer(), "current_size");
		op.add(Builder::currentSizeInBytes, JsonpDeserializer.longDeserializer(), "current_size_in_bytes");
		op.add(Builder::total, JsonpDeserializer.longDeserializer(), "total");
		op.add(Builder::totalAutoThrottle, JsonpDeserializer.stringDeserializer(), "total_auto_throttle");
		op.add(Builder::totalAutoThrottleInBytes, JsonpDeserializer.longDeserializer(), "total_auto_throttle_in_bytes");
		op.add(Builder::totalDocs, JsonpDeserializer.longDeserializer(), "total_docs");
		op.add(Builder::totalSize, JsonpDeserializer.stringDeserializer(), "total_size");
		op.add(Builder::totalSizeInBytes, JsonpDeserializer.longDeserializer(), "total_size_in_bytes");
		op.add(Builder::totalStoppedTime, JsonpDeserializer.stringDeserializer(), "total_stopped_time");
		op.add(Builder::totalStoppedTimeInMillis, JsonpDeserializer.longDeserializer(), "total_stopped_time_in_millis");
		op.add(Builder::totalThrottledTime, JsonpDeserializer.stringDeserializer(), "total_throttled_time");
		op.add(Builder::totalThrottledTimeInMillis, JsonpDeserializer.longDeserializer(),
				"total_throttled_time_in_millis");
		op.add(Builder::totalTime, JsonpDeserializer.stringDeserializer(), "total_time");
		op.add(Builder::totalTimeInMillis, JsonpDeserializer.longDeserializer(), "total_time_in_millis");

	}

}
