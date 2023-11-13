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

// typedef: _types.BulkStats

@JsonpDeserializable
public class BulkStats implements JsonpSerializable {
	private final long totalOperations;

	@Nullable
	private final String totalTime;

	private final long totalTimeInMillis;

	@Nullable
	private final String totalSize;

	private final long totalSizeInBytes;

	@Nullable
	private final String avgTime;

	private final long avgTimeInMillis;

	@Nullable
	private final String avgSize;

	private final long avgSizeInBytes;

	// ---------------------------------------------------------------------------------------------

	private BulkStats(Builder builder) {

		this.totalOperations = ApiTypeHelper.requireNonNull(builder.totalOperations, this, "totalOperations");
		this.totalTime = builder.totalTime;
		this.totalTimeInMillis = ApiTypeHelper.requireNonNull(builder.totalTimeInMillis, this, "totalTimeInMillis");
		this.totalSize = builder.totalSize;
		this.totalSizeInBytes = ApiTypeHelper.requireNonNull(builder.totalSizeInBytes, this, "totalSizeInBytes");
		this.avgTime = builder.avgTime;
		this.avgTimeInMillis = ApiTypeHelper.requireNonNull(builder.avgTimeInMillis, this, "avgTimeInMillis");
		this.avgSize = builder.avgSize;
		this.avgSizeInBytes = ApiTypeHelper.requireNonNull(builder.avgSizeInBytes, this, "avgSizeInBytes");

	}

	public static BulkStats of(Function<Builder, ObjectBuilder<BulkStats>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code total_operations}
	 */
	public final long totalOperations() {
		return this.totalOperations;
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
	 * API name: {@code avg_time}
	 */
	@Nullable
	public final String avgTime() {
		return this.avgTime;
	}

	/**
	 * Required - API name: {@code avg_time_in_millis}
	 */
	public final long avgTimeInMillis() {
		return this.avgTimeInMillis;
	}

	/**
	 * API name: {@code avg_size}
	 */
	@Nullable
	public final String avgSize() {
		return this.avgSize;
	}

	/**
	 * Required - API name: {@code avg_size_in_bytes}
	 */
	public final long avgSizeInBytes() {
		return this.avgSizeInBytes;
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

		generator.writeKey("total_operations");
		generator.write(this.totalOperations);

		if (this.totalTime != null) {
			generator.writeKey("total_time");
			generator.write(this.totalTime);

		}
		generator.writeKey("total_time_in_millis");
		generator.write(this.totalTimeInMillis);

		if (this.totalSize != null) {
			generator.writeKey("total_size");
			generator.write(this.totalSize);

		}
		generator.writeKey("total_size_in_bytes");
		generator.write(this.totalSizeInBytes);

		if (this.avgTime != null) {
			generator.writeKey("avg_time");
			generator.write(this.avgTime);

		}
		generator.writeKey("avg_time_in_millis");
		generator.write(this.avgTimeInMillis);

		if (this.avgSize != null) {
			generator.writeKey("avg_size");
			generator.write(this.avgSize);

		}
		generator.writeKey("avg_size_in_bytes");
		generator.write(this.avgSizeInBytes);

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link BulkStats}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<BulkStats> {
		private Long totalOperations;

		@Nullable
		private String totalTime;

		private Long totalTimeInMillis;

		@Nullable
		private String totalSize;

		private Long totalSizeInBytes;

		@Nullable
		private String avgTime;

		private Long avgTimeInMillis;

		@Nullable
		private String avgSize;

		private Long avgSizeInBytes;

		/**
		 * Required - API name: {@code total_operations}
		 */
		public final Builder totalOperations(long value) {
			this.totalOperations = value;
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
		 * API name: {@code avg_time}
		 */
		public final Builder avgTime(@Nullable String value) {
			this.avgTime = value;
			return this;
		}

		/**
		 * Required - API name: {@code avg_time_in_millis}
		 */
		public final Builder avgTimeInMillis(long value) {
			this.avgTimeInMillis = value;
			return this;
		}

		/**
		 * API name: {@code avg_size}
		 */
		public final Builder avgSize(@Nullable String value) {
			this.avgSize = value;
			return this;
		}

		/**
		 * Required - API name: {@code avg_size_in_bytes}
		 */
		public final Builder avgSizeInBytes(long value) {
			this.avgSizeInBytes = value;
			return this;
		}

		/**
		 * Builds a {@link BulkStats}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public BulkStats build() {
			_checkSingleUse();

			return new BulkStats(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link BulkStats}
	 */
	public static final JsonpDeserializer<BulkStats> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			BulkStats::setupBulkStatsDeserializer);

	protected static void setupBulkStatsDeserializer(ObjectDeserializer<BulkStats.Builder> op) {

		op.add(Builder::totalOperations, JsonpDeserializer.longDeserializer(), "total_operations");
		op.add(Builder::totalTime, JsonpDeserializer.stringDeserializer(), "total_time");
		op.add(Builder::totalTimeInMillis, JsonpDeserializer.longDeserializer(), "total_time_in_millis");
		op.add(Builder::totalSize, JsonpDeserializer.stringDeserializer(), "total_size");
		op.add(Builder::totalSizeInBytes, JsonpDeserializer.longDeserializer(), "total_size_in_bytes");
		op.add(Builder::avgTime, JsonpDeserializer.stringDeserializer(), "avg_time");
		op.add(Builder::avgTimeInMillis, JsonpDeserializer.longDeserializer(), "avg_time_in_millis");
		op.add(Builder::avgSize, JsonpDeserializer.stringDeserializer(), "avg_size");
		op.add(Builder::avgSizeInBytes, JsonpDeserializer.longDeserializer(), "avg_size_in_bytes");

	}

}
