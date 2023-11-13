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
import org.opensearch.client.json.JsonpSerializable;
import org.opensearch.client.json.ObjectBuilderDeserializer;
import org.opensearch.client.json.ObjectDeserializer;
import org.opensearch.client.util.ObjectBuilder;
import org.opensearch.client.util.ObjectBuilderBase;
import jakarta.json.stream.JsonGenerator;
import java.util.function.Function;
import javax.annotation.Nullable;

// typedef: _types.aggregations.VariableWidthHistogramAggregation

@JsonpDeserializable
public class VariableWidthHistogramAggregation implements AggregationVariant, JsonpSerializable {
	@Nullable
	private final String field;

	@Nullable
	private final Integer buckets;

	@Nullable
	private final Integer shardSize;

	@Nullable
	private final Integer initialBuffer;

	// ---------------------------------------------------------------------------------------------

	private VariableWidthHistogramAggregation(Builder builder) {

		this.field = builder.field;
		this.buckets = builder.buckets;
		this.shardSize = builder.shardSize;
		this.initialBuffer = builder.initialBuffer;

	}

	public static VariableWidthHistogramAggregation of(
			Function<Builder, ObjectBuilder<VariableWidthHistogramAggregation>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Aggregation variant kind.
	 */
	@Override
	public Aggregation.Kind _aggregationKind() {
		return Aggregation.Kind.VariableWidthHistogram;
	}

	/**
	 * API name: {@code field}
	 */
	@Nullable
	public final String field() {
		return this.field;
	}

	/**
	 * API name: {@code buckets}
	 */
	@Nullable
	public final Integer buckets() {
		return this.buckets;
	}

	/**
	 * API name: {@code shard_size}
	 */
	@Nullable
	public final Integer shardSize() {
		return this.shardSize;
	}

	/**
	 * API name: {@code initial_buffer}
	 */
	@Nullable
	public final Integer initialBuffer() {
		return this.initialBuffer;
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

		if (this.field != null) {
			generator.writeKey("field");
			generator.write(this.field);

		}
		if (this.buckets != null) {
			generator.writeKey("buckets");
			generator.write(this.buckets);

		}
		if (this.shardSize != null) {
			generator.writeKey("shard_size");
			generator.write(this.shardSize);

		}
		if (this.initialBuffer != null) {
			generator.writeKey("initial_buffer");
			generator.write(this.initialBuffer);

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link VariableWidthHistogramAggregation}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<VariableWidthHistogramAggregation> {
		@Nullable
		private String field;

		@Nullable
		private Integer buckets;

		@Nullable
		private Integer shardSize;

		@Nullable
		private Integer initialBuffer;

		/**
		 * API name: {@code field}
		 */
		public final Builder field(@Nullable String value) {
			this.field = value;
			return this;
		}

		/**
		 * API name: {@code buckets}
		 */
		public final Builder buckets(@Nullable Integer value) {
			this.buckets = value;
			return this;
		}

		/**
		 * API name: {@code shard_size}
		 */
		public final Builder shardSize(@Nullable Integer value) {
			this.shardSize = value;
			return this;
		}

		/**
		 * API name: {@code initial_buffer}
		 */
		public final Builder initialBuffer(@Nullable Integer value) {
			this.initialBuffer = value;
			return this;
		}

		/**
		 * Builds a {@link VariableWidthHistogramAggregation}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public VariableWidthHistogramAggregation build() {
			_checkSingleUse();

			return new VariableWidthHistogramAggregation(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link VariableWidthHistogramAggregation}
	 */
	public static final JsonpDeserializer<VariableWidthHistogramAggregation> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, VariableWidthHistogramAggregation::setupVariableWidthHistogramAggregationDeserializer);

	protected static void setupVariableWidthHistogramAggregationDeserializer(
			ObjectDeserializer<VariableWidthHistogramAggregation.Builder> op) {

		op.add(Builder::field, JsonpDeserializer.stringDeserializer(), "field");
		op.add(Builder::buckets, JsonpDeserializer.integerDeserializer(), "buckets");
		op.add(Builder::shardSize, JsonpDeserializer.integerDeserializer(), "shard_size");
		op.add(Builder::initialBuffer, JsonpDeserializer.integerDeserializer(), "initial_buffer");

	}

}
