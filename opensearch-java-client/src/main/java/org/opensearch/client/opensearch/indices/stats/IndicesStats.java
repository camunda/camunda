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

package org.opensearch.client.opensearch.indices.stats;

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
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nullable;

// typedef: indices.stats.IndicesStats


@JsonpDeserializable
public class IndicesStats implements JsonpSerializable {
	private final IndexStats primaries;

	private final Map<String, List<ShardStats>> shards;

	private final IndexStats total;

	@Nullable
	private final String uuid;

	// ---------------------------------------------------------------------------------------------

	private IndicesStats(Builder builder) {

		this.primaries = ApiTypeHelper.requireNonNull(builder.primaries, this, "primaries");
		this.shards = ApiTypeHelper.unmodifiable(builder.shards);
		this.total = ApiTypeHelper.requireNonNull(builder.total, this, "total");
		this.uuid = builder.uuid;

	}

	public static IndicesStats of(Function<Builder, ObjectBuilder<IndicesStats>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code primaries}
	 */
	public final IndexStats primaries() {
		return this.primaries;
	}

	/**
	 * API name: {@code shards}
	 */
	public final Map<String, List<ShardStats>> shards() {
		return this.shards;
	}

	/**
	 * Required - API name: {@code total}
	 */
	public final IndexStats total() {
		return this.total;
	}

	/**
	 * API name: {@code uuid}
	 */
	@Nullable
	public final String uuid() {
		return this.uuid;
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

		generator.writeKey("primaries");
		this.primaries.serialize(generator, mapper);

		if (ApiTypeHelper.isDefined(this.shards)) {
			generator.writeKey("shards");
			generator.writeStartObject();
			for (Map.Entry<String, List<ShardStats>> item0 : this.shards.entrySet()) {
				generator.writeKey(item0.getKey());
				generator.writeStartArray();
				if (item0.getValue() != null) {
					for (ShardStats item1 : item0.getValue()) {
						item1.serialize(generator, mapper);

					}
				}
				generator.writeEnd();

			}
			generator.writeEnd();

		}
		generator.writeKey("total");
		this.total.serialize(generator, mapper);

		if (this.uuid != null) {
			generator.writeKey("uuid");
			generator.write(this.uuid);

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link IndicesStats}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<IndicesStats> {
		private IndexStats primaries;

		@Nullable
		private Map<String, List<ShardStats>> shards;

		private IndexStats total;

		@Nullable
		private String uuid;

		/**
		 * Required - API name: {@code primaries}
		 */
		public final Builder primaries(IndexStats value) {
			this.primaries = value;
			return this;
		}

		/**
		 * Required - API name: {@code primaries}
		 */
		public final Builder primaries(Function<IndexStats.Builder, ObjectBuilder<IndexStats>> fn) {
			return this.primaries(fn.apply(new IndexStats.Builder()).build());
		}

		/**
		 * API name: {@code shards}
		 * <p>
		 * Adds all entries of <code>map</code> to <code>shards</code>.
		 */
		public final Builder shards(Map<String, List<ShardStats>> map) {
			this.shards = _mapPutAll(this.shards, map);
			return this;
		}

		/**
		 * API name: {@code shards}
		 * <p>
		 * Adds an entry to <code>shards</code>.
		 */
		public final Builder shards(String key, List<ShardStats> value) {
			this.shards = _mapPut(this.shards, key, value);
			return this;
		}

		/**
		 * Required - API name: {@code total}
		 */
		public final Builder total(IndexStats value) {
			this.total = value;
			return this;
		}

		/**
		 * Required - API name: {@code total}
		 */
		public final Builder total(Function<IndexStats.Builder, ObjectBuilder<IndexStats>> fn) {
			return this.total(fn.apply(new IndexStats.Builder()).build());
		}

		/**
		 * API name: {@code uuid}
		 */
		public final Builder uuid(@Nullable String value) {
			this.uuid = value;
			return this;
		}

		/**
		 * Builds a {@link IndicesStats}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public IndicesStats build() {
			_checkSingleUse();

			return new IndicesStats(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link IndicesStats}
	 */
	public static final JsonpDeserializer<IndicesStats> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			IndicesStats::setupIndicesStatsDeserializer);

	protected static void setupIndicesStatsDeserializer(ObjectDeserializer<IndicesStats.Builder> op) {

		op.add(Builder::primaries, IndexStats._DESERIALIZER, "primaries");
		op.add(Builder::shards,
				JsonpDeserializer.stringMapDeserializer(JsonpDeserializer.arrayDeserializer(ShardStats._DESERIALIZER)),
				"shards");
		op.add(Builder::total, IndexStats._DESERIALIZER, "total");
		op.add(Builder::uuid, JsonpDeserializer.stringDeserializer(), "uuid");

	}

}
