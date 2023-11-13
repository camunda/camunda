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

package org.opensearch.client.opensearch.cluster.reroute;

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

// typedef: cluster.reroute.RerouteParameters

@JsonpDeserializable
public class RerouteParameters implements JsonpSerializable {
	private final boolean allowPrimary;

	private final String index;

	private final String node;

	private final int shard;

	@Nullable
	private final String fromNode;

	@Nullable
	private final String toNode;

	// ---------------------------------------------------------------------------------------------

	private RerouteParameters(Builder builder) {

		this.allowPrimary = ApiTypeHelper.requireNonNull(builder.allowPrimary, this, "allowPrimary");
		this.index = ApiTypeHelper.requireNonNull(builder.index, this, "index");
		this.node = ApiTypeHelper.requireNonNull(builder.node, this, "node");
		this.shard = ApiTypeHelper.requireNonNull(builder.shard, this, "shard");
		this.fromNode = builder.fromNode;
		this.toNode = builder.toNode;

	}

	public static RerouteParameters of(Function<Builder, ObjectBuilder<RerouteParameters>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code allow_primary}
	 */
	public final boolean allowPrimary() {
		return this.allowPrimary;
	}

	/**
	 * Required - API name: {@code index}
	 */
	public final String index() {
		return this.index;
	}

	/**
	 * Required - API name: {@code node}
	 */
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
	 * API name: {@code from_node}
	 */
	@Nullable
	public final String fromNode() {
		return this.fromNode;
	}

	/**
	 * API name: {@code to_node}
	 */
	@Nullable
	public final String toNode() {
		return this.toNode;
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

		generator.writeKey("allow_primary");
		generator.write(this.allowPrimary);

		generator.writeKey("index");
		generator.write(this.index);

		generator.writeKey("node");
		generator.write(this.node);

		generator.writeKey("shard");
		generator.write(this.shard);

		if (this.fromNode != null) {
			generator.writeKey("from_node");
			generator.write(this.fromNode);

		}
		if (this.toNode != null) {
			generator.writeKey("to_node");
			generator.write(this.toNode);

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link RerouteParameters}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<RerouteParameters> {
		private Boolean allowPrimary;

		private String index;

		private String node;

		private Integer shard;

		@Nullable
		private String fromNode;

		@Nullable
		private String toNode;

		/**
		 * Required - API name: {@code allow_primary}
		 */
		public final Builder allowPrimary(boolean value) {
			this.allowPrimary = value;
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
		 * Required - API name: {@code node}
		 */
		public final Builder node(String value) {
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
		 * API name: {@code from_node}
		 */
		public final Builder fromNode(@Nullable String value) {
			this.fromNode = value;
			return this;
		}

		/**
		 * API name: {@code to_node}
		 */
		public final Builder toNode(@Nullable String value) {
			this.toNode = value;
			return this;
		}

		/**
		 * Builds a {@link RerouteParameters}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public RerouteParameters build() {
			_checkSingleUse();

			return new RerouteParameters(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link RerouteParameters}
	 */
	public static final JsonpDeserializer<RerouteParameters> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, RerouteParameters::setupRerouteParametersDeserializer);

	protected static void setupRerouteParametersDeserializer(ObjectDeserializer<RerouteParameters.Builder> op) {

		op.add(Builder::allowPrimary, JsonpDeserializer.booleanDeserializer(), "allow_primary");
		op.add(Builder::index, JsonpDeserializer.stringDeserializer(), "index");
		op.add(Builder::node, JsonpDeserializer.stringDeserializer(), "node");
		op.add(Builder::shard, JsonpDeserializer.integerDeserializer(), "shard");
		op.add(Builder::fromNode, JsonpDeserializer.stringDeserializer(), "from_node");
		op.add(Builder::toNode, JsonpDeserializer.stringDeserializer(), "to_node");

	}

}
