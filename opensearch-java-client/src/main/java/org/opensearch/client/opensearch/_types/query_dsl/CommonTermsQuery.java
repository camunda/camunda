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

package org.opensearch.client.opensearch._types.query_dsl;

import org.opensearch.client.json.JsonpDeserializable;
import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.ObjectBuilderDeserializer;
import org.opensearch.client.json.ObjectDeserializer;
import org.opensearch.client.util.ApiTypeHelper;
import org.opensearch.client.util.ObjectBuilder;
import jakarta.json.stream.JsonGenerator;
import java.util.function.Function;
import javax.annotation.Nullable;

// typedef: _types.query_dsl.CommonTermsQuery


@JsonpDeserializable
public class CommonTermsQuery extends QueryBase implements QueryVariant {
	// Single key dictionary
	private final String field;

	@Nullable
	private final String analyzer;

	@Nullable
	private final Double cutoffFrequency;

	@Nullable
	private final Operator highFreqOperator;

	@Nullable
	private final Operator lowFreqOperator;

	@Nullable
	private final String minimumShouldMatch;

	private final String query;

	// ---------------------------------------------------------------------------------------------

	private CommonTermsQuery(Builder builder) {
		super(builder);
		this.field = ApiTypeHelper.requireNonNull(builder.field, this, "field");

		this.analyzer = builder.analyzer;
		this.cutoffFrequency = builder.cutoffFrequency;
		this.highFreqOperator = builder.highFreqOperator;
		this.lowFreqOperator = builder.lowFreqOperator;
		this.minimumShouldMatch = builder.minimumShouldMatch;
		this.query = ApiTypeHelper.requireNonNull(builder.query, this, "query");

	}

	public static CommonTermsQuery of(Function<Builder, ObjectBuilder<CommonTermsQuery>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Query variant kind.
	 */
	@Override
	public Query.Kind _queryKind() {
		return Query.Kind.Common;
	}

	/**
	 * Required - The target field
	 */
	public final String field() {
		return this.field;
	}

	/**
	 * API name: {@code analyzer}
	 */
	@Nullable
	public final String analyzer() {
		return this.analyzer;
	}

	/**
	 * API name: {@code cutoff_frequency}
	 */
	@Nullable
	public final Double cutoffFrequency() {
		return this.cutoffFrequency;
	}

	/**
	 * API name: {@code high_freq_operator}
	 */
	@Nullable
	public final Operator highFreqOperator() {
		return this.highFreqOperator;
	}

	/**
	 * API name: {@code low_freq_operator}
	 */
	@Nullable
	public final Operator lowFreqOperator() {
		return this.lowFreqOperator;
	}

	/**
	 * API name: {@code minimum_should_match}
	 */
	@Nullable
	public final String minimumShouldMatch() {
		return this.minimumShouldMatch;
	}

	/**
	 * Required - API name: {@code query}
	 */
	public final String query() {
		return this.query;
	}

	protected void serializeInternal(JsonGenerator generator, JsonpMapper mapper) {
		generator.writeStartObject(this.field);

		super.serializeInternal(generator, mapper);
		if (this.analyzer != null) {
			generator.writeKey("analyzer");
			generator.write(this.analyzer);

		}
		if (this.cutoffFrequency != null) {
			generator.writeKey("cutoff_frequency");
			generator.write(this.cutoffFrequency);

		}
		if (this.highFreqOperator != null) {
			generator.writeKey("high_freq_operator");
			this.highFreqOperator.serialize(generator, mapper);
		}
		if (this.lowFreqOperator != null) {
			generator.writeKey("low_freq_operator");
			this.lowFreqOperator.serialize(generator, mapper);
		}
		if (this.minimumShouldMatch != null) {
			generator.writeKey("minimum_should_match");
			generator.write(this.minimumShouldMatch);

		}
		generator.writeKey("query");
		generator.write(this.query);

		generator.writeEnd();

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link CommonTermsQuery}.
	 */

	public static class Builder extends QueryBase.AbstractBuilder<Builder> implements ObjectBuilder<CommonTermsQuery> {
		private String field;

		/**
		 * Required - The target field
		 */
		public final Builder field(String value) {
			this.field = value;
			return this;
		}

		@Nullable
		private String analyzer;

		@Nullable
		private Double cutoffFrequency;

		@Nullable
		private Operator highFreqOperator;

		@Nullable
		private Operator lowFreqOperator;

		@Nullable
		private String minimumShouldMatch;

		private String query;

		/**
		 * API name: {@code analyzer}
		 */
		public final Builder analyzer(@Nullable String value) {
			this.analyzer = value;
			return this;
		}

		/**
		 * API name: {@code cutoff_frequency}
		 */
		public final Builder cutoffFrequency(@Nullable Double value) {
			this.cutoffFrequency = value;
			return this;
		}

		/**
		 * API name: {@code high_freq_operator}
		 */
		public final Builder highFreqOperator(@Nullable Operator value) {
			this.highFreqOperator = value;
			return this;
		}

		/**
		 * API name: {@code low_freq_operator}
		 */
		public final Builder lowFreqOperator(@Nullable Operator value) {
			this.lowFreqOperator = value;
			return this;
		}

		/**
		 * API name: {@code minimum_should_match}
		 */
		public final Builder minimumShouldMatch(@Nullable String value) {
			this.minimumShouldMatch = value;
			return this;
		}

		/**
		 * Required - API name: {@code query}
		 */
		public final Builder query(String value) {
			this.query = value;
			return this;
		}

		@Override
		protected Builder self() {
			return this;
		}

		/**
		 * Builds a {@link CommonTermsQuery}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public CommonTermsQuery build() {
			_checkSingleUse();

			return new CommonTermsQuery(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link CommonTermsQuery}
	 */
	public static final JsonpDeserializer<CommonTermsQuery> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			CommonTermsQuery::setupCommonTermsQueryDeserializer);

	protected static void setupCommonTermsQueryDeserializer(ObjectDeserializer<CommonTermsQuery.Builder> op) {
		setupQueryBaseDeserializer(op);
		op.add(Builder::analyzer, JsonpDeserializer.stringDeserializer(), "analyzer");
		op.add(Builder::cutoffFrequency, JsonpDeserializer.doubleDeserializer(), "cutoff_frequency");
		op.add(Builder::highFreqOperator, Operator._DESERIALIZER, "high_freq_operator");
		op.add(Builder::lowFreqOperator, Operator._DESERIALIZER, "low_freq_operator");
		op.add(Builder::minimumShouldMatch, JsonpDeserializer.stringDeserializer(), "minimum_should_match");
		op.add(Builder::query, JsonpDeserializer.stringDeserializer(), "query");

		op.setKey(Builder::field, JsonpDeserializer.stringDeserializer());
		op.shortcutProperty("query");

	}

}
