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
import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nullable;

// typedef: _types.IndexingStats

@JsonpDeserializable
public class IndexingStats implements JsonpSerializable {
	private final long indexCurrent;

	private final long deleteCurrent;

	@Nullable
	private final String deleteTime;

	private final long deleteTimeInMillis;

	private final long deleteTotal;

	private final boolean isThrottled;

	private final long noopUpdateTotal;

	@Nullable
	private final String throttleTime;

	private final long throttleTimeInMillis;

	@Nullable
	private final String indexTime;

	private final long indexTimeInMillis;

	private final long indexTotal;

	private final long indexFailed;

	private final Map<String, IndexingStats> types;

	// ---------------------------------------------------------------------------------------------

	private IndexingStats(Builder builder) {

		this.indexCurrent = ApiTypeHelper.requireNonNull(builder.indexCurrent, this, "indexCurrent");
		this.deleteCurrent = ApiTypeHelper.requireNonNull(builder.deleteCurrent, this, "deleteCurrent");
		this.deleteTime = builder.deleteTime;
		this.deleteTimeInMillis = ApiTypeHelper.requireNonNull(builder.deleteTimeInMillis, this, "deleteTimeInMillis");
		this.deleteTotal = ApiTypeHelper.requireNonNull(builder.deleteTotal, this, "deleteTotal");
		this.isThrottled = ApiTypeHelper.requireNonNull(builder.isThrottled, this, "isThrottled");
		this.noopUpdateTotal = ApiTypeHelper.requireNonNull(builder.noopUpdateTotal, this, "noopUpdateTotal");
		this.throttleTime = builder.throttleTime;
		this.throttleTimeInMillis = ApiTypeHelper.requireNonNull(builder.throttleTimeInMillis, this,
				"throttleTimeInMillis");
		this.indexTime = builder.indexTime;
		this.indexTimeInMillis = ApiTypeHelper.requireNonNull(builder.indexTimeInMillis, this, "indexTimeInMillis");
		this.indexTotal = ApiTypeHelper.requireNonNull(builder.indexTotal, this, "indexTotal");
		this.indexFailed = ApiTypeHelper.requireNonNull(builder.indexFailed, this, "indexFailed");
		this.types = ApiTypeHelper.unmodifiable(builder.types);

	}

	public static IndexingStats of(Function<Builder, ObjectBuilder<IndexingStats>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code index_current}
	 */
	public final long indexCurrent() {
		return this.indexCurrent;
	}

	/**
	 * Required - API name: {@code delete_current}
	 */
	public final long deleteCurrent() {
		return this.deleteCurrent;
	}

	/**
	 * API name: {@code delete_time}
	 */
	@Nullable
	public final String deleteTime() {
		return this.deleteTime;
	}

	/**
	 * Required - API name: {@code delete_time_in_millis}
	 */
	public final long deleteTimeInMillis() {
		return this.deleteTimeInMillis;
	}

	/**
	 * Required - API name: {@code delete_total}
	 */
	public final long deleteTotal() {
		return this.deleteTotal;
	}

	/**
	 * Required - API name: {@code is_throttled}
	 */
	public final boolean isThrottled() {
		return this.isThrottled;
	}

	/**
	 * Required - API name: {@code noop_update_total}
	 */
	public final long noopUpdateTotal() {
		return this.noopUpdateTotal;
	}

	/**
	 * API name: {@code throttle_time}
	 */
	@Nullable
	public final String throttleTime() {
		return this.throttleTime;
	}

	/**
	 * Required - API name: {@code throttle_time_in_millis}
	 */
	public final long throttleTimeInMillis() {
		return this.throttleTimeInMillis;
	}

	/**
	 * API name: {@code index_time}
	 */
	@Nullable
	public final String indexTime() {
		return this.indexTime;
	}

	/**
	 * Required - API name: {@code index_time_in_millis}
	 */
	public final long indexTimeInMillis() {
		return this.indexTimeInMillis;
	}

	/**
	 * Required - API name: {@code index_total}
	 */
	public final long indexTotal() {
		return this.indexTotal;
	}

	/**
	 * Required - API name: {@code index_failed}
	 */
	public final long indexFailed() {
		return this.indexFailed;
	}

