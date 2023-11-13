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

package org.opensearch.client.opensearch.nodes.info;

import org.opensearch.client.opensearch.indices.IndexRouting;
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
import java.util.function.Function;
import javax.annotation.Nullable;

// typedef: nodes.info.NodeInfoSettingsCluster

@JsonpDeserializable
public class NodeInfoSettingsCluster implements JsonpSerializable {
	private final String name;

	@Nullable
	private final IndexRouting routing;

	@Nullable
	private final NodeInfoSettingsClusterElection election;

	@Deprecated
	@Nullable
	private final List<String> initialMasterNodes;

	@Nullable
	private final List<String> initialClusterManagerNodes;

	// ---------------------------------------------------------------------------------------------

	private NodeInfoSettingsCluster(Builder builder) {

		this.name = ApiTypeHelper.requireNonNull(builder.name, this, "name");
		this.routing = builder.routing;
		this.election = builder.election;
		this.initialMasterNodes = builder.initialMasterNodes;
		this.initialClusterManagerNodes = builder.initialClusterManagerNodes;

	}

	public static NodeInfoSettingsCluster of(Function<Builder, ObjectBuilder<NodeInfoSettingsCluster>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code name}
	 */
	public final String name() {
		return this.name;
	}

	/**
	 * API name: {@code routing}
	 */
	@Nullable
	public final IndexRouting routing() {
		return this.routing;
	}

	/**
	 * API name: {@code election}
	 */
	@Nullable
	public final NodeInfoSettingsClusterElection election() {
		return this.election;
	}

	/**
	 * API name: {@code initial_master_nodes}
	 */
	@Deprecated
	@Nullable
	public final List<String> initialMasterNodes() {
		return this.initialMasterNodes;
	}

	/**
	 * API name: {@code initial_cluster_manager_nodes}
	 */
	@Nullable
	public final List<String> initialClusterManagerNodes() {
		return this.initialClusterManagerNodes;
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

		generator.writeKey("name");
		generator.write(this.name);

		if (this.routing != null) {
			generator.writeKey("routing");
			this.routing.serialize(generator, mapper);

		}
		generator.writeKey("election");
		this.election.serialize(generator, mapper);

		if (this.initialMasterNodes != null) {
		    generator.writeStartArray("initial_master_nodes");
		    this.initialMasterNodes.forEach(generator::write);
		    generator.writeEnd();
		}

		if (this.initialClusterManagerNodes != null) {
			generator.writeStartArray("initial_cluster_manager_nodes");
			this.initialClusterManagerNodes.forEach(generator::write);
			generator.writeEnd();
		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link NodeInfoSettingsCluster}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<NodeInfoSettingsCluster> {
		private String name;

		@Nullable
		private IndexRouting routing;

		@Nullable
		private NodeInfoSettingsClusterElection election;

		@Deprecated
		@Nullable
		private List<String> initialMasterNodes;

		@Nullable
		private List<String> initialClusterManagerNodes;

		/**
		 * Required - API name: {@code name}
		 */
		public final Builder name(String value) {
			this.name = value;
			return this;
		}

		/**
		 * API name: {@code routing}
		 */
		public final Builder routing(@Nullable IndexRouting value) {
			this.routing = value;
			return this;
		}

		/**
		 * API name: {@code routing}
		 */
		public final Builder routing(Function<IndexRouting.Builder, ObjectBuilder<IndexRouting>> fn) {
			return this.routing(fn.apply(new IndexRouting.Builder()).build());
		}

		/**
		 * API name: {@code election}
		 */
		public final Builder election(NodeInfoSettingsClusterElection value) {
			this.election = value;
			return this;
		}

		/**
		 * API name: {@code election}
		 */
		public final Builder election(
				Function<NodeInfoSettingsClusterElection.Builder, ObjectBuilder<NodeInfoSettingsClusterElection>> fn) {
			return this.election(fn.apply(new NodeInfoSettingsClusterElection.Builder()).build());
		}

		/**
		 * API name: {@code initial_master_nodes}
		 */
		@Deprecated
		public final Builder initialMasterNodes(@Nullable List<String> value) {
			this.initialMasterNodes = value;
			return this;
		}

		/**
		 * API name: {@code initial_cluster_manager_nodes}
		 */
		public final Builder initialClusterManagerNodes(@Nullable List<String> value) {
			this.initialClusterManagerNodes = value;
			return this;
		}

		/**
		 * Builds a {@link NodeInfoSettingsCluster}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public NodeInfoSettingsCluster build() {
			_checkSingleUse();

			return new NodeInfoSettingsCluster(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link NodeInfoSettingsCluster}
	 */
	public static final JsonpDeserializer<NodeInfoSettingsCluster> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, NodeInfoSettingsCluster::setupNodeInfoSettingsClusterDeserializer);

	protected static void setupNodeInfoSettingsClusterDeserializer(
			ObjectDeserializer<NodeInfoSettingsCluster.Builder> op) {

		op.add(Builder::name, JsonpDeserializer.stringDeserializer(), "name");
		op.add(Builder::routing, IndexRouting._DESERIALIZER, "routing");
		op.add(Builder::election, NodeInfoSettingsClusterElection._DESERIALIZER, "election");
		op.add(Builder::initialMasterNodes, JsonpDeserializer.arrayDeserializer(JsonpDeserializer.stringDeserializer()), "initial_master_nodes");
		op.add(Builder::initialClusterManagerNodes, JsonpDeserializer.arrayDeserializer(JsonpDeserializer.stringDeserializer()), "initial_cluster_manager_nodes");

	}

}
