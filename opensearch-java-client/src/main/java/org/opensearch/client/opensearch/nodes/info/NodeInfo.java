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

import org.opensearch.client.opensearch._types.NodeRole;
import org.opensearch.client.opensearch._types.PluginStats;
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

// typedef: nodes.info.NodeInfo

@JsonpDeserializable
public class NodeInfo implements JsonpSerializable {
    @Nullable
    private final Map<String, String> attributes;

	private final String buildHash;

	private final String buildType;

	private final String host;

	@Nullable
	private final NodeInfoHttp http;

	private final String ip;

	@Nullable
	private final NodeJvmInfo jvm;

	private final String name;

	@Nullable
	private final NodeInfoNetwork network;

	@Nullable
	private final NodeOperatingSystemInfo os;

	private final List<PluginStats> plugins;

	@Nullable
	private final NodeProcessInfo process;

	private final List<NodeRole> roles;

	@Nullable
	private final NodeInfoSettings settings;

	private final Map<String, NodeThreadPoolInfo> threadPool;

	@Nullable
	private final Long totalIndexingBuffer;

	@Nullable
	private final String totalIndexingBufferInBytes;

	@Nullable
	private final NodeInfoTransport transport;

	private final String transportAddress;

	private final String version;

	private final List<PluginStats> modules;

	@Nullable
	private final NodeInfoIngest ingest;

	private final Map<String, NodeInfoAggregation> aggregations;

	// ---------------------------------------------------------------------------------------------

	private NodeInfo(Builder builder) {

		this.attributes = ApiTypeHelper.unmodifiable(builder.attributes);
		this.buildHash = ApiTypeHelper.requireNonNull(builder.buildHash, this, "buildHash");
		this.buildType = ApiTypeHelper.requireNonNull(builder.buildType, this, "buildType");
		this.host = ApiTypeHelper.requireNonNull(builder.host, this, "host");
		this.http = builder.http;
		this.ip = ApiTypeHelper.requireNonNull(builder.ip, this, "ip");
		this.jvm = builder.jvm;
		this.name = ApiTypeHelper.requireNonNull(builder.name, this, "name");
		this.network = builder.network;
		this.os = builder.os;
		this.plugins = ApiTypeHelper.unmodifiable(builder.plugins);
		this.process = builder.process;
		this.roles = ApiTypeHelper.unmodifiableRequired(builder.roles, this, "roles");
		this.settings = builder.settings;
		this.threadPool = ApiTypeHelper.unmodifiable(builder.threadPool);
		this.totalIndexingBuffer = builder.totalIndexingBuffer;
		this.totalIndexingBufferInBytes = builder.totalIndexingBufferInBytes;
		this.transport = builder.transport;
		this.transportAddress = ApiTypeHelper.requireNonNull(builder.transportAddress, this, "transportAddress");
		this.version = ApiTypeHelper.requireNonNull(builder.version, this, "version");
		this.modules = ApiTypeHelper.unmodifiable(builder.modules);
		this.ingest = builder.ingest;
		this.aggregations = ApiTypeHelper.unmodifiable(builder.aggregations);

	}

