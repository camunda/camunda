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

// typedef: _types.QueryCacheStats

@JsonpDeserializable
public class QueryCacheStats implements JsonpSerializable {
	private final int cacheCount;

	private final int cacheSize;

	private final int evictions;

	private final int hitCount;

	@Nullable
	private final String memorySize;

	private final int memorySizeInBytes;

	private final int missCount;

	private final int totalCount;

	// ---------------------------------------------------------------------------------------------

	private QueryCacheStats(Builder builder) {

		this.cacheCount = ApiTypeHelper.requireNonNull(builder.cacheCount, this, "cacheCount");
		this.cacheSize = ApiTypeHelper.requireNonNull(builder.cacheSize, this, "cacheSize");
		this.evictions = ApiTypeHelper.requireNonNull(builder.evictions, this, "evictions");
		this.hitCount = ApiTypeHelper.requireNonNull(builder.hitCount, this, "hitCount");
		this.memorySize = builder.memorySize;
		this.memorySizeInBytes = ApiTypeHelper.requireNonNull(builder.memorySizeInBytes, this, "memorySizeInBytes");
		this.missCount = ApiTypeHelper.requireNonNull(builder.missCount, this, "missCount");
		this.totalCount = ApiTypeHelper.requireNonNull(builder.totalCount, this, "totalCount");

	}

	public static QueryCacheStats of(Function<Builder, ObjectBuilder<QueryCacheStats>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code cache_count}
	 */
	public final int cacheCount() {
		return this.cacheCount;
	}

	/**
	 * Required - API name: {@code cache_size}
	 */
	public final int cacheSize() {
		return this.cacheSize;
	}

	/**
	 * Required - API name: {@code evictions}
	 */
	public final int evictions() {
		return this.evictions;
	}

	/**
	 * Required - API name: {@code hit_count}
	 */
	public final int hitCount() {
		return this.hitCount;
	}

	/**
	 * API name: {@code memory_size}
	 */
	@Nullable
	public final String memorySize() {
		return this.memorySize;
	}

	/**
	 * Required - API name: {@code memory_size_in_bytes}
	 */
	public final int memorySizeInBytes() {
		return this.memorySizeInBytes;
	}

	/**
	 * Required - API name: {@code miss_count}
	 */
	public final int missCount() {
		return this.missCount;
	}

	/**
	 * Required - API name: {@code total_count}
	 */
	public final int totalCount() {
		return this.totalCount;
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

		generator.writeKey("cache_count");
		generator.write(this.cacheCount);

		generator.writeKey("cache_size");
		generator.write(this.cacheSize);

		generator.writeKey("evictions");
		generator.write(this.evictions);

		generator.writeKey("hit_count");
		generator.write(this.hitCount);

		if (this.memorySize != null) {
			generator.writeKey("memory_size");
			generator.write(this.memorySize);

		}
		generator.writeKey("memory_size_in_bytes");
		generator.write(this.memorySizeInBytes);

		generator.writeKey("miss_count");
		generator.write(this.missCount);

		generator.writeKey("total_count");
		generator.write(this.totalCount);

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link QueryCacheStats}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<QueryCacheStats> {
		private Integer cacheCount;

		private Integer cacheSize;

		private Integer evictions;

		private Integer hitCount;

		@Nullable
		private String memorySize;

		private Integer memorySizeInBytes;

		private Integer missCount;

		private Integer totalCount;

		/**
		 * Required - API name: {@code cache_count}
		 */
		public final Builder cacheCount(int value) {
			this.cacheCount = value;
			return this;
		}

		/**
		 * Required - API name: {@code cache_size}
		 */
		public final Builder cacheSize(int value) {
			this.cacheSize = value;
			return this;
		}

		/**
		 * Required - API name: {@code evictions}
		 */
		public final Builder evictions(int value) {
			this.evictions = value;
			return this;
		}

		/**
		 * Required - API name: {@code hit_count}
		 */
		public final Builder hitCount(int value) {
			this.hitCount = value;
			return this;
		}

		/**
		 * API name: {@code memory_size}
		 */
		public final Builder memorySize(@Nullable String value) {
			this.memorySize = value;
			return this;
		}

		/**
		 * Required - API name: {@code memory_size_in_bytes}
		 */
		public final Builder memorySizeInBytes(int value) {
			this.memorySizeInBytes = value;
			return this;
		}

		/**
		 * Required - API name: {@code miss_count}
		 */
		public final Builder missCount(int value) {
			this.missCount = value;
			return this;
		}

		/**
		 * Required - API name: {@code total_count}
		 */
		public final Builder totalCount(int value) {
			this.totalCount = value;
			return this;
		}

		/**
		 * Builds a {@link QueryCacheStats}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public QueryCacheStats build() {
			_checkSingleUse();

			return new QueryCacheStats(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link QueryCacheStats}
	 */
	public static final JsonpDeserializer<QueryCacheStats> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			QueryCacheStats::setupQueryCacheStatsDeserializer);

	protected static void setupQueryCacheStatsDeserializer(ObjectDeserializer<QueryCacheStats.Builder> op) {

		op.add(Builder::cacheCount, JsonpDeserializer.integerDeserializer(), "cache_count");
		op.add(Builder::cacheSize, JsonpDeserializer.integerDeserializer(), "cache_size");
		op.add(Builder::evictions, JsonpDeserializer.integerDeserializer(), "evictions");
		op.add(Builder::hitCount, JsonpDeserializer.integerDeserializer(), "hit_count");
		op.add(Builder::memorySize, JsonpDeserializer.stringDeserializer(), "memory_size");
		op.add(Builder::memorySizeInBytes, JsonpDeserializer.integerDeserializer(), "memory_size_in_bytes");
		op.add(Builder::missCount, JsonpDeserializer.integerDeserializer(), "miss_count");
		op.add(Builder::totalCount, JsonpDeserializer.integerDeserializer(), "total_count");

	}

}