	/**
	 * API name: {@code types}
	 */
	public final Map<String, IndexingStats> types() {
		return this.types;
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

		generator.writeKey("index_current");
		generator.write(this.indexCurrent);

		generator.writeKey("delete_current");
		generator.write(this.deleteCurrent);

		if (this.deleteTime != null) {
			generator.writeKey("delete_time");
			generator.write(this.deleteTime);

		}
		generator.writeKey("delete_time_in_millis");
		generator.write(this.deleteTimeInMillis);

		generator.writeKey("delete_total");
		generator.write(this.deleteTotal);

		generator.writeKey("is_throttled");
		generator.write(this.isThrottled);

		generator.writeKey("noop_update_total");
		generator.write(this.noopUpdateTotal);

		if (this.throttleTime != null) {
			generator.writeKey("throttle_time");
			generator.write(this.throttleTime);

		}
		generator.writeKey("throttle_time_in_millis");
		generator.write(this.throttleTimeInMillis);

		if (this.indexTime != null) {
			generator.writeKey("index_time");
			generator.write(this.indexTime);

		}
		generator.writeKey("index_time_in_millis");
		generator.write(this.indexTimeInMillis);

		generator.writeKey("index_total");
		generator.write(this.indexTotal);

		generator.writeKey("index_failed");
		generator.write(this.indexFailed);

		if (ApiTypeHelper.isDefined(this.types)) {
			generator.writeKey("types");
			generator.writeStartObject();
			for (Map.Entry<String, IndexingStats> item0 : this.types.entrySet()) {
				generator.writeKey(item0.getKey());
				item0.getValue().serialize(generator, mapper);

			}
			generator.writeEnd();

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link IndexingStats}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<IndexingStats> {
		private Long indexCurrent;

		private Long deleteCurrent;

		@Nullable
		private String deleteTime;

		private Long deleteTimeInMillis;

		private Long deleteTotal;

		private Boolean isThrottled;

		private Long noopUpdateTotal;

		@Nullable
		private String throttleTime;

		private Long throttleTimeInMillis;

		@Nullable
		private String indexTime;

		private Long indexTimeInMillis;

		private Long indexTotal;

		private Long indexFailed;

		@Nullable
		private Map<String, IndexingStats> types;

		/**
		 * Required - API name: {@code index_current}
		 */
		public final Builder indexCurrent(long value) {
			this.indexCurrent = value;
			return this;
		}

		/**
		 * Required - API name: {@code delete_current}
		 */
		public final Builder deleteCurrent(long value) {
			this.deleteCurrent = value;
			return this;
		}

		/**
		 * API name: {@code delete_time}
		 */
		public final Builder deleteTime(@Nullable String value) {
			this.deleteTime = value;
			return this;
		}

		/**
		 * Required - API name: {@code delete_time_in_millis}
		 */
		public final Builder deleteTimeInMillis(long value) {
			this.deleteTimeInMillis = value;
			return this;
		}

		/**
		 * Required - API name: {@code delete_total}
		 */
		public final Builder deleteTotal(long value) {
			this.deleteTotal = value;
			return this;
		}

		/**
		 * Required - API name: {@code is_throttled}
		 */
		public final Builder isThrottled(boolean value) {
			this.isThrottled = value;
			return this;
		}

		/**
		 * Required - API name: {@code noop_update_total}
		 */
		public final Builder noopUpdateTotal(long value) {
			this.noopUpdateTotal = value;
			return this;
		}

		/**
		 * API name: {@code throttle_time}
		 */
		public final Builder throttleTime(@Nullable String value) {
			this.throttleTime = value;
			return this;
		}

		/**
		 * Required - API name: {@code throttle_time_in_millis}
		 */
		public final Builder throttleTimeInMillis(long value) {
			this.throttleTimeInMillis = value;
			return this;
		}

		/**
		 * API name: {@code index_time}
		 */
		public final Builder indexTime(@Nullable String value) {
			this.indexTime = value;
			return this;
		}

		/**
		 * Required - API name: {@code index_time_in_millis}
		 */
		public final Builder indexTimeInMillis(long value) {
			this.indexTimeInMillis = value;
			return this;
		}

		/**
		 * Required - API name: {@code index_total}
		 */
		public final Builder indexTotal(long value) {
			this.indexTotal = value;
			return this;
		}

		/**
		 * Required - API name: {@code index_failed}
		 */
		public final Builder indexFailed(long value) {
			this.indexFailed = value;
			return this;
		}

		/**
		 * API name: {@code types}
		 * <p>
		 * Adds all entries of <code>map</code> to <code>types</code>.
		 */
		public final Builder types(Map<String, IndexingStats> map) {
			this.types = _mapPutAll(this.types, map);
			return this;
		}

		/**
		 * API name: {@code types}
		 * <p>
		 * Adds an entry to <code>types</code>.
		 */
		public final Builder types(String key, IndexingStats value) {
			this.types = _mapPut(this.types, key, value);
			return this;
		}

		/**
		 * API name: {@code types}
		 * <p>
		 * Adds an entry to <code>types</code> using a builder lambda.
		 */
		public final Builder types(String key, Function<IndexingStats.Builder, ObjectBuilder<IndexingStats>> fn) {
			return types(key, fn.apply(new IndexingStats.Builder()).build());
		}

		/**
		 * Builds a {@link IndexingStats}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public IndexingStats build() {
			_checkSingleUse();

			return new IndexingStats(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link IndexingStats}
	 */
	public static final JsonpDeserializer<IndexingStats> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			IndexingStats::setupIndexingStatsDeserializer);

	protected static void setupIndexingStatsDeserializer(ObjectDeserializer<IndexingStats.Builder> op) {

		op.add(Builder::indexCurrent, JsonpDeserializer.longDeserializer(), "index_current");
		op.add(Builder::deleteCurrent, JsonpDeserializer.longDeserializer(), "delete_current");
		op.add(Builder::deleteTime, JsonpDeserializer.stringDeserializer(), "delete_time");
		op.add(Builder::deleteTimeInMillis, JsonpDeserializer.longDeserializer(), "delete_time_in_millis");
		op.add(Builder::deleteTotal, JsonpDeserializer.longDeserializer(), "delete_total");
		op.add(Builder::isThrottled, JsonpDeserializer.booleanDeserializer(), "is_throttled");
		op.add(Builder::noopUpdateTotal, JsonpDeserializer.longDeserializer(), "noop_update_total");
		op.add(Builder::throttleTime, JsonpDeserializer.stringDeserializer(), "throttle_time");
		op.add(Builder::throttleTimeInMillis, JsonpDeserializer.longDeserializer(), "throttle_time_in_millis");
		op.add(Builder::indexTime, JsonpDeserializer.stringDeserializer(), "index_time");
		op.add(Builder::indexTimeInMillis, JsonpDeserializer.longDeserializer(), "index_time_in_millis");
		op.add(Builder::indexTotal, JsonpDeserializer.longDeserializer(), "index_total");
		op.add(Builder::indexFailed, JsonpDeserializer.longDeserializer(), "index_failed");
		op.add(Builder::types, JsonpDeserializer.stringMapDeserializer(IndexingStats._DESERIALIZER), "types");

	}

}
