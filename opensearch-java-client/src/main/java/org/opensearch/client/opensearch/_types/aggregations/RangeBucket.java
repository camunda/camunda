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

package org.opensearch.client.opensearch._types.aggregations;

import org.opensearch.client.json.JsonpDeserializable;
import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.ObjectBuilderDeserializer;
import org.opensearch.client.json.ObjectDeserializer;
import org.opensearch.client.util.ObjectBuilder;
import jakarta.json.stream.JsonGenerator;
import java.util.function.Function;
import javax.annotation.Nullable;

// typedef: _types.aggregations.RangeBucket


@JsonpDeserializable
public class RangeBucket extends MultiBucketBase {

	@Nullable
	private final String key;
	@Nullable
	private final Double from;

	@Nullable
	private final Double to;

	@Nullable
	private final String fromAsString;

	@Nullable
	private final String toAsString;

	// ---------------------------------------------------------------------------------------------

	private RangeBucket(Builder builder) {
		super(builder);

		this.key = builder.key;
		this.from = builder.from;
		this.to = builder.to;
		this.fromAsString = builder.fromAsString;
		this.toAsString = builder.toAsString;

	}

	public static RangeBucket of(Function<Builder, ObjectBuilder<RangeBucket>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * API name: {@code key}
	 */
	@Nullable
	public final String key() {
		return this.key;
	}

	/**
	 * API name: {@code from}
	 */
	@Nullable
	public final Double from() {
		return this.from;
	}

	/**
	 * API name: {@code to}
	 */
	@Nullable
	public final Double to() {
		return this.to;
	}

	/**
	 * API name: {@code from_as_string}
	 */
	@Nullable
	public final String fromAsString() {
		return this.fromAsString;
	}

	/**
	 * API name: {@code to_as_string}
	 */
	@Nullable
	public final String toAsString() {
		return this.toAsString;
	}

	protected void serializeInternal(JsonGenerator generator, JsonpMapper mapper) {

		super.serializeInternal(generator, mapper);
		if (this.key != null) {
			generator.writeKey("key");
			generator.write(this.key);

		}
		if (this.from != null) {
			generator.writeKey("from");
			generator.write(this.from);

		}
		if (this.to != null) {
			generator.writeKey("to");
			generator.write(this.to);

		}
		if (this.fromAsString != null) {
			generator.writeKey("from_as_string");
			generator.write(this.fromAsString);

		}
		if (this.toAsString != null) {
			generator.writeKey("to_as_string");
			generator.write(this.toAsString);

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link RangeBucket}.
	 */

	public static class Builder extends MultiBucketBase.AbstractBuilder<Builder> implements ObjectBuilder<RangeBucket> {

		@Nullable
		private String key;
		@Nullable
		private Double from;

		@Nullable
		private Double to;

		@Nullable
		private String fromAsString;

		@Nullable
		private String toAsString;

		/**
		 * API name: {@code key}
		 */
		public final Builder key(String value) {
			this.key = value;
			return this;
		}

		/**
		 * API name: {@code from}
		 */
		public final Builder from(@Nullable Double value) {
			this.from = value;
			return this;
		}

		/**
		 * API name: {@code to}
		 */
		public final Builder to(@Nullable Double value) {
			this.to = value;
			return this;
		}

		/**
		 * API name: {@code from_as_string}
		 */
		public final Builder fromAsString(@Nullable String value) {
			this.fromAsString = value;
			return this;
		}

		/**
		 * API name: {@code to_as_string}
		 */
		public final Builder toAsString(@Nullable String value) {
			this.toAsString = value;
			return this;
		}

		@Override
		protected Builder self() {
			return this;
		}

		/**
		 * Builds a {@link RangeBucket}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public RangeBucket build() {
			_checkSingleUse();

			return new RangeBucket(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link RangeBucket}
	 */
	public static final JsonpDeserializer<RangeBucket> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			RangeBucket::setupRangeBucketDeserializer);

	protected static void setupRangeBucketDeserializer(ObjectDeserializer<RangeBucket.Builder> op) {
		setupMultiBucketBaseDeserializer(op);
		op.add(Builder::key, JsonpDeserializer.stringDeserializer(), "key");
		op.add(Builder::from, JsonpDeserializer.doubleDeserializer(), "from");
		op.add(Builder::to, JsonpDeserializer.doubleDeserializer(), "to");
		op.add(Builder::fromAsString, JsonpDeserializer.stringDeserializer(), "from_as_string");
		op.add(Builder::toAsString, JsonpDeserializer.stringDeserializer(), "to_as_string");

	}

}
