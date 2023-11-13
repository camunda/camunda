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

package org.opensearch.client.opensearch.snapshot;

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

// typedef: snapshot._types.Status


@JsonpDeserializable
public class Status implements JsonpSerializable {
	private final boolean includeGlobalState;

	private final Map<String, SnapshotIndexStats> indices;

	private final String repository;

	private final ShardsStats shardsStats;

	private final String snapshot;

	private final String state;

	private final SnapshotStats stats;

	private final String uuid;

	// ---------------------------------------------------------------------------------------------

	private Status(Builder builder) {

		this.includeGlobalState = ApiTypeHelper.requireNonNull(builder.includeGlobalState, this, "includeGlobalState");
		this.indices = ApiTypeHelper.unmodifiableRequired(builder.indices, this, "indices");
		this.repository = ApiTypeHelper.requireNonNull(builder.repository, this, "repository");
		this.shardsStats = ApiTypeHelper.requireNonNull(builder.shardsStats, this, "shardsStats");
		this.snapshot = ApiTypeHelper.requireNonNull(builder.snapshot, this, "snapshot");
		this.state = ApiTypeHelper.requireNonNull(builder.state, this, "state");
		this.stats = ApiTypeHelper.requireNonNull(builder.stats, this, "stats");
		this.uuid = ApiTypeHelper.requireNonNull(builder.uuid, this, "uuid");

	}

	public static Status of(Function<Builder, ObjectBuilder<Status>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code include_global_state}
	 */
	public final boolean includeGlobalState() {
		return this.includeGlobalState;
	}

	/**
	 * Required - API name: {@code indices}
	 */
	public final Map<String, SnapshotIndexStats> indices() {
		return this.indices;
	}

	/**
	 * Required - API name: {@code repository}
	 */
	public final String repository() {
		return this.repository;
	}

	/**
	 * Required - API name: {@code shards_stats}
	 */
	public final ShardsStats shardsStats() {
		return this.shardsStats;
	}

	/**
	 * Required - API name: {@code snapshot}
	 */
	public final String snapshot() {
		return this.snapshot;
	}

	/**
	 * Required - API name: {@code state}
	 */
	public final String state() {
		return this.state;
	}

	/**
	 * Required - API name: {@code stats}
	 */
	public final SnapshotStats stats() {
		return this.stats;
	}

	/**
	 * Required - API name: {@code uuid}
	 */
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

		generator.writeKey("include_global_state");
		generator.write(this.includeGlobalState);

		if (ApiTypeHelper.isDefined(this.indices)) {
			generator.writeKey("indices");
			generator.writeStartObject();
			for (Map.Entry<String, SnapshotIndexStats> item0 : this.indices.entrySet()) {
				generator.writeKey(item0.getKey());
				item0.getValue().serialize(generator, mapper);

			}
			generator.writeEnd();

		}
		generator.writeKey("repository");
		generator.write(this.repository);

		generator.writeKey("shards_stats");
		this.shardsStats.serialize(generator, mapper);

		generator.writeKey("snapshot");
		generator.write(this.snapshot);

		generator.writeKey("state");
		generator.write(this.state);

		generator.writeKey("stats");
		this.stats.serialize(generator, mapper);

		generator.writeKey("uuid");
		generator.write(this.uuid);

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link Status}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<Status> {
		private Boolean includeGlobalState;

		private Map<String, SnapshotIndexStats> indices;

		private String repository;

		private ShardsStats shardsStats;

		private String snapshot;

		private String state;

		private SnapshotStats stats;

		private String uuid;

		/**
		 * Required - API name: {@code include_global_state}
		 */
		public final Builder includeGlobalState(boolean value) {
			this.includeGlobalState = value;
			return this;
		}

		/**
		 * Required - API name: {@code indices}
		 * <p>
		 * Adds all entries of <code>map</code> to <code>indices</code>.
		 */
		public final Builder indices(Map<String, SnapshotIndexStats> map) {
			this.indices = _mapPutAll(this.indices, map);
			return this;
		}

		/**
		 * Required - API name: {@code indices}
		 * <p>
		 * Adds an entry to <code>indices</code>.
		 */
		public final Builder indices(String key, SnapshotIndexStats value) {
			this.indices = _mapPut(this.indices, key, value);
			return this;
		}

		/**
		 * Required - API name: {@code indices}
		 * <p>
		 * Adds an entry to <code>indices</code> using a builder lambda.
		 */
		public final Builder indices(String key,
				Function<SnapshotIndexStats.Builder, ObjectBuilder<SnapshotIndexStats>> fn) {
			return indices(key, fn.apply(new SnapshotIndexStats.Builder()).build());
		}

		/**
		 * Required - API name: {@code repository}
		 */
		public final Builder repository(String value) {
			this.repository = value;
			return this;
		}

		/**
		 * Required - API name: {@code shards_stats}
		 */
		public final Builder shardsStats(ShardsStats value) {
			this.shardsStats = value;
			return this;
		}

		/**
		 * Required - API name: {@code shards_stats}
		 */
		public final Builder shardsStats(Function<ShardsStats.Builder, ObjectBuilder<ShardsStats>> fn) {
			return this.shardsStats(fn.apply(new ShardsStats.Builder()).build());
		}

		/**
		 * Required - API name: {@code snapshot}
		 */
		public final Builder snapshot(String value) {
			this.snapshot = value;
			return this;
		}

		/**
		 * Required - API name: {@code state}
		 */
		public final Builder state(String value) {
			this.state = value;
			return this;
		}

		/**
		 * Required - API name: {@code stats}
		 */
		public final Builder stats(SnapshotStats value) {
			this.stats = value;
			return this;
		}

		/**
		 * Required - API name: {@code stats}
		 */
		public final Builder stats(Function<SnapshotStats.Builder, ObjectBuilder<SnapshotStats>> fn) {
			return this.stats(fn.apply(new SnapshotStats.Builder()).build());
		}

		/**
		 * Required - API name: {@code uuid}
		 */
		public final Builder uuid(String value) {
			this.uuid = value;
			return this;
		}

		/**
		 * Builds a {@link Status}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public Status build() {
			_checkSingleUse();

			return new Status(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link Status}
	 */
	public static final JsonpDeserializer<Status> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			Status::setupStatusDeserializer);

	protected static void setupStatusDeserializer(ObjectDeserializer<Status.Builder> op) {

		op.add(Builder::includeGlobalState, JsonpDeserializer.booleanDeserializer(), "include_global_state");
		op.add(Builder::indices, JsonpDeserializer.stringMapDeserializer(SnapshotIndexStats._DESERIALIZER), "indices");
		op.add(Builder::repository, JsonpDeserializer.stringDeserializer(), "repository");
		op.add(Builder::shardsStats, ShardsStats._DESERIALIZER, "shards_stats");
		op.add(Builder::snapshot, JsonpDeserializer.stringDeserializer(), "snapshot");
		op.add(Builder::state, JsonpDeserializer.stringDeserializer(), "state");
		op.add(Builder::stats, SnapshotStats._DESERIALIZER, "stats");
		op.add(Builder::uuid, JsonpDeserializer.stringDeserializer(), "uuid");

	}

}
