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
import javax.annotation.Nullable;

// typedef: cluster.stats.ClusterOperatingSystem

@JsonpDeserializable
public class ClusterOperatingSystem implements JsonpSerializable {
	private final int allocatedProcessors;

	private final int availableProcessors;

	private final OperatingSystemMemoryInfo mem;

	private final List<ClusterOperatingSystemName> names;

	private final List<ClusterOperatingSystemPrettyName> prettyNames;

	private final List<ClusterOperatingSystemArchitecture> architectures;

	// ---------------------------------------------------------------------------------------------

	private ClusterOperatingSystem(Builder builder) {

		this.allocatedProcessors = ApiTypeHelper.requireNonNull(builder.allocatedProcessors, this,
				"allocatedProcessors");
		this.availableProcessors = ApiTypeHelper.requireNonNull(builder.availableProcessors, this,
				"availableProcessors");
		this.mem = ApiTypeHelper.requireNonNull(builder.mem, this, "mem");
		this.names = ApiTypeHelper.unmodifiableRequired(builder.names, this, "names");
		this.prettyNames = ApiTypeHelper.unmodifiableRequired(builder.prettyNames, this, "prettyNames");
		this.architectures = ApiTypeHelper.unmodifiable(builder.architectures);

	}

	public static ClusterOperatingSystem of(Function<Builder, ObjectBuilder<ClusterOperatingSystem>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code allocated_processors}
	 */
	public final int allocatedProcessors() {
		return this.allocatedProcessors;
	}

	/**
	 * Required - API name: {@code available_processors}
	 */
	public final int availableProcessors() {
		return this.availableProcessors;
	}

	/**
	 * Required - API name: {@code mem}
	 */
	public final OperatingSystemMemoryInfo mem() {
		return this.mem;
	}

	/**
	 * Required - API name: {@code names}
	 */
	public final List<ClusterOperatingSystemName> names() {
		return this.names;
	}

	/**
	 * Required - API name: {@code pretty_names}
	 */
	public final List<ClusterOperatingSystemPrettyName> prettyNames() {
		return this.prettyNames;
	}

