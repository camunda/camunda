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
import java.util.List;
import java.util.function.Function;

// typedef: cluster.stats.ClusterJvm

@JsonpDeserializable
public class ClusterJvm implements JsonpSerializable {
	private final long maxUptimeInMillis;

	private final ClusterJvmMemory mem;

	private final long threads;

	private final List<ClusterJvmVersion> versions;

	// ---------------------------------------------------------------------------------------------

	private ClusterJvm(Builder builder) {

		this.maxUptimeInMillis = ApiTypeHelper.requireNonNull(builder.maxUptimeInMillis, this, "maxUptimeInMillis");
		this.mem = ApiTypeHelper.requireNonNull(builder.mem, this, "mem");
		this.threads = ApiTypeHelper.requireNonNull(builder.threads, this, "threads");
		this.versions = ApiTypeHelper.unmodifiableRequired(builder.versions, this, "versions");

	}

	public static ClusterJvm of(Function<Builder, ObjectBuilder<ClusterJvm>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code max_uptime_in_millis}
	 */
	public final long maxUptimeInMillis() {
		return this.maxUptimeInMillis;
	}

	/**
	 * Required - API name: {@code mem}
	 */
	public final ClusterJvmMemory mem() {
		return this.mem;
	}

	/**
	 * Required - API name: {@code threads}
	 */
	public final long threads() {
		return this.threads;
	}

	/**
	 * Required - API name: {@code versions}
	 */
	public final List<ClusterJvmVersion> versions() {
		return this.versions;
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

		generator.writeKey("max_uptime_in_millis");
		generator.write(this.maxUptimeInMillis);

		generator.writeKey("mem");
		this.mem.serialize(generator, mapper);

		generator.writeKey("threads");
		generator.write(this.threads);

		if (ApiTypeHelper.isDefined(this.versions)) {
			generator.writeKey("versions");
			generator.writeStartArray();
			for (ClusterJvmVersion item0 : this.versions) {
				item0.serialize(generator, mapper);

			}
			generator.writeEnd();

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link ClusterJvm}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<ClusterJvm> {
		private Long maxUptimeInMillis;

		private ClusterJvmMemory mem;

		private Long threads;

		private List<ClusterJvmVersion> versions;

		/**
		 * Required - API name: {@code max_uptime_in_millis}
		 */
		public final Builder maxUptimeInMillis(long value) {
			this.maxUptimeInMillis = value;
			return this;
		}

		/**
		 * Required - API name: {@code mem}
		 */
		public final Builder mem(ClusterJvmMemory value) {
			this.mem = value;
			return this;
		}

		/**
		 * Required - API name: {@code mem}
		 */
		public final Builder mem(Function<ClusterJvmMemory.Builder, ObjectBuilder<ClusterJvmMemory>> fn) {
			return this.mem(fn.apply(new ClusterJvmMemory.Builder()).build());
		}

		/**
		 * Required - API name: {@code threads}
		 */
		public final Builder threads(long value) {
			this.threads = value;
			return this;
		}

		/**
		 * Required - API name: {@code versions}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>versions</code>.
		 */
		public final Builder versions(List<ClusterJvmVersion> list) {
			this.versions = _listAddAll(this.versions, list);
			return this;
		}

		/**
		 * Required - API name: {@code versions}
		 * <p>
		 * Adds one or more values to <code>versions</code>.
		 */
		public final Builder versions(ClusterJvmVersion value, ClusterJvmVersion... values) {
			this.versions = _listAdd(this.versions, value, values);
			return this;
		}

		/**
		 * Required - API name: {@code versions}
		 * <p>
		 * Adds a value to <code>versions</code> using a builder lambda.
		 */
		public final Builder versions(Function<ClusterJvmVersion.Builder, ObjectBuilder<ClusterJvmVersion>> fn) {
			return versions(fn.apply(new ClusterJvmVersion.Builder()).build());
		}

		/**
		 * Builds a {@link ClusterJvm}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public ClusterJvm build() {
			_checkSingleUse();

			return new ClusterJvm(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link ClusterJvm}
	 */
	public static final JsonpDeserializer<ClusterJvm> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			ClusterJvm::setupClusterJvmDeserializer);

	protected static void setupClusterJvmDeserializer(ObjectDeserializer<ClusterJvm.Builder> op) {

		op.add(Builder::maxUptimeInMillis, JsonpDeserializer.longDeserializer(), "max_uptime_in_millis");
		op.add(Builder::mem, ClusterJvmMemory._DESERIALIZER, "mem");
		op.add(Builder::threads, JsonpDeserializer.longDeserializer(), "threads");
		op.add(Builder::versions, JsonpDeserializer.arrayDeserializer(ClusterJvmVersion._DESERIALIZER), "versions");

	}

}
