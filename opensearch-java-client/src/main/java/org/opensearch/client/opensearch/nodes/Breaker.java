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
import java.util.function.Function;

// typedef: nodes._types.Breaker

@JsonpDeserializable
public class Breaker implements JsonpSerializable {
	private final String estimatedSize;

	private final long estimatedSizeInBytes;

	private final String limitSize;

	private final long limitSizeInBytes;

	private final float overhead;

	private final float tripped;

	// ---------------------------------------------------------------------------------------------

	private Breaker(Builder builder) {

		this.estimatedSize = ApiTypeHelper.requireNonNull(builder.estimatedSize, this, "estimatedSize");
		this.estimatedSizeInBytes = ApiTypeHelper.requireNonNull(builder.estimatedSizeInBytes, this,
				"estimatedSizeInBytes");
		this.limitSize = ApiTypeHelper.requireNonNull(builder.limitSize, this, "limitSize");
		this.limitSizeInBytes = ApiTypeHelper.requireNonNull(builder.limitSizeInBytes, this, "limitSizeInBytes");
		this.overhead = ApiTypeHelper.requireNonNull(builder.overhead, this, "overhead");
		this.tripped = ApiTypeHelper.requireNonNull(builder.tripped, this, "tripped");

	}

	public static Breaker of(Function<Builder, ObjectBuilder<Breaker>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code estimated_size}
	 */
	public final String estimatedSize() {
		return this.estimatedSize;
	}

	/**
	 * Required - API name: {@code estimated_size_in_bytes}
	 */
	public final long estimatedSizeInBytes() {
		return this.estimatedSizeInBytes;
	}

	/**
	 * Required - API name: {@code limit_size}
	 */
	public final String limitSize() {
		return this.limitSize;
	}

	/**
	 * Required - API name: {@code limit_size_in_bytes}
	 */
	public final long limitSizeInBytes() {
		return this.limitSizeInBytes;
	}

	/**
	 * Required - API name: {@code overhead}
	 */
	public final float overhead() {
		return this.overhead;
	}

	/**
	 * Required - API name: {@code tripped}
	 */
	public final float tripped() {
		return this.tripped;
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

		generator.writeKey("estimated_size");
		generator.write(this.estimatedSize);

		generator.writeKey("estimated_size_in_bytes");
		generator.write(this.estimatedSizeInBytes);

		generator.writeKey("limit_size");
		generator.write(this.limitSize);

		generator.writeKey("limit_size_in_bytes");
		generator.write(this.limitSizeInBytes);

		generator.writeKey("overhead");
		generator.write(this.overhead);

		generator.writeKey("tripped");
		generator.write(this.tripped);

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link Breaker}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<Breaker> {
		private String estimatedSize;

		private Long estimatedSizeInBytes;

		private String limitSize;

		private Long limitSizeInBytes;

		private Float overhead;

		private Float tripped;

		/**
		 * Required - API name: {@code estimated_size}
		 */
		public final Builder estimatedSize(String value) {
			this.estimatedSize = value;
			return this;
		}

		/**
		 * Required - API name: {@code estimated_size_in_bytes}
		 */
		public final Builder estimatedSizeInBytes(long value) {
			this.estimatedSizeInBytes = value;
			return this;
		}

		/**
		 * Required - API name: {@code limit_size}
		 */
		public final Builder limitSize(String value) {
			this.limitSize = value;
			return this;
		}

		/**
		 * Required - API name: {@code limit_size_in_bytes}
		 */
		public final Builder limitSizeInBytes(long value) {
			this.limitSizeInBytes = value;
			return this;
		}

		/**
		 * Required - API name: {@code overhead}
		 */
		public final Builder overhead(float value) {
			this.overhead = value;
			return this;
		}

		/**
		 * Required - API name: {@code tripped}
		 */
		public final Builder tripped(float value) {
			this.tripped = value;
			return this;
		}

		/**
		 * Builds a {@link Breaker}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public Breaker build() {
			_checkSingleUse();

			return new Breaker(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link Breaker}
	 */
	public static final JsonpDeserializer<Breaker> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			Breaker::setupBreakerDeserializer);

	protected static void setupBreakerDeserializer(ObjectDeserializer<Breaker.Builder> op) {

		op.add(Builder::estimatedSize, JsonpDeserializer.stringDeserializer(), "estimated_size");
		op.add(Builder::estimatedSizeInBytes, JsonpDeserializer.longDeserializer(), "estimated_size_in_bytes");
		op.add(Builder::limitSize, JsonpDeserializer.stringDeserializer(), "limit_size");
		op.add(Builder::limitSizeInBytes, JsonpDeserializer.longDeserializer(), "limit_size_in_bytes");
		op.add(Builder::overhead, JsonpDeserializer.floatDeserializer(), "overhead");
		op.add(Builder::tripped, JsonpDeserializer.floatDeserializer(), "tripped");

	}

}
