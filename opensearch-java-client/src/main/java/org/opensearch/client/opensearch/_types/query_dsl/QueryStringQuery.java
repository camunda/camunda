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
import java.util.List;
import java.util.function.Function;
import javax.annotation.Nullable;

// typedef: _types.query_dsl.QueryStringQuery


@JsonpDeserializable
public class QueryStringQuery extends QueryBase implements QueryVariant {
	@Nullable
	private final Boolean allowLeadingWildcard;

	@Nullable
	private final String analyzer;

	@Nullable
	private final Boolean analyzeWildcard;

	@Nullable
	private final Boolean autoGenerateSynonymsPhraseQuery;

	@Nullable
	private final String defaultField;

	@Nullable
	private final Operator defaultOperator;

	@Nullable
	private final Boolean enablePositionIncrements;

	@Nullable
	private final Boolean escape;

	private final List<String> fields;

	@Nullable
	private final String fuzziness;

	@Nullable
	private final Integer fuzzyMaxExpansions;

	@Nullable
	private final Integer fuzzyPrefixLength;

	@Nullable
	private final String fuzzyRewrite;

	@Nullable
	private final Boolean fuzzyTranspositions;

	@Nullable
	private final Boolean lenient;

	@Nullable
	private final Integer maxDeterminizedStates;

	@Nullable
	private final String minimumShouldMatch;

	@Nullable
	private final Double phraseSlop;

	private final String query;

	@Nullable
	private final String quoteAnalyzer;

	@Nullable
	private final String quoteFieldSuffix;

	@Nullable
	private final String rewrite;

	@Nullable
	private final Double tieBreaker;

	@Nullable
	private final String timeZone;

	@Nullable
	private final TextQueryType type;

	// ---------------------------------------------------------------------------------------------

	private QueryStringQuery(Builder builder) {
		super(builder);

		this.allowLeadingWildcard = builder.allowLeadingWildcard;
		this.analyzer = builder.analyzer;
		this.analyzeWildcard = builder.analyzeWildcard;
		this.autoGenerateSynonymsPhraseQuery = builder.autoGenerateSynonymsPhraseQuery;
		this.defaultField = builder.defaultField;
		this.defaultOperator = builder.defaultOperator;
		this.enablePositionIncrements = builder.enablePositionIncrements;
		this.escape = builder.escape;
		this.fields = ApiTypeHelper.unmodifiable(builder.fields);
		this.fuzziness = builder.fuzziness;
		this.fuzzyMaxExpansions = builder.fuzzyMaxExpansions;
		this.fuzzyPrefixLength = builder.fuzzyPrefixLength;
		this.fuzzyRewrite = builder.fuzzyRewrite;
		this.fuzzyTranspositions = builder.fuzzyTranspositions;
		this.lenient = builder.lenient;
		this.maxDeterminizedStates = builder.maxDeterminizedStates;
		this.minimumShouldMatch = builder.minimumShouldMatch;
		this.phraseSlop = builder.phraseSlop;
		this.query = ApiTypeHelper.requireNonNull(builder.query, this, "query");
		this.quoteAnalyzer = builder.quoteAnalyzer;
		this.quoteFieldSuffix = builder.quoteFieldSuffix;
		this.rewrite = builder.rewrite;
		this.tieBreaker = builder.tieBreaker;
		this.timeZone = builder.timeZone;
		this.type = builder.type;

	}

