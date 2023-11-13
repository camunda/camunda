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

package org.opensearch.client.opensearch.cluster.stats;

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

// typedef: cluster.stats.ClusterIndicesShardsIndex


@JsonpDeserializable
public class ClusterIndicesShardsIndex implements JsonpSerializable {
	private final ClusterShardMetrics primaries;

	private final ClusterShardMetrics replication;

	private final ClusterShardMetrics shards;

	// ---------------------------------------------------------------------------------------------

	private ClusterIndicesShardsIndex(Builder builder) {

		this.primaries = ApiTypeHelper.requireNonNull(builder.primaries, this, "primaries");
		this.replication = ApiTypeHelper.requireNonNull(builder.replication, this, "replication");
		this.shards = ApiTypeHelper.requireNonNull(builder.shards, this, "shards");

	}

	public static ClusterIndicesShardsIndex of(Function<Builder, ObjectBuilder<ClusterIndicesShardsIndex>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - Contains statistics about the number of primary shards assigned to
	 * selected nodes.
	 * <p>
	 * API name: {@code primaries}
	 */
	public final ClusterShardMetrics primaries() {
		return this.primaries;
	}

	/**
	 * Required - Contains statistics about the number of replication shards
	 * assigned to selected nodes.
	 * <p>
	 * API name: {@code replication}
	 */
	public final ClusterShardMetrics replication() {
		return this.replication;
	}

	/**
	 * Required - Contains statistics about the number of shards assigned to
	 * selected nodes.
	 * <p>
	 * API name: {@code shards}
	 */
	public final ClusterShardMetrics shards() {
		return this.shards;
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

		generator.writeKey("replication");
		this.replication.serialize(generator, mapper);

		generator.writeKey("shards");
		this.shards.serialize(generator, mapper);

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link ClusterIndicesShardsIndex}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<ClusterIndicesShardsIndex> {
		private ClusterShardMetrics primaries;

		private ClusterShardMetrics replication;

		private ClusterShardMetrics shards;

		/**
		 * Required - Contains statistics about the number of primary shards assigned to
		 * selected nodes.
		 * <p>
		 * API name: {@code primaries}
		 */
		public final Builder primaries(ClusterShardMetrics value) {
			this.primaries = value;
			return this;
		}

		/**
		 * Required - Contains statistics about the number of primary shards assigned to
		 * selected nodes.
		 * <p>
		 * API name: {@code primaries}
		 */
		public final Builder primaries(Function<ClusterShardMetrics.Builder, ObjectBuilder<ClusterShardMetrics>> fn) {
			return this.primaries(fn.apply(new ClusterShardMetrics.Builder()).build());
		}

		/**
		 * Required - Contains statistics about the number of replication shards
		 * assigned to selected nodes.
		 * <p>
		 * API name: {@code replication}
		 */
		public final Builder replication(ClusterShardMetrics value) {
			this.replication = value;
			return this;
		}

		/**
		 * Required - Contains statistics about the number of replication shards
		 * assigned to selected nodes.
		 * <p>
		 * API name: {@code replication}
		 */
		public final Builder replication(Function<ClusterShardMetrics.Builder, ObjectBuilder<ClusterShardMetrics>> fn) {
			return this.replication(fn.apply(new ClusterShardMetrics.Builder()).build());
		}

		/**
		 * Required - Contains statistics about the number of shards assigned to
		 * selected nodes.
		 * <p>
		 * API name: {@code shards}
		 */
		public final Builder shards(ClusterShardMetrics value) {
			this.shards = value;
			return this;
		}

		/**
		 * Required - Contains statistics about the number of shards assigned to
		 * selected nodes.
		 * <p>
		 * API name: {@code shards}
		 */
		public final Builder shards(Function<ClusterShardMetrics.Builder, ObjectBuilder<ClusterShardMetrics>> fn) {
			return this.shards(fn.apply(new ClusterShardMetrics.Builder()).build());
		}

		/**
		 * Builds a {@link ClusterIndicesShardsIndex}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public ClusterIndicesShardsIndex build() {
			_checkSingleUse();

			return new ClusterIndicesShardsIndex(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link ClusterIndicesShardsIndex}
	 */
	public static final JsonpDeserializer<ClusterIndicesShardsIndex> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, ClusterIndicesShardsIndex::setupClusterIndicesShardsIndexDeserializer);

	protected static void setupClusterIndicesShardsIndexDeserializer(
			ObjectDeserializer<ClusterIndicesShardsIndex.Builder> op) {

		op.add(Builder::primaries, ClusterShardMetrics._DESERIALIZER, "primaries");
		op.add(Builder::replication, ClusterShardMetrics._DESERIALIZER, "replication");
		op.add(Builder::shards, ClusterShardMetrics._DESERIALIZER, "shards");

	}

}
