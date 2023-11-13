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

// typedef: nodes._types.Jvm


@JsonpDeserializable
public class Jvm implements JsonpSerializable {
	private final Map<String, NodeBufferPool> bufferPools;

	private final JvmClasses classes;

	private final GarbageCollector gc;

	private final MemoryStats mem;

	private final JvmThreads threads;

	private final long timestamp;

	private final long uptimeInMillis;

	// ---------------------------------------------------------------------------------------------

	private Jvm(Builder builder) {

		this.bufferPools = ApiTypeHelper.unmodifiableRequired(builder.bufferPools, this, "bufferPools");
		this.classes = ApiTypeHelper.requireNonNull(builder.classes, this, "classes");
		this.gc = ApiTypeHelper.requireNonNull(builder.gc, this, "gc");
		this.mem = ApiTypeHelper.requireNonNull(builder.mem, this, "mem");
		this.threads = ApiTypeHelper.requireNonNull(builder.threads, this, "threads");
		this.timestamp = ApiTypeHelper.requireNonNull(builder.timestamp, this, "timestamp");
		this.uptimeInMillis = ApiTypeHelper.requireNonNull(builder.uptimeInMillis, this, "uptimeInMillis");

	}

	public static Jvm of(Function<Builder, ObjectBuilder<Jvm>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code buffer_pools}
	 */
	public final Map<String, NodeBufferPool> bufferPools() {
		return this.bufferPools;
	}

	/**
	 * Required - API name: {@code classes}
	 */
	public final JvmClasses classes() {
		return this.classes;
	}

	/**
	 * Required - API name: {@code gc}
	 */
	public final GarbageCollector gc() {
		return this.gc;
	}

	/**
	 * Required - API name: {@code mem}
	 */
	public final MemoryStats mem() {
		return this.mem;
	}

	/**
	 * Required - API name: {@code threads}
	 */
	public final JvmThreads threads() {
		return this.threads;
	}

	/**
	 * Required - API name: {@code timestamp}
	 */
	public final long timestamp() {
		return this.timestamp;
	}

	/**
	 * Required - API name: {@code uptime_in_millis}
	 */
	public final long uptimeInMillis() {
		return this.uptimeInMillis;
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

		if (ApiTypeHelper.isDefined(this.bufferPools)) {
			generator.writeKey("buffer_pools");
			generator.writeStartObject();
			for (Map.Entry<String, NodeBufferPool> item0 : this.bufferPools.entrySet()) {
				generator.writeKey(item0.getKey());
				item0.getValue().serialize(generator, mapper);

			}
			generator.writeEnd();

		}
		generator.writeKey("classes");
		this.classes.serialize(generator, mapper);

		generator.writeKey("gc");
		this.gc.serialize(generator, mapper);

		generator.writeKey("mem");
		this.mem.serialize(generator, mapper);

		generator.writeKey("threads");
		this.threads.serialize(generator, mapper);

		generator.writeKey("timestamp");
		generator.write(this.timestamp);

		generator.writeKey("uptime_in_millis");
		generator.write(this.uptimeInMillis);

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link Jvm}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<Jvm> {
		private Map<String, NodeBufferPool> bufferPools;

		private JvmClasses classes;

		private GarbageCollector gc;

		private MemoryStats mem;

		private JvmThreads threads;

		private Long timestamp;

		private Long uptimeInMillis;

		/**
		 * Required - API name: {@code buffer_pools}
		 * <p>
		 * Adds all entries of <code>map</code> to <code>bufferPools</code>.
		 */
		public final Builder bufferPools(Map<String, NodeBufferPool> map) {
			this.bufferPools = _mapPutAll(this.bufferPools, map);
			return this;
		}

		/**
		 * Required - API name: {@code buffer_pools}
		 * <p>
		 * Adds an entry to <code>bufferPools</code>.
		 */
		public final Builder bufferPools(String key, NodeBufferPool value) {
			this.bufferPools = _mapPut(this.bufferPools, key, value);
			return this;
		}

		/**
		 * Required - API name: {@code buffer_pools}
		 * <p>
		 * Adds an entry to <code>bufferPools</code> using a builder lambda.
		 */
		public final Builder bufferPools(String key,
				Function<NodeBufferPool.Builder, ObjectBuilder<NodeBufferPool>> fn) {
			return bufferPools(key, fn.apply(new NodeBufferPool.Builder()).build());
		}

		/**
		 * Required - API name: {@code classes}
		 */
		public final Builder classes(JvmClasses value) {
			this.classes = value;
			return this;
		}

		/**
		 * Required - API name: {@code classes}
		 */
		public final Builder classes(Function<JvmClasses.Builder, ObjectBuilder<JvmClasses>> fn) {
			return this.classes(fn.apply(new JvmClasses.Builder()).build());
		}

		/**
		 * Required - API name: {@code gc}
		 */
		public final Builder gc(GarbageCollector value) {
			this.gc = value;
			return this;
		}

		/**
		 * Required - API name: {@code gc}
		 */
		public final Builder gc(Function<GarbageCollector.Builder, ObjectBuilder<GarbageCollector>> fn) {
			return this.gc(fn.apply(new GarbageCollector.Builder()).build());
		}

		/**
		 * Required - API name: {@code mem}
		 */
		public final Builder mem(MemoryStats value) {
			this.mem = value;
			return this;
		}

		/**
		 * Required - API name: {@code mem}
		 */
		public final Builder mem(Function<MemoryStats.Builder, ObjectBuilder<MemoryStats>> fn) {
			return this.mem(fn.apply(new MemoryStats.Builder()).build());
		}

		/**
		 * Required - API name: {@code threads}
		 */
		public final Builder threads(JvmThreads value) {
			this.threads = value;
			return this;
		}

		/**
		 * Required - API name: {@code threads}
		 */
		public final Builder threads(Function<JvmThreads.Builder, ObjectBuilder<JvmThreads>> fn) {
			return this.threads(fn.apply(new JvmThreads.Builder()).build());
		}

		/**
		 * Required - API name: {@code timestamp}
		 */
		public final Builder timestamp(long value) {
			this.timestamp = value;
			return this;
		}

		/**
		 * Required - API name: {@code uptime_in_millis}
		 */
		public final Builder uptimeInMillis(long value) {
			this.uptimeInMillis = value;
			return this;
		}

		/**
		 * Builds a {@link Jvm}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public Jvm build() {
			_checkSingleUse();

			return new Jvm(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link Jvm}
	 */
	public static final JsonpDeserializer<Jvm> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			Jvm::setupJvmDeserializer);

	protected static void setupJvmDeserializer(ObjectDeserializer<Jvm.Builder> op) {

		op.add(Builder::bufferPools, JsonpDeserializer.stringMapDeserializer(NodeBufferPool._DESERIALIZER),
				"buffer_pools");
		op.add(Builder::classes, JvmClasses._DESERIALIZER, "classes");
		op.add(Builder::gc, GarbageCollector._DESERIALIZER, "gc");
		op.add(Builder::mem, MemoryStats._DESERIALIZER, "mem");
		op.add(Builder::threads, JvmThreads._DESERIALIZER, "threads");
		op.add(Builder::timestamp, JsonpDeserializer.longDeserializer(), "timestamp");
		op.add(Builder::uptimeInMillis, JsonpDeserializer.longDeserializer(), "uptime_in_millis");

	}

}
