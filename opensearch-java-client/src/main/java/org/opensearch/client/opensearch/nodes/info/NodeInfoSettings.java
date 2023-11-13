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

// typedef: nodes.info.NodeInfoSettings

@JsonpDeserializable
public class NodeInfoSettings implements JsonpSerializable {
	private final NodeInfoSettingsCluster cluster;

	private final NodeInfoSettingsNode node;

	private final NodeInfoPath path;

	@Nullable
	private final NodeInfoRepositories repositories;

	@Nullable
	private final NodeInfoDiscover discovery;

	@Nullable
	private final NodeInfoAction action;

	private final NodeInfoClient client;

	private final NodeInfoSettingsHttp http;

	@Nullable
	private final NodeInfoBootstrap bootstrap;

	private final NodeInfoSettingsTransport transport;

	@Nullable
	private final NodeInfoSettingsNetwork network;

	@Nullable
	private final NodeInfoScript script;

	@Nullable
	private final NodeInfoSearch search;

	@Nullable
	private final NodeInfoSettingsIngest ingest;

	// ---------------------------------------------------------------------------------------------

	private NodeInfoSettings(Builder builder) {

		this.cluster = ApiTypeHelper.requireNonNull(builder.cluster, this, "cluster");
		this.node = ApiTypeHelper.requireNonNull(builder.node, this, "node");
		this.path = ApiTypeHelper.requireNonNull(builder.path, this, "path");
		this.repositories = builder.repositories;
		this.discovery = builder.discovery;
		this.action = builder.action;
		this.client = ApiTypeHelper.requireNonNull(builder.client, this, "client");
		this.http = ApiTypeHelper.requireNonNull(builder.http, this, "http");
		this.bootstrap = builder.bootstrap;
		this.transport = ApiTypeHelper.requireNonNull(builder.transport, this, "transport");
		this.network = builder.network;
		this.script = builder.script;
		this.search = builder.search;
		this.ingest = builder.ingest;

	}

	public static NodeInfoSettings of(Function<Builder, ObjectBuilder<NodeInfoSettings>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code cluster}
	 */
	public final NodeInfoSettingsCluster cluster() {
		return this.cluster;
	}

	/**
	 * Required - API name: {@code node}
	 */
	public final NodeInfoSettingsNode node() {
		return this.node;
	}

	/**
	 * Required - API name: {@code path}
	 */
	public final NodeInfoPath path() {
		return this.path;
	}

	/**
	 * API name: {@code repositories}
	 */
	@Nullable
	public final NodeInfoRepositories repositories() {
		return this.repositories;
	}

	/**
	 * API name: {@code discovery}
	 */
	@Nullable
	public final NodeInfoDiscover discovery() {
		return this.discovery;
	}

	/**
	 * API name: {@code action}
	 */
	@Nullable
	public final NodeInfoAction action() {
		return this.action;
	}

	/**
	 * Required - API name: {@code client}
	 */
	public final NodeInfoClient client() {
		return this.client;
	}

	/**
	 * Required - API name: {@code http}
	 */
	public final NodeInfoSettingsHttp http() {
		return this.http;
	}

	/**
	 * API name: {@code bootstrap}
	 */
	@Nullable
	public final NodeInfoBootstrap bootstrap() {
		return this.bootstrap;
	}

	/**
	 * Required - API name: {@code transport}
	 */
	public final NodeInfoSettingsTransport transport() {
		return this.transport;
	}

	/**
	 * API name: {@code network}
	 */
	@Nullable
	public final NodeInfoSettingsNetwork network() {
		return this.network;
	}

	/**
	 * API name: {@code script}
	 */
	@Nullable
	public final NodeInfoScript script() {
		return this.script;
	}

	/**
	 * API name: {@code search}
	 */
	@Nullable
	public final NodeInfoSearch search() {
		return this.search;
	}

