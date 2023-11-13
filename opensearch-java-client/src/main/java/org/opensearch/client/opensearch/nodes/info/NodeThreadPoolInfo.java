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

// typedef: nodes.info.NodeThreadPoolInfo

@JsonpDeserializable
public class NodeThreadPoolInfo implements JsonpSerializable {
	@Nullable
	private final Integer core;

	@Nullable
	private final String keepAlive;

	@Nullable
	private final Integer max;

	private final int queueSize;

	@Nullable
	private final Integer size;

	private final String type;

	// ---------------------------------------------------------------------------------------------

	private NodeThreadPoolInfo(Builder builder) {

		this.core = builder.core;
		this.keepAlive = builder.keepAlive;
		this.max = builder.max;
		this.queueSize = ApiTypeHelper.requireNonNull(builder.queueSize, this, "queueSize");
		this.size = builder.size;
		this.type = ApiTypeHelper.requireNonNull(builder.type, this, "type");

	}

	public static NodeThreadPoolInfo of(Function<Builder, ObjectBuilder<NodeThreadPoolInfo>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * API name: {@code core}
	 */
	@Nullable
	public final Integer core() {
		return this.core;
	}

	/**
	 * API name: {@code keep_alive}
	 */
	@Nullable
	public final String keepAlive() {
		return this.keepAlive;
	}

	/**
	 * API name: {@code max}
	 */
	@Nullable
	public final Integer max() {
		return this.max;
	}

	/**
	 * Required - API name: {@code queue_size}
	 */
	public final int queueSize() {
		return this.queueSize;
	}

	/**
	 * API name: {@code size}
	 */
	@Nullable
	public final Integer size() {
		return this.size;
	}

	/**
	 * Required - API name: {@code type}
	 */
	public final String type() {
		return this.type;
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

		if (this.core != null) {
			generator.writeKey("core");
			generator.write(this.core);

		}
		if (this.keepAlive != null) {
			generator.writeKey("keep_alive");
			generator.write(this.keepAlive);

		}
		if (this.max != null) {
			generator.writeKey("max");
			generator.write(this.max);

		}
		generator.writeKey("queue_size");
		generator.write(this.queueSize);

		if (this.size != null) {
			generator.writeKey("size");
			generator.write(this.size);

		}
		generator.writeKey("type");
		generator.write(this.type);

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link NodeThreadPoolInfo}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<NodeThreadPoolInfo> {
		@Nullable
		private Integer core;

		@Nullable
		private String keepAlive;

		@Nullable
		private Integer max;

		private Integer queueSize;

		@Nullable
		private Integer size;

		private String type;

		/**
		 * API name: {@code core}
		 */
		public final Builder core(@Nullable Integer value) {
			this.core = value;
			return this;
		}

		/**
		 * API name: {@code keep_alive}
		 */
		public final Builder keepAlive(@Nullable String value) {
			this.keepAlive = value;
			return this;
		}

		/**
		 * API name: {@code max}
		 */
		public final Builder max(@Nullable Integer value) {
			this.max = value;
			return this;
		}

		/**
		 * Required - API name: {@code queue_size}
		 */
		public final Builder queueSize(int value) {
			this.queueSize = value;
			return this;
		}

		/**
		 * API name: {@code size}
		 */
		public final Builder size(@Nullable Integer value) {
			this.size = value;
			return this;
		}

		/**
		 * Required - API name: {@code type}
		 */
		public final Builder type(String value) {
			this.type = value;
			return this;
		}

		/**
		 * Builds a {@link NodeThreadPoolInfo}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public NodeThreadPoolInfo build() {
			_checkSingleUse();

			return new NodeThreadPoolInfo(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link NodeThreadPoolInfo}
	 */
	public static final JsonpDeserializer<NodeThreadPoolInfo> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, NodeThreadPoolInfo::setupNodeThreadPoolInfoDeserializer);

	protected static void setupNodeThreadPoolInfoDeserializer(ObjectDeserializer<NodeThreadPoolInfo.Builder> op) {

		op.add(Builder::core, JsonpDeserializer.integerDeserializer(), "core");
		op.add(Builder::keepAlive, JsonpDeserializer.stringDeserializer(), "keep_alive");
		op.add(Builder::max, JsonpDeserializer.integerDeserializer(), "max");
		op.add(Builder::queueSize, JsonpDeserializer.integerDeserializer(), "queue_size");
		op.add(Builder::size, JsonpDeserializer.integerDeserializer(), "size");
		op.add(Builder::type, JsonpDeserializer.stringDeserializer(), "type");

	}

}