	/**
	 * API name: {@code architectures}
	 */
	public final List<ClusterOperatingSystemArchitecture> architectures() {
		return this.architectures;
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

		generator.writeKey("allocated_processors");
		generator.write(this.allocatedProcessors);

		generator.writeKey("available_processors");
		generator.write(this.availableProcessors);

		generator.writeKey("mem");
		this.mem.serialize(generator, mapper);

		if (ApiTypeHelper.isDefined(this.names)) {
			generator.writeKey("names");
			generator.writeStartArray();
			for (ClusterOperatingSystemName item0 : this.names) {
				item0.serialize(generator, mapper);

			}
			generator.writeEnd();

		}
		if (ApiTypeHelper.isDefined(this.prettyNames)) {
			generator.writeKey("pretty_names");
			generator.writeStartArray();
			for (ClusterOperatingSystemPrettyName item0 : this.prettyNames) {
				item0.serialize(generator, mapper);

			}
			generator.writeEnd();

		}
		if (ApiTypeHelper.isDefined(this.architectures)) {
			generator.writeKey("architectures");
			generator.writeStartArray();
			for (ClusterOperatingSystemArchitecture item0 : this.architectures) {
				item0.serialize(generator, mapper);

			}
			generator.writeEnd();

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link ClusterOperatingSystem}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<ClusterOperatingSystem> {
		private Integer allocatedProcessors;

		private Integer availableProcessors;

		private OperatingSystemMemoryInfo mem;

		private List<ClusterOperatingSystemName> names;

		private List<ClusterOperatingSystemPrettyName> prettyNames;

		@Nullable
		private List<ClusterOperatingSystemArchitecture> architectures;

		/**
		 * Required - API name: {@code allocated_processors}
		 */
		public final Builder allocatedProcessors(int value) {
			this.allocatedProcessors = value;
			return this;
		}

		/**
		 * Required - API name: {@code available_processors}
		 */
		public final Builder availableProcessors(int value) {
			this.availableProcessors = value;
			return this;
		}

		/**
		 * Required - API name: {@code mem}
		 */
		public final Builder mem(OperatingSystemMemoryInfo value) {
			this.mem = value;
			return this;
		}

		/**
		 * Required - API name: {@code mem}
		 */
		public final Builder mem(
				Function<OperatingSystemMemoryInfo.Builder, ObjectBuilder<OperatingSystemMemoryInfo>> fn) {
			return this.mem(fn.apply(new OperatingSystemMemoryInfo.Builder()).build());
		}

		/**
		 * Required - API name: {@code names}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>names</code>.
		 */
		public final Builder names(List<ClusterOperatingSystemName> list) {
			this.names = _listAddAll(this.names, list);
			return this;
		}

		/**
		 * Required - API name: {@code names}
		 * <p>
		 * Adds one or more values to <code>names</code>.
		 */
		public final Builder names(ClusterOperatingSystemName value, ClusterOperatingSystemName... values) {
			this.names = _listAdd(this.names, value, values);
			return this;
		}

		/**
		 * Required - API name: {@code names}
		 * <p>
		 * Adds a value to <code>names</code> using a builder lambda.
		 */
		public final Builder names(
				Function<ClusterOperatingSystemName.Builder, ObjectBuilder<ClusterOperatingSystemName>> fn) {
			return names(fn.apply(new ClusterOperatingSystemName.Builder()).build());
		}

		/**
		 * Required - API name: {@code pretty_names}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>prettyNames</code>.
		 */
		public final Builder prettyNames(List<ClusterOperatingSystemPrettyName> list) {
			this.prettyNames = _listAddAll(this.prettyNames, list);
			return this;
		}

		/**
		 * Required - API name: {@code pretty_names}
		 * <p>
		 * Adds one or more values to <code>prettyNames</code>.
		 */
		public final Builder prettyNames(ClusterOperatingSystemPrettyName value,
				ClusterOperatingSystemPrettyName... values) {
			this.prettyNames = _listAdd(this.prettyNames, value, values);
			return this;
		}

		/**
		 * Required - API name: {@code pretty_names}
		 * <p>
		 * Adds a value to <code>prettyNames</code> using a builder lambda.
		 */
		public final Builder prettyNames(
				Function<ClusterOperatingSystemPrettyName.Builder, ObjectBuilder<ClusterOperatingSystemPrettyName>> fn) {
			return prettyNames(fn.apply(new ClusterOperatingSystemPrettyName.Builder()).build());
		}

		/**
		 * API name: {@code architectures}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>architectures</code>.
		 */
		public final Builder architectures(List<ClusterOperatingSystemArchitecture> list) {
			this.architectures = _listAddAll(this.architectures, list);
			return this;
		}

		/**
		 * API name: {@code architectures}
		 * <p>
		 * Adds one or more values to <code>architectures</code>.
		 */
		public final Builder architectures(ClusterOperatingSystemArchitecture value,
				ClusterOperatingSystemArchitecture... values) {
			this.architectures = _listAdd(this.architectures, value, values);
			return this;
		}

		/**
		 * API name: {@code architectures}
		 * <p>
		 * Adds a value to <code>architectures</code> using a builder lambda.
		 */
		public final Builder architectures(
				Function<ClusterOperatingSystemArchitecture.Builder,
						ObjectBuilder<ClusterOperatingSystemArchitecture>> fn) {
			return architectures(fn.apply(new ClusterOperatingSystemArchitecture.Builder()).build());
		}

		/**
		 * Builds a {@link ClusterOperatingSystem}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public ClusterOperatingSystem build() {
			_checkSingleUse();

			return new ClusterOperatingSystem(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link ClusterOperatingSystem}
	 */
	public static final JsonpDeserializer<ClusterOperatingSystem> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, ClusterOperatingSystem::setupClusterOperatingSystemDeserializer);

	protected static void setupClusterOperatingSystemDeserializer(
			ObjectDeserializer<ClusterOperatingSystem.Builder> op) {

		op.add(Builder::allocatedProcessors, JsonpDeserializer.integerDeserializer(), "allocated_processors");
		op.add(Builder::availableProcessors, JsonpDeserializer.integerDeserializer(), "available_processors");
		op.add(Builder::mem, OperatingSystemMemoryInfo._DESERIALIZER, "mem");
		op.add(Builder::names, JsonpDeserializer.arrayDeserializer(ClusterOperatingSystemName._DESERIALIZER), "names");
		op.add(Builder::prettyNames,
				JsonpDeserializer.arrayDeserializer(ClusterOperatingSystemPrettyName._DESERIALIZER), "pretty_names");
		op.add(Builder::architectures,
				JsonpDeserializer.arrayDeserializer(ClusterOperatingSystemArchitecture._DESERIALIZER), "architectures");

	}

}
