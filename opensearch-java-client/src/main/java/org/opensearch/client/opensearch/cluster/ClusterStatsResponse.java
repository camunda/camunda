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

package org.opensearch.client.opensearch.cluster;

import org.opensearch.client.opensearch._types.HealthStatus;
import org.opensearch.client.opensearch.cluster.stats.ClusterIndices;
import org.opensearch.client.opensearch.cluster.stats.ClusterNodes;
import org.opensearch.client.opensearch.nodes.NodesResponseBase;
import org.opensearch.client.json.JsonpDeserializable;
import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.ObjectBuilderDeserializer;
import org.opensearch.client.json.ObjectDeserializer;
import org.opensearch.client.util.ApiTypeHelper;
import org.opensearch.client.util.ObjectBuilder;
import jakarta.json.stream.JsonGenerator;

import java.util.function.Function;

// typedef: cluster.stats.Response

@JsonpDeserializable
public class ClusterStatsResponse extends NodesResponseBase {
	private final String clusterName;

	private final String clusterUuid;

	private final ClusterIndices indices;

	private final ClusterNodes nodes;

	private final HealthStatus status;

	private final long timestamp;

	// ---------------------------------------------------------------------------------------------

	private ClusterStatsResponse(Builder builder) {
		super(builder);

		this.clusterName = ApiTypeHelper.requireNonNull(builder.clusterName, this, "clusterName");
		this.clusterUuid = ApiTypeHelper.requireNonNull(builder.clusterUuid, this, "clusterUuid");
		this.indices = ApiTypeHelper.requireNonNull(builder.indices, this, "indices");
		this.nodes = ApiTypeHelper.requireNonNull(builder.nodes, this, "nodes");
		this.status = ApiTypeHelper.requireNonNull(builder.status, this, "status");
		this.timestamp = ApiTypeHelper.requireNonNull(builder.timestamp, this, "timestamp");

	}

	public static ClusterStatsResponse of(Function<Builder, ObjectBuilder<ClusterStatsResponse>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - Name of the cluster, based on the Cluster name setting setting.
	 * <p>
	 * API name: {@code cluster_name}
	 */
	public final String clusterName() {
		return this.clusterName;
	}

	/**
	 * Required - Unique identifier for the cluster.
	 * <p>
	 * API name: {@code cluster_uuid}
	 */
	public final String clusterUuid() {
		return this.clusterUuid;
	}

	/**
	 * Required - Contains statistics about indices with shards assigned to selected
	 * nodes.
	 * <p>
	 * API name: {@code indices}
	 */
	public final ClusterIndices indices() {
		return this.indices;
	}

	/**
	 * Required - Contains statistics about nodes selected by the request's node
	 * filters.
	 * <p>
	 * API name: {@code nodes}
	 */
	public final ClusterNodes nodes() {
		return this.nodes;
	}

	/**
	 * Required - Health status of the cluster, based on the state of its primary
	 * and replica shards.
	 * <p>
	 * API name: {@code status}
	 */
	public final HealthStatus status() {
		return this.status;
	}

	/**
	 * Required - Unix timestamp, in milliseconds, of the last time the cluster
	 * statistics were refreshed.
	 * <p>
	 * API name: {@code timestamp}
	 */
	public final long timestamp() {
		return this.timestamp;
	}

	protected void serializeInternal(JsonGenerator generator, JsonpMapper mapper) {

		super.serializeInternal(generator, mapper);
		generator.writeKey("cluster_name");
		generator.write(this.clusterName);

		generator.writeKey("cluster_uuid");
		generator.write(this.clusterUuid);

		generator.writeKey("indices");
		this.indices.serialize(generator, mapper);

		generator.writeKey("nodes");
		this.nodes.serialize(generator, mapper);

		generator.writeKey("status");
		this.status.serialize(generator, mapper);
		generator.writeKey("timestamp");
		generator.write(this.timestamp);

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link ClusterStatsResponse}.
	 */

	public static class Builder extends NodesResponseBase.AbstractBuilder<Builder>
			implements
				ObjectBuilder<ClusterStatsResponse> {
		private String clusterName;

		private String clusterUuid;

		private ClusterIndices indices;

		private ClusterNodes nodes;

		private HealthStatus status;

		private Long timestamp;

		/**
		 * Required - Name of the cluster, based on the Cluster name setting setting.
		 * <p>
		 * API name: {@code cluster_name}
		 */
		public final Builder clusterName(String value) {
			this.clusterName = value;
			return this;
		}

		/**
		 * Required - Unique identifier for the cluster.
		 * <p>
		 * API name: {@code cluster_uuid}
		 */
		public final Builder clusterUuid(String value) {
			this.clusterUuid = value;
			return this;
		}

		/**
		 * Required - Contains statistics about indices with shards assigned to selected
		 * nodes.
		 * <p>
		 * API name: {@code indices}
		 */
		public final Builder indices(ClusterIndices value) {
			this.indices = value;
			return this;
		}

		/**
		 * Required - Contains statistics about indices with shards assigned to selected
		 * nodes.
		 * <p>
		 * API name: {@code indices}
		 */
		public final Builder indices(Function<ClusterIndices.Builder, ObjectBuilder<ClusterIndices>> fn) {
			return this.indices(fn.apply(new ClusterIndices.Builder()).build());
		}

		/**
		 * Required - Contains statistics about nodes selected by the request's node
		 * filters.
		 * <p>
		 * API name: {@code nodes}
		 */
		public final Builder nodes(ClusterNodes value) {
			this.nodes = value;
			return this;
		}

		/**
		 * Required - Contains statistics about nodes selected by the request's node
		 * filters.
		 * <p>
		 * API name: {@code nodes}
		 */
		public final Builder nodes(Function<ClusterNodes.Builder, ObjectBuilder<ClusterNodes>> fn) {
			return this.nodes(fn.apply(new ClusterNodes.Builder()).build());
		}

		/**
		 * Required - Health status of the cluster, based on the state of its primary
		 * and replica shards.
		 * <p>
		 * API name: {@code status}
		 */
		public final Builder status(HealthStatus value) {
			this.status = value;
			return this;
		}

		/**
		 * Required - Unix timestamp, in milliseconds, of the last time the cluster
		 * statistics were refreshed.
		 * <p>
		 * API name: {@code timestamp}
		 */
		public final Builder timestamp(long value) {
			this.timestamp = value;
			return this;
		}

		@Override
		protected Builder self() {
			return this;
		}

		/**
		 * Builds a {@link ClusterStatsResponse}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public ClusterStatsResponse build() {
			_checkSingleUse();

			return new ClusterStatsResponse(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link ClusterStatsResponse}
	 */
	public static final JsonpDeserializer<ClusterStatsResponse> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, ClusterStatsResponse::setupClusterStatsResponseDeserializer);

	protected static void setupClusterStatsResponseDeserializer(ObjectDeserializer<ClusterStatsResponse.Builder> op) {
		NodesResponseBase.setupNodesResponseBaseDeserializer(op);
		op.add(Builder::clusterName, JsonpDeserializer.stringDeserializer(), "cluster_name");
		op.add(Builder::clusterUuid, JsonpDeserializer.stringDeserializer(), "cluster_uuid");
		op.add(Builder::indices, ClusterIndices._DESERIALIZER, "indices");
		op.add(Builder::nodes, ClusterNodes._DESERIALIZER, "nodes");
		op.add(Builder::status, HealthStatus._DESERIALIZER, "status");
		op.add(Builder::timestamp, JsonpDeserializer.longDeserializer(), "timestamp");

	}

}
