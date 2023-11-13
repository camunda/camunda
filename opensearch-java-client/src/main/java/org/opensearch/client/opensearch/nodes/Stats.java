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

package org.opensearch.client.opensearch.nodes;

import org.opensearch.client.opensearch._types.NodeRole;
import org.opensearch.client.opensearch.indices.stats.IndexStats;
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

// typedef: nodes._types.Stats

@JsonpDeserializable
public class Stats implements JsonpSerializable {
	private final Map<String, AdaptiveSelection> adaptiveSelection;

	private final Map<String, Breaker> breakers;

	private final FileSystem fs;

	private final String host;

	private final Http http;

	private final IndexStats indices;

	private final Ingest ingest;

	private final List<String> ip;

	private final Jvm jvm;

	private final String name;

	private final OperatingSystem os;

	private final Process process;

	private final List<NodeRole> roles;

	private final Scripting script;

	private final Map<String, ThreadCount> threadPool;

	private final long timestamp;

	private final Transport transport;

	private final String transportAddress;

	@Nullable
	private final Map<String, String> attributes;

	// ---------------------------------------------------------------------------------------------

	private Stats(Builder builder) {

		this.adaptiveSelection = ApiTypeHelper.unmodifiableRequired(builder.adaptiveSelection, this,
				"adaptiveSelection");
		this.breakers = ApiTypeHelper.unmodifiableRequired(builder.breakers, this, "breakers");
		this.fs = ApiTypeHelper.requireNonNull(builder.fs, this, "fs");
		this.host = ApiTypeHelper.requireNonNull(builder.host, this, "host");
		this.http = ApiTypeHelper.requireNonNull(builder.http, this, "http");
		this.indices = ApiTypeHelper.requireNonNull(builder.indices, this, "indices");
		this.ingest = ApiTypeHelper.requireNonNull(builder.ingest, this, "ingest");
		this.ip = ApiTypeHelper.unmodifiableRequired(builder.ip, this, "ip");
		this.jvm = ApiTypeHelper.requireNonNull(builder.jvm, this, "jvm");
		this.name = ApiTypeHelper.requireNonNull(builder.name, this, "name");
		this.os = ApiTypeHelper.requireNonNull(builder.os, this, "os");
		this.process = ApiTypeHelper.requireNonNull(builder.process, this, "process");
		this.roles = ApiTypeHelper.unmodifiableRequired(builder.roles, this, "roles");
		this.script = ApiTypeHelper.requireNonNull(builder.script, this, "script");
		this.threadPool = ApiTypeHelper.unmodifiableRequired(builder.threadPool, this, "threadPool");
		this.timestamp = ApiTypeHelper.requireNonNull(builder.timestamp, this, "timestamp");
		this.transport = ApiTypeHelper.requireNonNull(builder.transport, this, "transport");
		this.transportAddress = ApiTypeHelper.requireNonNull(builder.transportAddress, this, "transportAddress");
		this.attributes = builder.attributes;

	}

	public static Stats of(Function<Builder, ObjectBuilder<Stats>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code adaptive_selection}
	 */
	public final Map<String, AdaptiveSelection> adaptiveSelection() {
		return this.adaptiveSelection;
	}

	/**
	 * Required - API name: {@code breakers}
	 */
	public final Map<String, Breaker> breakers() {
		return this.breakers;
	}

	/**
	 * Required - API name: {@code fs}
	 */
	public final FileSystem fs() {
		return this.fs;
	}

	/**
	 * Required - API name: {@code host}
	 */
	public final String host() {
		return this.host;
	}

	/**
	 * Required - API name: {@code http}
	 */
	public final Http http() {
		return this.http;
	}

	/**
	 * Required - API name: {@code indices}
	 */
	public final IndexStats indices() {
		return this.indices;
	}

	/**
	 * Required - API name: {@code ingest}
	 */
	public final Ingest ingest() {
		return this.ingest;
	}

	/**
	 * Required - API name: {@code ip}
	 */
	public final List<String> ip() {
		return this.ip;
	}

	/**
	 * Required - API name: {@code jvm}
	 */
	public final Jvm jvm() {
		return this.jvm;
	}

	/**
	 * Required - API name: {@code name}
	 */
	public final String name() {
		return this.name;
	}

	/**
	 * Required - API name: {@code os}
	 */
	public final OperatingSystem os() {
		return this.os;
	}

	/**
	 * Required - API name: {@code process}
	 */
	public final Process process() {
		return this.process;
	}

	/**
	 * Required - API name: {@code roles}
	 */
	public final List<NodeRole> roles() {
		return this.roles;
	}

	/**
	 * Required - API name: {@code script}
	 */
	public final Scripting script() {
		return this.script;
	}

