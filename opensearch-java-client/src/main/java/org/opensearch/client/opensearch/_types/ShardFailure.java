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

package org.opensearch.client.opensearch._types;

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

// typedef: _types.ShardFailure

@JsonpDeserializable
public class ShardFailure implements JsonpSerializable {
	@Nullable
	private final String index;

	@Nullable
	private final String node;

	private final ErrorCause reason;

	private final int shard;

	@Nullable
	private final String status;

	// ---------------------------------------------------------------------------------------------

	private ShardFailure(Builder builder) {

		this.index = builder.index;
		this.node = builder.node;
		this.reason = ApiTypeHelper.requireNonNull(builder.reason, this, "reason");
		this.shard = ApiTypeHelper.requireNonNull(builder.shard, this, "shard");
		this.status = builder.status;

	}

	public static ShardFailure of(Function<Builder, ObjectBuilder<ShardFailure>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * API name: {@code index}
	 */
	@Nullable
	public final String index() {
		return this.index;
	}

	/**
	 * API name: {@code node}
	 */
	@Nullable
	public final String node() {
		return this.node;
	}

	/**
	 * Required - API name: {@code reason}
	 */
	public final ErrorCause reason() {
		return this.reason;
	}

	/**
	 * Required - API name: {@code shard}
	 */
	public final int shard() {
		return this.shard;
	}

	/**
	 * API name: {@code status}
	 */
	@Nullable
	public final String status() {
		return this.status;
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

		if (this.index != null) {
			generator.writeKey("index");
			generator.write(this.index);

		}
		if (this.node != null) {
			generator.writeKey("node");
			generator.write(this.node);

		}
		generator.writeKey("reason");
		this.reason.serialize(generator, mapper);

		generator.writeKey("shard");
		generator.write(this.shard);

		if (this.status != null) {
			generator.writeKey("status");
			generator.write(this.status);

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link ShardFailure}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<ShardFailure> {
		@Nullable
		private String index;

		@Nullable
		private String node;

		private ErrorCause reason;

		private Integer shard;

		@Nullable
		private String status;

		/**
		 * API name: {@code index}
		 */
		public final Builder index(@Nullable String value) {
			this.index = value;
			return this;
		}

		/**
		 * API name: {@code node}
		 */
		public final Builder node(@Nullable String value) {
			this.node = value;
			return this;
		}

		/**
		 * Required - API name: {@code reason}
		 */
		public final Builder reason(ErrorCause value) {
			this.reason = value;
			return this;
		}

		/**
		 * Required - API name: {@code reason}
		 */
		public final Builder reason(Function<ErrorCause.Builder, ObjectBuilder<ErrorCause>> fn) {
			return this.reason(fn.apply(new ErrorCause.Builder()).build());
		}

		/**
		 * Required - API name: {@code shard}
		 */
		public final Builder shard(int value) {
			this.shard = value;
			return this;
		}

		/**
		 * API name: {@code status}
		 */
		public final Builder status(@Nullable String value) {
			this.status = value;
			return this;
		}

		/**
		 * Builds a {@link ShardFailure}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public ShardFailure build() {
			_checkSingleUse();

			return new ShardFailure(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link ShardFailure}
	 */
	public static final JsonpDeserializer<ShardFailure> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			ShardFailure::setupShardFailureDeserializer);

	protected static void setupShardFailureDeserializer(ObjectDeserializer<ShardFailure.Builder> op) {

		op.add(Builder::index, JsonpDeserializer.stringDeserializer(), "index");
		op.add(Builder::node, JsonpDeserializer.stringDeserializer(), "node");
		op.add(Builder::reason, ErrorCause._DESERIALIZER, "reason");
		op.add(Builder::shard, JsonpDeserializer.integerDeserializer(), "shard");
		op.add(Builder::status, JsonpDeserializer.stringDeserializer(), "status");

	}

}