	/**
	 * API name: {@code ingest}
	 */
	@Nullable
	public final NodeInfoSettingsIngest ingest() {
		return this.ingest;
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

		generator.writeKey("cluster");
		this.cluster.serialize(generator, mapper);

		generator.writeKey("node");
		this.node.serialize(generator, mapper);

		generator.writeKey("path");
		this.path.serialize(generator, mapper);

		if (this.repositories != null) {
			generator.writeKey("repositories");
			this.repositories.serialize(generator, mapper);

		}
		if (this.discovery != null) {
			generator.writeKey("discovery");
			this.discovery.serialize(generator, mapper);

		}
		if (this.action != null) {
			generator.writeKey("action");
			this.action.serialize(generator, mapper);

		}
		generator.writeKey("client");
		this.client.serialize(generator, mapper);

		generator.writeKey("http");
		this.http.serialize(generator, mapper);

		if (this.bootstrap != null) {
			generator.writeKey("bootstrap");
			this.bootstrap.serialize(generator, mapper);

		}
		generator.writeKey("transport");
		this.transport.serialize(generator, mapper);

		if (this.network != null) {
			generator.writeKey("network");
			this.network.serialize(generator, mapper);

		}
		if (this.script != null) {
			generator.writeKey("script");
			this.script.serialize(generator, mapper);

		}
		if (this.search != null) {
			generator.writeKey("search");
			this.search.serialize(generator, mapper);

		}
		if (this.ingest != null) {
			generator.writeKey("ingest");
			this.ingest.serialize(generator, mapper);

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link NodeInfoSettings}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<NodeInfoSettings> {
		private NodeInfoSettingsCluster cluster;

		private NodeInfoSettingsNode node;

		private NodeInfoPath path;

		@Nullable
		private NodeInfoRepositories repositories;

		@Nullable
		private NodeInfoDiscover discovery;

		@Nullable
		private NodeInfoAction action;

		private NodeInfoClient client;

		private NodeInfoSettingsHttp http;

		@Nullable
		private NodeInfoBootstrap bootstrap;

		private NodeInfoSettingsTransport transport;

		@Nullable
		private NodeInfoSettingsNetwork network;

		@Nullable
		private NodeInfoScript script;

		@Nullable
		private NodeInfoSearch search;

		@Nullable
		private NodeInfoSettingsIngest ingest;

		/**
		 * Required - API name: {@code cluster}
		 */
		public final Builder cluster(NodeInfoSettingsCluster value) {
			this.cluster = value;
			return this;
		}

		/**
		 * Required - API name: {@code cluster}
		 */
		public final Builder cluster(
				Function<NodeInfoSettingsCluster.Builder, ObjectBuilder<NodeInfoSettingsCluster>> fn) {
			return this.cluster(fn.apply(new NodeInfoSettingsCluster.Builder()).build());
		}

		/**
		 * Required - API name: {@code node}
		 */
		public final Builder node(NodeInfoSettingsNode value) {
			this.node = value;
			return this;
		}

		/**
		 * Required - API name: {@code node}
		 */
		public final Builder node(Function<NodeInfoSettingsNode.Builder, ObjectBuilder<NodeInfoSettingsNode>> fn) {
			return this.node(fn.apply(new NodeInfoSettingsNode.Builder()).build());
		}

		/**
		 * Required - API name: {@code path}
		 */
		public final Builder path(NodeInfoPath value) {
			this.path = value;
			return this;
		}

		/**
		 * Required - API name: {@code path}
		 */
		public final Builder path(Function<NodeInfoPath.Builder, ObjectBuilder<NodeInfoPath>> fn) {
			return this.path(fn.apply(new NodeInfoPath.Builder()).build());
		}

		/**
		 * API name: {@code repositories}
		 */
		public final Builder repositories(@Nullable NodeInfoRepositories value) {
			this.repositories = value;
			return this;
		}

		/**
		 * API name: {@code repositories}
		 */
		public final Builder repositories(
				Function<NodeInfoRepositories.Builder, ObjectBuilder<NodeInfoRepositories>> fn) {
			return this.repositories(fn.apply(new NodeInfoRepositories.Builder()).build());
		}

		/**
		 * API name: {@code discovery}
		 */
		public final Builder discovery(@Nullable NodeInfoDiscover value) {
			this.discovery = value;
			return this;
		}

		/**
		 * API name: {@code discovery}
		 */
		public final Builder discovery(Function<NodeInfoDiscover.Builder, ObjectBuilder<NodeInfoDiscover>> fn) {
			return this.discovery(fn.apply(new NodeInfoDiscover.Builder()).build());
		}

		/**
		 * API name: {@code action}
		 */
		public final Builder action(@Nullable NodeInfoAction value) {
			this.action = value;
			return this;
		}

		/**
		 * API name: {@code action}
		 */
		public final Builder action(Function<NodeInfoAction.Builder, ObjectBuilder<NodeInfoAction>> fn) {
			return this.action(fn.apply(new NodeInfoAction.Builder()).build());
		}

		/**
		 * Required - API name: {@code client}
		 */
		public final Builder client(NodeInfoClient value) {
			this.client = value;
			return this;
		}

		/**
		 * Required - API name: {@code client}
		 */
		public final Builder client(Function<NodeInfoClient.Builder, ObjectBuilder<NodeInfoClient>> fn) {
			return this.client(fn.apply(new NodeInfoClient.Builder()).build());
		}

		/**
		 * Required - API name: {@code http}
		 */
		public final Builder http(NodeInfoSettingsHttp value) {
			this.http = value;
			return this;
		}

		/**
		 * Required - API name: {@code http}
		 */
		public final Builder http(Function<NodeInfoSettingsHttp.Builder, ObjectBuilder<NodeInfoSettingsHttp>> fn) {
			return this.http(fn.apply(new NodeInfoSettingsHttp.Builder()).build());
		}

		/**
		 * API name: {@code bootstrap}
		 */
		public final Builder bootstrap(@Nullable NodeInfoBootstrap value) {
			this.bootstrap = value;
			return this;
		}

		/**
		 * API name: {@code bootstrap}
		 */
		public final Builder bootstrap(Function<NodeInfoBootstrap.Builder, ObjectBuilder<NodeInfoBootstrap>> fn) {
			return this.bootstrap(fn.apply(new NodeInfoBootstrap.Builder()).build());
		}

		/**
		 * Required - API name: {@code transport}
		 */
		public final Builder transport(NodeInfoSettingsTransport value) {
			this.transport = value;
			return this;
		}

		/**
		 * Required - API name: {@code transport}
		 */
		public final Builder transport(
				Function<NodeInfoSettingsTransport.Builder, ObjectBuilder<NodeInfoSettingsTransport>> fn) {
			return this.transport(fn.apply(new NodeInfoSettingsTransport.Builder()).build());
		}

		/**
		 * API name: {@code network}
		 */
		public final Builder network(@Nullable NodeInfoSettingsNetwork value) {
			this.network = value;
			return this;
		}

		/**
		 * API name: {@code network}
		 */
		public final Builder network(
				Function<NodeInfoSettingsNetwork.Builder, ObjectBuilder<NodeInfoSettingsNetwork>> fn) {
			return this.network(fn.apply(new NodeInfoSettingsNetwork.Builder()).build());
		}

		/**
		 * API name: {@code script}
		 */
		public final Builder script(@Nullable NodeInfoScript value) {
			this.script = value;
			return this;
		}

		/**
		 * API name: {@code script}
		 */
		public final Builder script(Function<NodeInfoScript.Builder, ObjectBuilder<NodeInfoScript>> fn) {
			return this.script(fn.apply(new NodeInfoScript.Builder()).build());
		}

		/**
		 * API name: {@code search}
		 */
		public final Builder search(@Nullable NodeInfoSearch value) {
			this.search = value;
			return this;
		}

		/**
		 * API name: {@code search}
		 */
		public final Builder search(Function<NodeInfoSearch.Builder, ObjectBuilder<NodeInfoSearch>> fn) {
			return this.search(fn.apply(new NodeInfoSearch.Builder()).build());
		}

		/**
		 * API name: {@code ingest}
		 */
		public final Builder ingest(@Nullable NodeInfoSettingsIngest value) {
			this.ingest = value;
			return this;
		}

		/**
		 * API name: {@code ingest}
		 */
		public final Builder ingest(
				Function<NodeInfoSettingsIngest.Builder, ObjectBuilder<NodeInfoSettingsIngest>> fn) {
			return this.ingest(fn.apply(new NodeInfoSettingsIngest.Builder()).build());
		}

		/**
		 * Builds a {@link NodeInfoSettings}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public NodeInfoSettings build() {
			_checkSingleUse();

			return new NodeInfoSettings(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link NodeInfoSettings}
	 */
	public static final JsonpDeserializer<NodeInfoSettings> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			NodeInfoSettings::setupNodeInfoSettingsDeserializer);

	protected static void setupNodeInfoSettingsDeserializer(ObjectDeserializer<NodeInfoSettings.Builder> op) {

		op.add(Builder::cluster, NodeInfoSettingsCluster._DESERIALIZER, "cluster");
		op.add(Builder::node, NodeInfoSettingsNode._DESERIALIZER, "node");
		op.add(Builder::path, NodeInfoPath._DESERIALIZER, "path");
		op.add(Builder::repositories, NodeInfoRepositories._DESERIALIZER, "repositories");
		op.add(Builder::discovery, NodeInfoDiscover._DESERIALIZER, "discovery");
		op.add(Builder::action, NodeInfoAction._DESERIALIZER, "action");
		op.add(Builder::client, NodeInfoClient._DESERIALIZER, "client");
		op.add(Builder::http, NodeInfoSettingsHttp._DESERIALIZER, "http");
		op.add(Builder::bootstrap, NodeInfoBootstrap._DESERIALIZER, "bootstrap");
		op.add(Builder::transport, NodeInfoSettingsTransport._DESERIALIZER, "transport");
		op.add(Builder::network, NodeInfoSettingsNetwork._DESERIALIZER, "network");
		op.add(Builder::script, NodeInfoScript._DESERIALIZER, "script");
		op.add(Builder::search, NodeInfoSearch._DESERIALIZER, "search");
		op.add(Builder::ingest, NodeInfoSettingsIngest._DESERIALIZER, "ingest");

	}

}