	/**
	 * Required - API name: {@code thread_pool}
	 */
	public final Map<String, ThreadCount> threadPool() {
		return this.threadPool;
	}

	/**
	 * Required - API name: {@code timestamp}
	 */
	public final long timestamp() {
		return this.timestamp;
	}

	/**
	 * Required - API name: {@code transport}
	 */
	public final Transport transport() {
		return this.transport;
	}

	/**
	 * Required - API name: {@code transport_address}
	 */
	public final String transportAddress() {
		return this.transportAddress;
	}

	/**
	 * API name: {@code attributes}
	 */
	public final Map<String, String> attributes() {
		return this.attributes;
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

		if (ApiTypeHelper.isDefined(this.adaptiveSelection)) {
			generator.writeKey("adaptive_selection");
			generator.writeStartObject();
			for (Map.Entry<String, AdaptiveSelection> item0 : this.adaptiveSelection.entrySet()) {
				generator.writeKey(item0.getKey());
				item0.getValue().serialize(generator, mapper);

			}
			generator.writeEnd();

		}
		if (ApiTypeHelper.isDefined(this.breakers)) {
			generator.writeKey("breakers");
			generator.writeStartObject();
			for (Map.Entry<String, Breaker> item0 : this.breakers.entrySet()) {
				generator.writeKey(item0.getKey());
				item0.getValue().serialize(generator, mapper);

			}
			generator.writeEnd();

		}
		generator.writeKey("fs");
		this.fs.serialize(generator, mapper);

		generator.writeKey("host");
		generator.write(this.host);

		generator.writeKey("http");
		this.http.serialize(generator, mapper);

		generator.writeKey("indices");
		this.indices.serialize(generator, mapper);

		generator.writeKey("ingest");
		this.ingest.serialize(generator, mapper);

		if (ApiTypeHelper.isDefined(this.ip)) {
			generator.writeKey("ip");
			generator.writeStartArray();
			for (String item0 : this.ip) {
				generator.write(item0);

			}
			generator.writeEnd();

		}
		generator.writeKey("jvm");
		this.jvm.serialize(generator, mapper);

		generator.writeKey("name");
		generator.write(this.name);

		generator.writeKey("os");
		this.os.serialize(generator, mapper);

		generator.writeKey("process");
		this.process.serialize(generator, mapper);

		if (ApiTypeHelper.isDefined(this.roles)) {
			generator.writeKey("roles");
			generator.writeStartArray();
			for (NodeRole item0 : this.roles) {
				item0.serialize(generator, mapper);
			}
			generator.writeEnd();

		}
		generator.writeKey("script");
		this.script.serialize(generator, mapper);

		if (ApiTypeHelper.isDefined(this.threadPool)) {
			generator.writeKey("thread_pool");
			generator.writeStartObject();
			for (Map.Entry<String, ThreadCount> item0 : this.threadPool.entrySet()) {
				generator.writeKey(item0.getKey());
				item0.getValue().serialize(generator, mapper);

			}
			generator.writeEnd();

		}
		generator.writeKey("timestamp");
		generator.write(this.timestamp);

		generator.writeKey("transport");
		this.transport.serialize(generator, mapper);

		generator.writeKey("transport_address");
		generator.write(this.transportAddress);

		if (ApiTypeHelper.isDefined(this.attributes)) {
			generator.writeKey("attributes");
			generator.writeStartObject();
			for (Map.Entry<String, String> item0 : this.attributes.entrySet()) {
				generator.writeKey(item0.getKey());
				generator.write(item0.getValue());

			}
			generator.writeEnd();

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link Stats}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<Stats> {
		private Map<String, AdaptiveSelection> adaptiveSelection;

		private Map<String, Breaker> breakers;

		private FileSystem fs;

		private String host;

		private Http http;

		private IndexStats indices;

		private Ingest ingest;

		private List<String> ip;

		private Jvm jvm;

		private String name;

		private OperatingSystem os;

		private Process process;

		private List<NodeRole> roles;

		private Scripting script;

		private Map<String, ThreadCount> threadPool;

		private Long timestamp;

		private Transport transport;

		private String transportAddress;

		@Nullable
		private Map<String, String> attributes;

		/**
		 * Required - API name: {@code adaptive_selection}
		 * <p>
		 * Adds all entries of <code>map</code> to <code>adaptiveSelection</code>.
		 */
		public final Builder adaptiveSelection(Map<String, AdaptiveSelection> map) {
			this.adaptiveSelection = _mapPutAll(this.adaptiveSelection, map);
			return this;
		}

		/**
		 * Required - API name: {@code adaptive_selection}
		 * <p>
		 * Adds an entry to <code>adaptiveSelection</code>.
		 */
		public final Builder adaptiveSelection(String key, AdaptiveSelection value) {
			this.adaptiveSelection = _mapPut(this.adaptiveSelection, key, value);
			return this;
		}

		/**
		 * Required - API name: {@code adaptive_selection}
		 * <p>
		 * Adds an entry to <code>adaptiveSelection</code> using a builder lambda.
		 */
		public final Builder adaptiveSelection(String key,
				Function<AdaptiveSelection.Builder, ObjectBuilder<AdaptiveSelection>> fn) {
			return adaptiveSelection(key, fn.apply(new AdaptiveSelection.Builder()).build());
		}

		/**
		 * Required - API name: {@code breakers}
		 * <p>
		 * Adds all entries of <code>map</code> to <code>breakers</code>.
		 */
		public final Builder breakers(Map<String, Breaker> map) {
			this.breakers = _mapPutAll(this.breakers, map);
			return this;
		}

		/**
		 * Required - API name: {@code breakers}
		 * <p>
		 * Adds an entry to <code>breakers</code>.
		 */
		public final Builder breakers(String key, Breaker value) {
			this.breakers = _mapPut(this.breakers, key, value);
			return this;
		}

		/**
		 * Required - API name: {@code breakers}
		 * <p>
		 * Adds an entry to <code>breakers</code> using a builder lambda.
		 */
		public final Builder breakers(String key, Function<Breaker.Builder, ObjectBuilder<Breaker>> fn) {
			return breakers(key, fn.apply(new Breaker.Builder()).build());
		}

		/**
		 * Required - API name: {@code fs}
		 */
		public final Builder fs(FileSystem value) {
			this.fs = value;
			return this;
		}

		/**
		 * Required - API name: {@code fs}
		 */
		public final Builder fs(Function<FileSystem.Builder, ObjectBuilder<FileSystem>> fn) {
			return this.fs(fn.apply(new FileSystem.Builder()).build());
		}

		/**
		 * Required - API name: {@code host}
		 */
		public final Builder host(String value) {
			this.host = value;
			return this;
		}

		/**
		 * Required - API name: {@code http}
		 */
		public final Builder http(Http value) {
			this.http = value;
			return this;
		}

		/**
		 * Required - API name: {@code http}
		 */
		public final Builder http(Function<Http.Builder, ObjectBuilder<Http>> fn) {
			return this.http(fn.apply(new Http.Builder()).build());
		}

		/**
		 * Required - API name: {@code indices}
		 */
		public final Builder indices(IndexStats value) {
			this.indices = value;
			return this;
		}

		/**
		 * Required - API name: {@code indices}
		 */
		public final Builder indices(Function<IndexStats.Builder, ObjectBuilder<IndexStats>> fn) {
			return this.indices(fn.apply(new IndexStats.Builder()).build());
		}

		/**
		 * Required - API name: {@code ingest}
		 */
		public final Builder ingest(Ingest value) {
			this.ingest = value;
			return this;
		}

		/**
		 * Required - API name: {@code ingest}
		 */
		public final Builder ingest(Function<Ingest.Builder, ObjectBuilder<Ingest>> fn) {
			return this.ingest(fn.apply(new Ingest.Builder()).build());
		}

		/**
		 * Required - API name: {@code ip}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>ip</code>.
		 */
		public final Builder ip(List<String> list) {
			this.ip = _listAddAll(this.ip, list);
			return this;
		}

		/**
		 * Required - API name: {@code ip}
		 * <p>
		 * Adds one or more values to <code>ip</code>.
		 */
		public final Builder ip(String value, String... values) {
			this.ip = _listAdd(this.ip, value, values);
			return this;
		}

		/**
		 * Required - API name: {@code jvm}
		 */
		public final Builder jvm(Jvm value) {
			this.jvm = value;
			return this;
		}

		/**
		 * Required - API name: {@code jvm}
		 */
		public final Builder jvm(Function<Jvm.Builder, ObjectBuilder<Jvm>> fn) {
			return this.jvm(fn.apply(new Jvm.Builder()).build());
		}

		/**
		 * Required - API name: {@code name}
		 */
		public final Builder name(String value) {
			this.name = value;
			return this;
		}

		/**
		 * Required - API name: {@code os}
		 */
		public final Builder os(OperatingSystem value) {
			this.os = value;
			return this;
		}

		/**
		 * Required - API name: {@code os}
		 */
		public final Builder os(Function<OperatingSystem.Builder, ObjectBuilder<OperatingSystem>> fn) {
			return this.os(fn.apply(new OperatingSystem.Builder()).build());
		}

		/**
		 * Required - API name: {@code process}
		 */
		public final Builder process(Process value) {
			this.process = value;
			return this;
		}

		/**
		 * Required - API name: {@code process}
		 */
		public final Builder process(Function<Process.Builder, ObjectBuilder<Process>> fn) {
			return this.process(fn.apply(new Process.Builder()).build());
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
		 * Required - API name: {@code script}
		 */
		public final Builder script(Scripting value) {
			this.script = value;
			return this;
		}

		/**
		 * Required - API name: {@code script}
		 */
		public final Builder script(Function<Scripting.Builder, ObjectBuilder<Scripting>> fn) {
			return this.script(fn.apply(new Scripting.Builder()).build());
		}

		/**
		 * Required - API name: {@code thread_pool}
		 * <p>
		 * Adds all entries of <code>map</code> to <code>threadPool</code>.
		 */
		public final Builder threadPool(Map<String, ThreadCount> map) {
			this.threadPool = _mapPutAll(this.threadPool, map);
			return this;
		}

		/**
		 * Required - API name: {@code thread_pool}
		 * <p>
		 * Adds an entry to <code>threadPool</code>.
		 */
		public final Builder threadPool(String key, ThreadCount value) {
			this.threadPool = _mapPut(this.threadPool, key, value);
			return this;
		}

		/**
		 * Required - API name: {@code thread_pool}
		 * <p>
		 * Adds an entry to <code>threadPool</code> using a builder lambda.
		 */
		public final Builder threadPool(String key, Function<ThreadCount.Builder, ObjectBuilder<ThreadCount>> fn) {
			return threadPool(key, fn.apply(new ThreadCount.Builder()).build());
		}

		/**
		 * Required - API name: {@code timestamp}
		 */
		public final Builder timestamp(long value) {
			this.timestamp = value;
			return this;
		}

		/**
		 * Required - API name: {@code transport}
		 */
		public final Builder transport(Transport value) {
			this.transport = value;
			return this;
		}

		/**
		 * Required - API name: {@code transport}
		 */
		public final Builder transport(Function<Transport.Builder, ObjectBuilder<Transport>> fn) {
			return this.transport(fn.apply(new Transport.Builder()).build());
		}

		/**
		 * Required - API name: {@code transport_address}
		 */
		public final Builder transportAddress(String value) {
			this.transportAddress = value;
			return this;
		}

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
		 * Builds a {@link Stats}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public Stats build() {
			_checkSingleUse();

			return new Stats(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link Stats}
	 */
	public static final JsonpDeserializer<Stats> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			Stats::setupStatsDeserializer);

	protected static void setupStatsDeserializer(ObjectDeserializer<Stats.Builder> op) {

		op.add(Builder::adaptiveSelection, JsonpDeserializer.stringMapDeserializer(AdaptiveSelection._DESERIALIZER),
				"adaptive_selection");
		op.add(Builder::breakers, JsonpDeserializer.stringMapDeserializer(Breaker._DESERIALIZER), "breakers");
		op.add(Builder::fs, FileSystem._DESERIALIZER, "fs");
		op.add(Builder::host, JsonpDeserializer.stringDeserializer(), "host");
		op.add(Builder::http, Http._DESERIALIZER, "http");
		op.add(Builder::indices, IndexStats._DESERIALIZER, "indices");
		op.add(Builder::ingest, Ingest._DESERIALIZER, "ingest");
		op.add(Builder::ip, JsonpDeserializer.arrayDeserializer(JsonpDeserializer.stringDeserializer()), "ip");
		op.add(Builder::jvm, Jvm._DESERIALIZER, "jvm");
		op.add(Builder::name, JsonpDeserializer.stringDeserializer(), "name");
		op.add(Builder::os, OperatingSystem._DESERIALIZER, "os");
		op.add(Builder::process, Process._DESERIALIZER, "process");
		op.add(Builder::roles, JsonpDeserializer.arrayDeserializer(NodeRole._DESERIALIZER), "roles");
		op.add(Builder::script, Scripting._DESERIALIZER, "script");
		op.add(Builder::threadPool, JsonpDeserializer.stringMapDeserializer(ThreadCount._DESERIALIZER), "thread_pool");
		op.add(Builder::timestamp, JsonpDeserializer.longDeserializer(), "timestamp");
		op.add(Builder::transport, Transport._DESERIALIZER, "transport");
		op.add(Builder::transportAddress, JsonpDeserializer.stringDeserializer(), "transport_address");
		op.add(Builder::attributes, JsonpDeserializer.stringMapDeserializer(JsonpDeserializer.stringDeserializer()),
				"attributes");

	}

}