	public static QueryStringQuery of(Function<Builder, ObjectBuilder<QueryStringQuery>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Query variant kind.
	 */
	@Override
	public Query.Kind _queryKind() {
		return Query.Kind.QueryString;
	}

	/**
	 * API name: {@code allow_leading_wildcard}
	 */
	@Nullable
	public final Boolean allowLeadingWildcard() {
		return this.allowLeadingWildcard;
	}

	/**
	 * API name: {@code analyzer}
	 */
	@Nullable
	public final String analyzer() {
		return this.analyzer;
	}

	/**
	 * API name: {@code analyze_wildcard}
	 */
	@Nullable
	public final Boolean analyzeWildcard() {
		return this.analyzeWildcard;
	}

	/**
	 * API name: {@code auto_generate_synonyms_phrase_query}
	 */
	@Nullable
	public final Boolean autoGenerateSynonymsPhraseQuery() {
		return this.autoGenerateSynonymsPhraseQuery;
	}

	/**
	 * API name: {@code default_field}
	 */
	@Nullable
	public final String defaultField() {
		return this.defaultField;
	}

	/**
	 * API name: {@code default_operator}
	 */
	@Nullable
	public final Operator defaultOperator() {
		return this.defaultOperator;
	}

	/**
	 * API name: {@code enable_position_increments}
	 */
	@Nullable
	public final Boolean enablePositionIncrements() {
		return this.enablePositionIncrements;
	}

	/**
	 * API name: {@code escape}
	 */
	@Nullable
	public final Boolean escape() {
		return this.escape;
	}

	/**
	 * API name: {@code fields}
	 */
	public final List<String> fields() {
		return this.fields;
	}

	/**
	 * API name: {@code fuzziness}
	 */
	@Nullable
	public final String fuzziness() {
		return this.fuzziness;
	}

	/**
	 * API name: {@code fuzzy_max_expansions}
	 */
	@Nullable
	public final Integer fuzzyMaxExpansions() {
		return this.fuzzyMaxExpansions;
	}

	/**
	 * API name: {@code fuzzy_prefix_length}
	 */
	@Nullable
	public final Integer fuzzyPrefixLength() {
		return this.fuzzyPrefixLength;
	}

	/**
	 * API name: {@code fuzzy_rewrite}
	 */
	@Nullable
	public final String fuzzyRewrite() {
		return this.fuzzyRewrite;
	}

	/**
	 * API name: {@code fuzzy_transpositions}
	 */
	@Nullable
	public final Boolean fuzzyTranspositions() {
		return this.fuzzyTranspositions;
	}

	/**
	 * API name: {@code lenient}
	 */
	@Nullable
	public final Boolean lenient() {
		return this.lenient;
	}

	/**
	 * API name: {@code max_determinized_states}
	 */
	@Nullable
	public final Integer maxDeterminizedStates() {
		return this.maxDeterminizedStates;
	}

	/**
	 * API name: {@code minimum_should_match}
	 */
	@Nullable
	public final String minimumShouldMatch() {
		return this.minimumShouldMatch;
	}

	/**
	 * API name: {@code phrase_slop}
	 */
	@Nullable
	public final Double phraseSlop() {
		return this.phraseSlop;
	}

	/**
	 * Required - API name: {@code query}
	 */
	public final String query() {
		return this.query;
	}

	/**
	 * API name: {@code quote_analyzer}
	 */
	@Nullable
	public final String quoteAnalyzer() {
		return this.quoteAnalyzer;
	}

	/**
	 * API name: {@code quote_field_suffix}
	 */
	@Nullable
	public final String quoteFieldSuffix() {
		return this.quoteFieldSuffix;
	}

	/**
	 * API name: {@code rewrite}
	 */
	@Nullable
	public final String rewrite() {
		return this.rewrite;
	}

	/**
	 * API name: {@code tie_breaker}
	 */
	@Nullable
	public final Double tieBreaker() {
		return this.tieBreaker;
	}

	/**
	 * API name: {@code time_zone}
	 */
	@Nullable
	public final String timeZone() {
		return this.timeZone;
	}

	/**
	 * API name: {@code type}
	 */
	@Nullable
	public final TextQueryType type() {
		return this.type;
	}

	protected void serializeInternal(JsonGenerator generator, JsonpMapper mapper) {

		super.serializeInternal(generator, mapper);
		if (this.allowLeadingWildcard != null) {
			generator.writeKey("allow_leading_wildcard");
			generator.write(this.allowLeadingWildcard);

		}
		if (this.analyzer != null) {
			generator.writeKey("analyzer");
			generator.write(this.analyzer);

		}
		if (this.analyzeWildcard != null) {
			generator.writeKey("analyze_wildcard");
			generator.write(this.analyzeWildcard);

		}
		if (this.autoGenerateSynonymsPhraseQuery != null) {
			generator.writeKey("auto_generate_synonyms_phrase_query");
			generator.write(this.autoGenerateSynonymsPhraseQuery);

		}
		if (this.defaultField != null) {
			generator.writeKey("default_field");
			generator.write(this.defaultField);

		}
		if (this.defaultOperator != null) {
			generator.writeKey("default_operator");
			this.defaultOperator.serialize(generator, mapper);
		}
		if (this.enablePositionIncrements != null) {
			generator.writeKey("enable_position_increments");
			generator.write(this.enablePositionIncrements);

		}
		if (this.escape != null) {
			generator.writeKey("escape");
			generator.write(this.escape);

		}
		if (ApiTypeHelper.isDefined(this.fields)) {
			generator.writeKey("fields");
			generator.writeStartArray();
			for (String item0 : this.fields) {
				generator.write(item0);

			}
			generator.writeEnd();

		}
		if (this.fuzziness != null) {
			generator.writeKey("fuzziness");
			generator.write(this.fuzziness);

		}
		if (this.fuzzyMaxExpansions != null) {
			generator.writeKey("fuzzy_max_expansions");
			generator.write(this.fuzzyMaxExpansions);

		}
		if (this.fuzzyPrefixLength != null) {
			generator.writeKey("fuzzy_prefix_length");
			generator.write(this.fuzzyPrefixLength);

		}
		if (this.fuzzyRewrite != null) {
			generator.writeKey("fuzzy_rewrite");
			generator.write(this.fuzzyRewrite);

		}
		if (this.fuzzyTranspositions != null) {
			generator.writeKey("fuzzy_transpositions");
			generator.write(this.fuzzyTranspositions);

		}
		if (this.lenient != null) {
			generator.writeKey("lenient");
			generator.write(this.lenient);

		}
		if (this.maxDeterminizedStates != null) {
			generator.writeKey("max_determinized_states");
			generator.write(this.maxDeterminizedStates);

		}
		if (this.minimumShouldMatch != null) {
			generator.writeKey("minimum_should_match");
			generator.write(this.minimumShouldMatch);

		}
		if (this.phraseSlop != null) {
			generator.writeKey("phrase_slop");
			generator.write(this.phraseSlop);

		}
		generator.writeKey("query");
		generator.write(this.query);

		if (this.quoteAnalyzer != null) {
			generator.writeKey("quote_analyzer");
			generator.write(this.quoteAnalyzer);

		}
		if (this.quoteFieldSuffix != null) {
			generator.writeKey("quote_field_suffix");
			generator.write(this.quoteFieldSuffix);

		}
		if (this.rewrite != null) {
			generator.writeKey("rewrite");
			generator.write(this.rewrite);

		}
		if (this.tieBreaker != null) {
			generator.writeKey("tie_breaker");
			generator.write(this.tieBreaker);

		}
		if (this.timeZone != null) {
			generator.writeKey("time_zone");
			generator.write(this.timeZone);

		}
		if (this.type != null) {
			generator.writeKey("type");
			this.type.serialize(generator, mapper);
		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link QueryStringQuery}.
	 */

	public static class Builder extends QueryBase.AbstractBuilder<Builder> implements ObjectBuilder<QueryStringQuery> {
		@Nullable
		private Boolean allowLeadingWildcard;

		@Nullable
		private String analyzer;

		@Nullable
		private Boolean analyzeWildcard;

		@Nullable
		private Boolean autoGenerateSynonymsPhraseQuery;

		@Nullable
		private String defaultField;

		@Nullable
		private Operator defaultOperator;

		@Nullable
		private Boolean enablePositionIncrements;

		@Nullable
		private Boolean escape;

		@Nullable
		private List<String> fields;

		@Nullable
		private String fuzziness;

		@Nullable
		private Integer fuzzyMaxExpansions;

		@Nullable
		private Integer fuzzyPrefixLength;

		@Nullable
		private String fuzzyRewrite;

		@Nullable
		private Boolean fuzzyTranspositions;

		@Nullable
		private Boolean lenient;

		@Nullable
		private Integer maxDeterminizedStates;

		@Nullable
		private String minimumShouldMatch;

		@Nullable
		private Double phraseSlop;

		private String query;

		@Nullable
		private String quoteAnalyzer;

		@Nullable
		private String quoteFieldSuffix;

		@Nullable
		private String rewrite;

		@Nullable
		private Double tieBreaker;

		@Nullable
		private String timeZone;

		@Nullable
		private TextQueryType type;

		/**
		 * API name: {@code allow_leading_wildcard}
		 */
		public final Builder allowLeadingWildcard(@Nullable Boolean value) {
			this.allowLeadingWildcard = value;
			return this;
		}

		/**
		 * API name: {@code analyzer}
		 */
		public final Builder analyzer(@Nullable String value) {
			this.analyzer = value;
			return this;
		}

		/**
		 * API name: {@code analyze_wildcard}
		 */
		public final Builder analyzeWildcard(@Nullable Boolean value) {
			this.analyzeWildcard = value;
			return this;
		}

		/**
		 * API name: {@code auto_generate_synonyms_phrase_query}
		 */
		public final Builder autoGenerateSynonymsPhraseQuery(@Nullable Boolean value) {
			this.autoGenerateSynonymsPhraseQuery = value;
			return this;
		}

		/**
		 * API name: {@code default_field}
		 */
		public final Builder defaultField(@Nullable String value) {
			this.defaultField = value;
			return this;
		}

		/**
		 * API name: {@code default_operator}
		 */
		public final Builder defaultOperator(@Nullable Operator value) {
			this.defaultOperator = value;
			return this;
		}

		/**
		 * API name: {@code enable_position_increments}
		 */
		public final Builder enablePositionIncrements(@Nullable Boolean value) {
			this.enablePositionIncrements = value;
			return this;
		}

		/**
		 * API name: {@code escape}
		 */
		public final Builder escape(@Nullable Boolean value) {
			this.escape = value;
			return this;
		}

		/**
		 * API name: {@code fields}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>fields</code>.
		 */
		public final Builder fields(List<String> list) {
			this.fields = _listAddAll(this.fields, list);
			return this;
		}

		/**
		 * API name: {@code fields}
		 * <p>
		 * Adds one or more values to <code>fields</code>.
		 */
		public final Builder fields(String value, String... values) {
			this.fields = _listAdd(this.fields, value, values);
			return this;
		}

		/**
		 * API name: {@code fuzziness}
		 */
		public final Builder fuzziness(@Nullable String value) {
			this.fuzziness = value;
			return this;
		}

		/**
		 * API name: {@code fuzzy_max_expansions}
		 */
		public final Builder fuzzyMaxExpansions(@Nullable Integer value) {
			this.fuzzyMaxExpansions = value;
			return this;
		}

		/**
		 * API name: {@code fuzzy_prefix_length}
		 */
		public final Builder fuzzyPrefixLength(@Nullable Integer value) {
			this.fuzzyPrefixLength = value;
			return this;
		}

		/**
		 * API name: {@code fuzzy_rewrite}
		 */
		public final Builder fuzzyRewrite(@Nullable String value) {
			this.fuzzyRewrite = value;
			return this;
		}

		/**
		 * API name: {@code fuzzy_transpositions}
		 */
		public final Builder fuzzyTranspositions(@Nullable Boolean value) {
			this.fuzzyTranspositions = value;
			return this;
		}

		/**
		 * API name: {@code lenient}
		 */
		public final Builder lenient(@Nullable Boolean value) {
			this.lenient = value;
			return this;
		}

		/**
		 * API name: {@code max_determinized_states}
		 */
		public final Builder maxDeterminizedStates(@Nullable Integer value) {
			this.maxDeterminizedStates = value;
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
		 * API name: {@code phrase_slop}
		 */
		public final Builder phraseSlop(@Nullable Double value) {
			this.phraseSlop = value;
			return this;
		}

		/**
		 * Required - API name: {@code query}
		 */
		public final Builder query(String value) {
			this.query = value;
			return this;
		}

		/**
		 * API name: {@code quote_analyzer}
		 */
		public final Builder quoteAnalyzer(@Nullable String value) {
			this.quoteAnalyzer = value;
			return this;
		}

		/**
		 * API name: {@code quote_field_suffix}
		 */
		public final Builder quoteFieldSuffix(@Nullable String value) {
			this.quoteFieldSuffix = value;
			return this;
		}

		/**
		 * API name: {@code rewrite}
		 */
		public final Builder rewrite(@Nullable String value) {
			this.rewrite = value;
			return this;
		}

		/**
		 * API name: {@code tie_breaker}
		 */
		public final Builder tieBreaker(@Nullable Double value) {
			this.tieBreaker = value;
			return this;
		}

		/**
		 * API name: {@code time_zone}
		 */
		public final Builder timeZone(@Nullable String value) {
			this.timeZone = value;
			return this;
		}

		/**
		 * API name: {@code type}
		 */
		public final Builder type(@Nullable TextQueryType value) {
			this.type = value;
			return this;
		}

		@Override
		protected Builder self() {
			return this;
		}

		/**
		 * Builds a {@link QueryStringQuery}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public QueryStringQuery build() {
			_checkSingleUse();

			return new QueryStringQuery(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link QueryStringQuery}
	 */
	public static final JsonpDeserializer<QueryStringQuery> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			QueryStringQuery::setupQueryStringQueryDeserializer);

	protected static void setupQueryStringQueryDeserializer(ObjectDeserializer<QueryStringQuery.Builder> op) {
		QueryBase.setupQueryBaseDeserializer(op);
		op.add(Builder::allowLeadingWildcard, JsonpDeserializer.booleanDeserializer(), "allow_leading_wildcard");
		op.add(Builder::analyzer, JsonpDeserializer.stringDeserializer(), "analyzer");
		op.add(Builder::analyzeWildcard, JsonpDeserializer.booleanDeserializer(), "analyze_wildcard");
		op.add(Builder::autoGenerateSynonymsPhraseQuery, JsonpDeserializer.booleanDeserializer(),
				"auto_generate_synonyms_phrase_query");
		op.add(Builder::defaultField, JsonpDeserializer.stringDeserializer(), "default_field");
		op.add(Builder::defaultOperator, Operator._DESERIALIZER, "default_operator");
		op.add(Builder::enablePositionIncrements, JsonpDeserializer.booleanDeserializer(),
				"enable_position_increments");
		op.add(Builder::escape, JsonpDeserializer.booleanDeserializer(), "escape");
		op.add(Builder::fields, JsonpDeserializer.arrayDeserializer(JsonpDeserializer.stringDeserializer()), "fields");
		op.add(Builder::fuzziness, JsonpDeserializer.stringDeserializer(), "fuzziness");
		op.add(Builder::fuzzyMaxExpansions, JsonpDeserializer.integerDeserializer(), "fuzzy_max_expansions");
		op.add(Builder::fuzzyPrefixLength, JsonpDeserializer.integerDeserializer(), "fuzzy_prefix_length");
		op.add(Builder::fuzzyRewrite, JsonpDeserializer.stringDeserializer(), "fuzzy_rewrite");
		op.add(Builder::fuzzyTranspositions, JsonpDeserializer.booleanDeserializer(), "fuzzy_transpositions");
		op.add(Builder::lenient, JsonpDeserializer.booleanDeserializer(), "lenient");
		op.add(Builder::maxDeterminizedStates, JsonpDeserializer.integerDeserializer(), "max_determinized_states");
		op.add(Builder::minimumShouldMatch, JsonpDeserializer.stringDeserializer(), "minimum_should_match");
		op.add(Builder::phraseSlop, JsonpDeserializer.doubleDeserializer(), "phrase_slop");
		op.add(Builder::query, JsonpDeserializer.stringDeserializer(), "query");
		op.add(Builder::quoteAnalyzer, JsonpDeserializer.stringDeserializer(), "quote_analyzer");
		op.add(Builder::quoteFieldSuffix, JsonpDeserializer.stringDeserializer(), "quote_field_suffix");
		op.add(Builder::rewrite, JsonpDeserializer.stringDeserializer(), "rewrite");
		op.add(Builder::tieBreaker, JsonpDeserializer.doubleDeserializer(), "tie_breaker");
		op.add(Builder::timeZone, JsonpDeserializer.stringDeserializer(), "time_zone");
		op.add(Builder::type, TextQueryType._DESERIALIZER, "type");

	}

}