	public static NodeInfo of(Function<Builder, ObjectBuilder<NodeInfo>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * API name: {@code attributes}
	 */
	@Nullable
	public final Map<String, String> attributes() {
		return this.attributes;
	}

	/**
	 * Required - Short hash of the last git commit in this release.
	 * <p>
	 * API name: {@code build_hash}
	 */
	public final String buildHash() {
		return this.buildHash;
	}

	/**
	 * Required - API name: {@code build_type}
	 */
	public final String buildType() {
		return this.buildType;
	}

	/**
	 * Required - The node's host name.
	 * <p>
	 * API name: {@code host}
	 */
	public final String host() {
		return this.host;
	}

	/**
	 * API name: {@code http}
	 */
	@Nullable
	public final NodeInfoHttp http() {
		return this.http;
	}

	/**
	 * Required - The node's IP address.
	 * <p>
	 * API name: {@code ip}
	 */
	public final String ip() {
		return this.ip;
	}

	/**
	 * API name: {@code jvm}
	 */
	@Nullable
	public final NodeJvmInfo jvm() {
		return this.jvm;
	}

	/**
	 * Required - The node's name
	 * <p>
	 * API name: {@code name}
	 */
	public final String name() {
		return this.name;
	}

	/**
	 * API name: {@code network}
	 */
	@Nullable
	public final NodeInfoNetwork network() {
		return this.network;
	}

	/**
	 * API name: {@code os}
	 */
	@Nullable
	public final NodeOperatingSystemInfo os() {
		return this.os;
	}

	/**
	 * API name: {@code plugins}
	 */
	public final List<PluginStats> plugins() {
		return this.plugins;
	}

	/**
	 * API name: {@code process}
	 */
	@Nullable
	public final NodeProcessInfo process() {
		return this.process;
	}

	/**
	 * Required - API name: {@code roles}
	 */
	public final List<NodeRole> roles() {
		return this.roles;
	}

	/**
	 * API name: {@code settings}
	 */
	@Nullable
	public final NodeInfoSettings settings() {
		return this.settings;
	}

	/**
	 * API name: {@code thread_pool}
	 */
	public final Map<String, NodeThreadPoolInfo> threadPool() {
		return this.threadPool;
	}

	/**
	 * Total heap allowed to be used to hold recently indexed documents before they
	 * must be written to disk. This size is a shared pool across all shards on this
	 * node, and is controlled by Indexing Buffer settings.
	 * <p>
	 * API name: {@code total_indexing_buffer}
	 */
	@Nullable
	public final Long totalIndexingBuffer() {
		return this.totalIndexingBuffer;
	}

	/**
	 * Same as total_indexing_buffer, but expressed in bytes.
	 * <p>
	 * API name: {@code total_indexing_buffer_in_bytes}
	 */
	@Nullable
	public final String totalIndexingBufferInBytes() {
		return this.totalIndexingBufferInBytes;
	}

	/**
	 * API name: {@code transport}
	 */
	@Nullable
	public final NodeInfoTransport transport() {
		return this.transport;
	}

	/**
	 * Required - Host and port where transport HTTP connections are accepted.
	 * <p>
	 * API name: {@code transport_address}
	 */
	public final String transportAddress() {
		return this.transportAddress;
	}

	/**
	 * Required - Elasticsearch version running on this node.
	 * <p>
	 * API name: {@code version}
	 */
	public final String version() {
		return this.version;
	}

	/**
	 * API name: {@code modules}
	 */
	public final List<PluginStats> modules() {
		return this.modules;
	}

	/**
	 * API name: {@code ingest}
	 */
	@Nullable
	public final NodeInfoIngest ingest() {
		return this.ingest;
	}

	/**
	 * API name: {@code aggregations}
	 */
	public final Map<String, NodeInfoAggregation> aggregations() {
		return this.aggregations;
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

		if (ApiTypeHelper.isDefined(this.attributes)) {
			generator.writeKey("attributes");
			generator.writeStartObject();
			for (Map.Entry<String, String> item0 : this.attributes.entrySet()) {
				generator.writeKey(item0.getKey());
				generator.write(item0.getValue());

			}
			generator.writeEnd();

		}

		generator.writeKey("build_hash");
		generator.write(this.buildHash);

		generator.writeKey("build_type");
		generator.write(this.buildType);

		generator.writeKey("host");
		generator.write(this.host);

		if (this.http != null) {
			generator.writeKey("http");
			this.http.serialize(generator, mapper);

		}
		generator.writeKey("ip");
		generator.write(this.ip);

		if (this.jvm != null) {
			generator.writeKey("jvm");
			this.jvm.serialize(generator, mapper);

		}
		generator.writeKey("name");
		generator.write(this.name);

		if (this.network != null) {
			generator.writeKey("network");
			this.network.serialize(generator, mapper);

		}
		if (this.os != null) {
			generator.writeKey("os");
			this.os.serialize(generator, mapper);

		}
		if (ApiTypeHelper.isDefined(this.plugins)) {
			generator.writeKey("plugins");
			generator.writeStartArray();
			for (PluginStats item0 : this.plugins) {
				item0.serialize(generator, mapper);

			}
			generator.writeEnd();

		}
		if (this.process != null) {
			generator.writeKey("process");
			this.process.serialize(generator, mapper);

		}
		if (ApiTypeHelper.isDefined(this.roles)) {
			generator.writeKey("roles");
			generator.writeStartArray();
			for (NodeRole item0 : this.roles) {
				item0.serialize(generator, mapper);
			}
			generator.writeEnd();

		}
		if (this.settings != null) {
			generator.writeKey("settings");
			this.settings.serialize(generator, mapper);

		}
		if (ApiTypeHelper.isDefined(this.threadPool)) {
			generator.writeKey("thread_pool");
			generator.writeStartObject();
			for (Map.Entry<String, NodeThreadPoolInfo> item0 : this.threadPool.entrySet()) {
				generator.writeKey(item0.getKey());
				item0.getValue().serialize(generator, mapper);

			}
			generator.writeEnd();

		}
		if (this.totalIndexingBuffer != null) {
			generator.writeKey("total_indexing_buffer");
			generator.write(this.totalIndexingBuffer);

		}
		if (this.totalIndexingBufferInBytes != null) {
			generator.writeKey("total_indexing_buffer_in_bytes");
			generator.write(this.totalIndexingBufferInBytes);

		}
		if (this.transport != null) {
			generator.writeKey("transport");
			this.transport.serialize(generator, mapper);

		}
		generator.writeKey("transport_address");
		generator.write(this.transportAddress);

		generator.writeKey("version");
		generator.write(this.version);

		if (ApiTypeHelper.isDefined(this.modules)) {
			generator.writeKey("modules");
			generator.writeStartArray();
			for (PluginStats item0 : this.modules) {
				item0.serialize(generator, mapper);

			}
			generator.writeEnd();

		}
		if (this.ingest != null) {
			generator.writeKey("ingest");
			this.ingest.serialize(generator, mapper);

		}
		if (ApiTypeHelper.isDefined(this.aggregations)) {
			generator.writeKey("aggregations");
			generator.writeStartObject();
			for (Map.Entry<String, NodeInfoAggregation> item0 : this.aggregations.entrySet()) {
				generator.writeKey(item0.getKey());
				item0.getValue().serialize(generator, mapper);

			}
			generator.writeEnd();

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link NodeInfo}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<NodeInfo> {
	    @Nullable
		private Map<String, String> attributes;

		private String buildHash;

		private String buildType;

		private String host;

		@Nullable
		private NodeInfoHttp http;

		private String ip;

		@Nullable
		private NodeJvmInfo jvm;

		private String name;

		@Nullable
		private NodeInfoNetwork network;

		@Nullable
		private NodeOperatingSystemInfo os;

		@Nullable
		private List<PluginStats> plugins;

		@Nullable
		private NodeProcessInfo process;

		private List<NodeRole> roles;

		@Nullable
		private NodeInfoSettings settings;

		@Nullable
		private Map<String, NodeThreadPoolInfo> threadPool;

		@Nullable
		private Long totalIndexingBuffer;

		@Nullable
		private String totalIndexingBufferInBytes;

		@Nullable
		private NodeInfoTransport transport;

		private String transportAddress;

		private String version;

		@Nullable
		private List<PluginStats> modules;

		@Nullable
		private NodeInfoIngest ingest;

		@Nullable
		private Map<String, NodeInfoAggregation> aggregations;

		/**
		 * API name: {@code attributes}
		 * <p>
		 * Adds all entries of <code>map</code> to <code>attributes</code>.
		 */
		public final Builder attributes(Map<String, String> map) {
			this.attributes = _mapPutAll(this.attributes, map);
			return this;
		}

		/**
		 * API name: {@code attributes}
		 * <p>
		 * Adds an entry to <code>attributes</code>.
		 */
		public final Builder attributes(String key, String value) {
			this.attributes = _mapPut(this.attributes, key, value);
			return this;
		}

		/**
		 * Required - Short hash of the last git commit in this release.
		 * <p>
		 * API name: {@code build_hash}
		 */
		public final Builder buildHash(String value) {
			this.buildHash = value;
			return this;
		}

		/**
		 * Required - API name: {@code build_type}
		 */
		public final Builder buildType(String value) {
			this.buildType = value;
			return this;
		}

		/**
		 * Required - The node's host name.
		 * <p>
		 * API name: {@code host}
		 */
		public final Builder host(String value) {
			this.host = value;
			return this;
		}

		/**
		 * API name: {@code http}
		 */
		public final Builder http(@Nullable NodeInfoHttp value) {
			this.http = value;
			return this;
		}

		/**
		 * API name: {@code http}
		 */
		public final Builder http(Function<NodeInfoHttp.Builder, ObjectBuilder<NodeInfoHttp>> fn) {
			return this.http(fn.apply(new NodeInfoHttp.Builder()).build());
		}

		/**
		 * Required - The node's IP address.
		 * <p>
		 * API name: {@code ip}
		 */
		public final Builder ip(String value) {
			this.ip = value;
			return this;
		}

		/**
		 * API name: {@code jvm}
		 */
		public final Builder jvm(@Nullable NodeJvmInfo value) {
			this.jvm = value;
			return this;
		}

		/**
		 * API name: {@code jvm}
		 */
		public final Builder jvm(Function<NodeJvmInfo.Builder, ObjectBuilder<NodeJvmInfo>> fn) {
			return this.jvm(fn.apply(new NodeJvmInfo.Builder()).build());
		}

		/**
		 * Required - The node's name
		 * <p>
		 * API name: {@code name}
		 */
		public final Builder name(String value) {
			this.name = value;
			return this;
		}

		/**
		 * API name: {@code network}
		 */
		public final Builder network(@Nullable NodeInfoNetwork value) {
			this.network = value;
			return this;
		}

		/**
		 * API name: {@code network}
		 */
		public final Builder network(Function<NodeInfoNetwork.Builder, ObjectBuilder<NodeInfoNetwork>> fn) {
			return this.network(fn.apply(new NodeInfoNetwork.Builder()).build());
		}

		/**
		 * API name: {@code os}
		 */
		public final Builder os(@Nullable NodeOperatingSystemInfo value) {
			this.os = value;
			return this;
		}

		/**
		 * API name: {@code os}
		 */
		public final Builder os(Function<NodeOperatingSystemInfo.Builder, ObjectBuilder<NodeOperatingSystemInfo>> fn) {
			return this.os(fn.apply(new NodeOperatingSystemInfo.Builder()).build());
		}

		/**
		 * API name: {@code plugins}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>plugins</code>.
		 */
		public final Builder plugins(List<PluginStats> list) {
			this.plugins = _listAddAll(this.plugins, list);
			return this;
		}

		/**
		 * API name: {@code plugins}
		 * <p>
		 * Adds one or more values to <code>plugins</code>.
		 */
		public final Builder plugins(PluginStats value, PluginStats... values) {
			this.plugins = _listAdd(this.plugins, value, values);
			return this;
		}

		/**
		 * API name: {@code plugins}
		 * <p>
		 * Adds a value to <code>plugins</code> using a builder lambda.
		 */
		public final Builder plugins(Function<PluginStats.Builder, ObjectBuilder<PluginStats>> fn) {
			return plugins(fn.apply(new PluginStats.Builder()).build());
		}

		/**
		 * API name: {@code process}
		 */
		public final Builder process(@Nullable NodeProcessInfo value) {
			this.process = value;
			return this;
		}

		/**
		 * API name: {@code process}
		 */
		public final Builder process(Function<NodeProcessInfo.Builder, ObjectBuilder<NodeProcessInfo>> fn) {
			return this.process(fn.apply(new NodeProcessInfo.Builder()).build());
		}

		/**
		 * Required - API name: {@code roles}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>roles</code>.
		 */
		public final Builder roles(List<NodeRole> list) {
			this.roles = _listAddAll(this.roles, list);
			return this;
		}

		/**
		 * Required - API name: {@code roles}
		 * <p>
		 * Adds one or more values to <code>roles</code>.
		 */
		public final Builder roles(NodeRole value, NodeRole... values) {
			this.roles = _listAdd(this.roles, value, values);
			return this;
		}

		/**
		 * API name: {@code settings}
		 */
		public final Builder settings(@Nullable NodeInfoSettings value) {
			this.settings = value;
			return this;
		}

		/**
		 * API name: {@code settings}
		 */
		public final Builder settings(Function<NodeInfoSettings.Builder, ObjectBuilder<NodeInfoSettings>> fn) {
			return this.settings(fn.apply(new NodeInfoSettings.Builder()).build());
		}

		/**
		 * API name: {@code thread_pool}
		 * <p>
		 * Adds all entries of <code>map</code> to <code>threadPool</code>.
		 */
		public final Builder threadPool(Map<String, NodeThreadPoolInfo> map) {
			this.threadPool = _mapPutAll(this.threadPool, map);
			return this;
		}

		/**
		 * API name: {@code thread_pool}
		 * <p>
		 * Adds an entry to <code>threadPool</code>.
		 */
		public final Builder threadPool(String key, NodeThreadPoolInfo value) {
			this.threadPool = _mapPut(this.threadPool, key, value);
			return this;
		}

		/**
		 * API name: {@code thread_pool}
		 * <p>
		 * Adds an entry to <code>threadPool</code> using a builder lambda.
		 */
		public final Builder threadPool(String key,
				Function<NodeThreadPoolInfo.Builder, ObjectBuilder<NodeThreadPoolInfo>> fn) {
			return threadPool(key, fn.apply(new NodeThreadPoolInfo.Builder()).build());
		}

		/**
		 * Total heap allowed to be used to hold recently indexed documents before they
		 * must be written to disk. This size is a shared pool across all shards on this
		 * node, and is controlled by Indexing Buffer settings.
		 * <p>
		 * API name: {@code total_indexing_buffer}
		 */
		public final Builder totalIndexingBuffer(@Nullable Long value) {
			this.totalIndexingBuffer = value;
			return this;
		}

		/**
		 * Same as total_indexing_buffer, but expressed in bytes.
		 * <p>
		 * API name: {@code total_indexing_buffer_in_bytes}
		 */
		public final Builder totalIndexingBufferInBytes(@Nullable String value) {
			this.totalIndexingBufferInBytes = value;
			return this;
		}

		/**
		 * API name: {@code transport}
		 */
		public final Builder transport(@Nullable NodeInfoTransport value) {
			this.transport = value;
			return this;
		}

		/**
		 * API name: {@code transport}
		 */
		public final Builder transport(Function<NodeInfoTransport.Builder, ObjectBuilder<NodeInfoTransport>> fn) {
			return this.transport(fn.apply(new NodeInfoTransport.Builder()).build());
		}

		/**
		 * Required - Host and port where transport HTTP connections are accepted.
		 * <p>
		 * API name: {@code transport_address}
		 */
		public final Builder transportAddress(String value) {
			this.transportAddress = value;
			return this;
		}

		/**
		 * Required - Elasticsearch version running on this node.
		 * <p>
		 * API name: {@code version}
		 */
		public final Builder version(String value) {
			this.version = value;
			return this;
		}

		/**
		 * API name: {@code modules}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>modules</code>.
		 */
		public final Builder modules(List<PluginStats> list) {
			this.modules = _listAddAll(this.modules, list);
			return this;
		}

		/**
		 * API name: {@code modules}
		 * <p>
		 * Adds one or more values to <code>modules</code>.
		 */
		public final Builder modules(PluginStats value, PluginStats... values) {
			this.modules = _listAdd(this.modules, value, values);
			return this;
		}

		/**
		 * API name: {@code modules}
		 * <p>
		 * Adds a value to <code>modules</code> using a builder lambda.
		 */
		public final Builder modules(Function<PluginStats.Builder, ObjectBuilder<PluginStats>> fn) {
			return modules(fn.apply(new PluginStats.Builder()).build());
		}

		/**
		 * API name: {@code ingest}
		 */
		public final Builder ingest(@Nullable NodeInfoIngest value) {
			this.ingest = value;
			return this;
		}

		/**
		 * API name: {@code ingest}
		 */
		public final Builder ingest(Function<NodeInfoIngest.Builder, ObjectBuilder<NodeInfoIngest>> fn) {
			return this.ingest(fn.apply(new NodeInfoIngest.Builder()).build());
		}

		/**
		 * API name: {@code aggregations}
		 * <p>
		 * Adds all entries of <code>map</code> to <code>aggregations</code>.
		 */
		public final Builder aggregations(Map<String, NodeInfoAggregation> map) {
			this.aggregations = _mapPutAll(this.aggregations, map);
			return this;
		}

		/**
		 * API name: {@code aggregations}
		 * <p>
		 * Adds an entry to <code>aggregations</code>.
		 */
		public final Builder aggregations(String key, NodeInfoAggregation value) {
			this.aggregations = _mapPut(this.aggregations, key, value);
			return this;
		}

		/**
		 * API name: {@code aggregations}
		 * <p>
		 * Adds an entry to <code>aggregations</code> using a builder lambda.
		 */
		public final Builder aggregations(String key,
				Function<NodeInfoAggregation.Builder, ObjectBuilder<NodeInfoAggregation>> fn) {
			return aggregations(key, fn.apply(new NodeInfoAggregation.Builder()).build());
		}

		/**
		 * Builds a {@link NodeInfo}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public NodeInfo build() {
			_checkSingleUse();

			return new NodeInfo(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link NodeInfo}
	 */
	public static final JsonpDeserializer<NodeInfo> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			NodeInfo::setupNodeInfoDeserializer);

	protected static void setupNodeInfoDeserializer(ObjectDeserializer<NodeInfo.Builder> op) {

		op.add(Builder::attributes, JsonpDeserializer.stringMapDeserializer(JsonpDeserializer.stringDeserializer()),
				"attributes");
		op.add(Builder::buildHash, JsonpDeserializer.stringDeserializer(), "build_hash");
		op.add(Builder::buildType, JsonpDeserializer.stringDeserializer(), "build_type");
		op.add(Builder::host, JsonpDeserializer.stringDeserializer(), "host");
		op.add(Builder::http, NodeInfoHttp._DESERIALIZER, "http");
		op.add(Builder::ip, JsonpDeserializer.stringDeserializer(), "ip");
		op.add(Builder::jvm, NodeJvmInfo._DESERIALIZER, "jvm");
		op.add(Builder::name, JsonpDeserializer.stringDeserializer(), "name");
		op.add(Builder::network, NodeInfoNetwork._DESERIALIZER, "network");
		op.add(Builder::os, NodeOperatingSystemInfo._DESERIALIZER, "os");
		op.add(Builder::plugins, JsonpDeserializer.arrayDeserializer(PluginStats._DESERIALIZER), "plugins");
		op.add(Builder::process, NodeProcessInfo._DESERIALIZER, "process");
		op.add(Builder::roles, JsonpDeserializer.arrayDeserializer(NodeRole._DESERIALIZER), "roles");
		op.add(Builder::settings, NodeInfoSettings._DESERIALIZER, "settings");
		op.add(Builder::threadPool, JsonpDeserializer.stringMapDeserializer(NodeThreadPoolInfo._DESERIALIZER),
				"thread_pool");
		op.add(Builder::totalIndexingBuffer, JsonpDeserializer.longDeserializer(), "total_indexing_buffer");
		op.add(Builder::totalIndexingBufferInBytes, JsonpDeserializer.stringDeserializer(),
				"total_indexing_buffer_in_bytes");
		op.add(Builder::transport, NodeInfoTransport._DESERIALIZER, "transport");
		op.add(Builder::transportAddress, JsonpDeserializer.stringDeserializer(), "transport_address");
		op.add(Builder::version, JsonpDeserializer.stringDeserializer(), "version");
		op.add(Builder::modules, JsonpDeserializer.arrayDeserializer(PluginStats._DESERIALIZER), "modules");
		op.add(Builder::ingest, NodeInfoIngest._DESERIALIZER, "ingest");
		op.add(Builder::aggregations, JsonpDeserializer.stringMapDeserializer(NodeInfoAggregation._DESERIALIZER),
				"aggregations");

	}

}
