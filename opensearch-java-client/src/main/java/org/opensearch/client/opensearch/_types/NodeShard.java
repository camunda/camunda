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

import org.opensearch.client.opensearch.cluster.allocation_explain.UnassignedInformation;
import org.opensearch.client.opensearch.indices.stats.ShardRoutingState;
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

// typedef: _types.NodeShard

@JsonpDeserializable
public class NodeShard implements JsonpSerializable {
	private final ShardRoutingState state;

	private final boolean primary;

	@Nullable
	private final String node;

	private final int shard;

	private final String index;

	private final Map<String, String> allocationId;

	private final Map<String, String> recoverySource;

	@Nullable
	private final UnassignedInformation unassignedInfo;

	// ---------------------------------------------------------------------------------------------

	private NodeShard(Builder builder) {

		this.state = ApiTypeHelper.requireNonNull(builder.state, this, "state");
		this.primary = ApiTypeHelper.requireNonNull(builder.primary, this, "primary");
		this.node = builder.node;
		this.shard = ApiTypeHelper.requireNonNull(builder.shard, this, "shard");
		this.index = ApiTypeHelper.requireNonNull(builder.index, this, "index");
		this.allocationId = ApiTypeHelper.unmodifiable(builder.allocationId);
		this.recoverySource = ApiTypeHelper.unmodifiable(builder.recoverySource);
		this.unassignedInfo = builder.unassignedInfo;

	}

	public static NodeShard of(Function<Builder, ObjectBuilder<NodeShard>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code state}
	 */
	public final ShardRoutingState state() {
		return this.state;
	}

	/**
	 * Required - API name: {@code primary}
	 */
	public final boolean primary() {
		return this.primary;
	}

	/**
	 * API name: {@code node}
	 */
	@Nullable
	public final String node() {
		return this.node;
	}

	/**
	 * Required - API name: {@code shard}
	 */
	public final int shard() {
		return this.shard;
	}

	/**
	 * Required - API name: {@code index}
	 */
	public final String index() {
		return this.index;
	}

	/**
	 * API name: {@code allocation_id}
	 */
	public final Map<String, String> allocationId() {
		return this.allocationId;
	}

	/**
	 * API name: {@code recovery_source}
	 */
	public final Map<String, String> recoverySource() {
		return this.recoverySource;
	}

	/**
	 * API name: {@code unassigned_info}
	 */
	@Nullable
	public final UnassignedInformation unassignedInfo() {
		return this.unassignedInfo;
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

		generator.writeKey("state");
		this.state.serialize(generator, mapper);
		generator.writeKey("primary");
		generator.write(this.primary);

		if (this.node != null) {
			generator.writeKey("node");
			generator.write(this.node);

		}
		generator.writeKey("shard");
		generator.write(this.shard);

		generator.writeKey("index");
		generator.write(this.index);

		if (ApiTypeHelper.isDefined(this.allocationId)) {
			generator.writeKey("allocation_id");
			generator.writeStartObject();
			for (Map.Entry<String, String> item0 : this.allocationId.entrySet()) {
				generator.writeKey(item0.getKey());
				generator.write(item0.getValue());

			}
			generator.writeEnd();

		}
		if (ApiTypeHelper.isDefined(this.recoverySource)) {
			generator.writeKey("recovery_source");
			generator.writeStartObject();
			for (Map.Entry<String, String> item0 : this.recoverySource.entrySet()) {
				generator.writeKey(item0.getKey());
				generator.write(item0.getValue());

			}
			generator.writeEnd();

		}
		if (this.unassignedInfo != null) {
			generator.writeKey("unassigned_info");
			this.unassignedInfo.serialize(generator, mapper);

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link NodeShard}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<NodeShard> {
		private ShardRoutingState state;

		private Boolean primary;

		@Nullable
		private String node;

		private Integer shard;

		private String index;

		@Nullable
		private Map<String, String> allocationId;

		@Nullable
		private Map<String, String> recoverySource;

		@Nullable
		private UnassignedInformation unassignedInfo;

		/**
		 * Required - API name: {@code state}
		 */
		public final Builder state(ShardRoutingState value) {
			this.state = value;
			return this;
		}

		/**
		 * Required - API name: {@code primary}
		 */
		public final Builder primary(boolean value) {
			this.primary = value;
			return this;
		}

		/**
		 * API name: {@code node}
		 */
		public final Builder node(@Nullable String value) {
			this.node = value;
			return this;
		}

		/**
		 * Required - API name: {@code shard}
		 */
		public final Builder shard(int value) {
			this.shard = value;
			return this;
		}

		/**
		 * Required - API name: {@code index}
		 */
		public final Builder index(String value) {
			this.index = value;
			return this;
		}

		/**
		 * API name: {@code allocation_id}
		 * <p>
		 * Adds all entries of <code>map</code> to <code>allocationId</code>.
		 */
		public final Builder allocationId(Map<String, String> map) {
			this.allocationId = _mapPutAll(this.allocationId, map);
			return this;
		}

		/**
		 * API name: {@code allocation_id}
		 * <p>
		 * Adds an entry to <code>allocationId</code>.
		 */
		public final Builder allocationId(String key, String value) {
			this.allocationId = _mapPut(this.allocationId, key, value);
			return this;
		}

		/**
		 * API name: {@code recovery_source}
		 * <p>
		 * Adds all entries of <code>map</code> to <code>recoverySource</code>.
		 */
		public final Builder recoverySource(Map<String, String> map) {
			this.recoverySource = _mapPutAll(this.recoverySource, map);
			return this;
		}

		/**
		 * API name: {@code recovery_source}
		 * <p>
		 * Adds an entry to <code>recoverySource</code>.
		 */
		public final Builder recoverySource(String key, String value) {
			this.recoverySource = _mapPut(this.recoverySource, key, value);
			return this;
		}

		/**
		 * API name: {@code unassigned_info}
		 */
		public final Builder unassignedInfo(@Nullable UnassignedInformation value) {
			this.unassignedInfo = value;
			return this;
		}

		/**
		 * API name: {@code unassigned_info}
		 */
		public final Builder unassignedInfo(
				Function<UnassignedInformation.Builder, ObjectBuilder<UnassignedInformation>> fn) {
			return this.unassignedInfo(fn.apply(new UnassignedInformation.Builder()).build());
		}

		/**
		 * Builds a {@link NodeShard}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public NodeShard build() {
			_checkSingleUse();

			return new NodeShard(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link NodeShard}
	 */
	public static final JsonpDeserializer<NodeShard> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			NodeShard::setupNodeShardDeserializer);

	protected static void setupNodeShardDeserializer(ObjectDeserializer<NodeShard.Builder> op) {

		op.add(Builder::state, ShardRoutingState._DESERIALIZER, "state");
		op.add(Builder::primary, JsonpDeserializer.booleanDeserializer(), "primary");
		op.add(Builder::node, JsonpDeserializer.stringDeserializer(), "node");
		op.add(Builder::shard, JsonpDeserializer.integerDeserializer(), "shard");
		op.add(Builder::index, JsonpDeserializer.stringDeserializer(), "index");
		op.add(Builder::allocationId, JsonpDeserializer.stringMapDeserializer(JsonpDeserializer.stringDeserializer()),
				"allocation_id");
		op.add(Builder::recoverySource, JsonpDeserializer.stringMapDeserializer(JsonpDeserializer.stringDeserializer()),
				"recovery_source");
		op.add(Builder::unassignedInfo, UnassignedInformation._DESERIALIZER, "unassigned_info");

	}

}
