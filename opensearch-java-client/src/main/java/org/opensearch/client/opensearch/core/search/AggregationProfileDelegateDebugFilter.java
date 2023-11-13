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

package org.opensearch.client.opensearch.core.search;

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

// typedef: _global.search._types.AggregationProfileDelegateDebugFilter


@JsonpDeserializable
public class AggregationProfileDelegateDebugFilter implements JsonpSerializable {
	@Nullable
	private final Integer resultsFromMetadata;

	@Nullable
	private final String query;

	@Nullable
	private final String specializedFor;

	// ---------------------------------------------------------------------------------------------

	private AggregationProfileDelegateDebugFilter(Builder builder) {

		this.resultsFromMetadata = builder.resultsFromMetadata;
		this.query = builder.query;
		this.specializedFor = builder.specializedFor;

	}

	public static AggregationProfileDelegateDebugFilter of(
			Function<Builder, ObjectBuilder<AggregationProfileDelegateDebugFilter>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * API name: {@code results_from_metadata}
	 */
	@Nullable
	public final Integer resultsFromMetadata() {
		return this.resultsFromMetadata;
	}

	/**
	 * API name: {@code query}
	 */
	@Nullable
	public final String query() {
		return this.query;
	}

	/**
	 * API name: {@code specialized_for}
	 */
	@Nullable
	public final String specializedFor() {
		return this.specializedFor;
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

		if (this.resultsFromMetadata != null) {
			generator.writeKey("results_from_metadata");
			generator.write(this.resultsFromMetadata);

		}
		if (this.query != null) {
			generator.writeKey("query");
			generator.write(this.query);

		}
		if (this.specializedFor != null) {
			generator.writeKey("specialized_for");
			generator.write(this.specializedFor);

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link AggregationProfileDelegateDebugFilter}.
	 */

	public static class Builder extends ObjectBuilderBase
			implements
				ObjectBuilder<AggregationProfileDelegateDebugFilter> {
		@Nullable
		private Integer resultsFromMetadata;

		@Nullable
		private String query;

		@Nullable
		private String specializedFor;

		/**
		 * API name: {@code results_from_metadata}
		 */
		public final Builder resultsFromMetadata(@Nullable Integer value) {
			this.resultsFromMetadata = value;
			return this;
		}

		/**
		 * API name: {@code query}
		 */
		public final Builder query(@Nullable String value) {
			this.query = value;
			return this;
		}

		/**
		 * API name: {@code specialized_for}
		 */
		public final Builder specializedFor(@Nullable String value) {
			this.specializedFor = value;
			return this;
		}

		/**
		 * Builds a {@link AggregationProfileDelegateDebugFilter}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public AggregationProfileDelegateDebugFilter build() {
			_checkSingleUse();

			return new AggregationProfileDelegateDebugFilter(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link AggregationProfileDelegateDebugFilter}
	 */
	public static final JsonpDeserializer<AggregationProfileDelegateDebugFilter> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new,
					AggregationProfileDelegateDebugFilter::setupAggregationProfileDelegateDebugFilterDeserializer);

	protected static void setupAggregationProfileDelegateDebugFilterDeserializer(
			ObjectDeserializer<AggregationProfileDelegateDebugFilter.Builder> op) {

		op.add(Builder::resultsFromMetadata, JsonpDeserializer.integerDeserializer(), "results_from_metadata");
		op.add(Builder::query, JsonpDeserializer.stringDeserializer(), "query");
		op.add(Builder::specializedFor, JsonpDeserializer.stringDeserializer(), "specialized_for");

	}

}
