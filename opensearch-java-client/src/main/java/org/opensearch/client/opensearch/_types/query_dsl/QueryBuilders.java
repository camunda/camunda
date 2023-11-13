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

/**
 * Builders for {@link Query} variants.
 */
public class QueryBuilders {
	private QueryBuilders() {
	}

	/**
	 * Creates a builder for the {@link BoolQuery bool} {@code Query} variant.
	 */
	public static BoolQuery.Builder bool() {
		return new BoolQuery.Builder();
	}

	/**
	 * Creates a builder for the {@link BoostingQuery boosting} {@code Query}
	 * variant.
	 */
	public static BoostingQuery.Builder boosting() {
		return new BoostingQuery.Builder();
	}

	/**
	 * Creates a builder for the {@link CommonTermsQuery common} {@code Query}
	 * variant.
	 */
	public static CommonTermsQuery.Builder common() {
		return new CommonTermsQuery.Builder();
	}

	/**
	 * Creates a builder for the {@link CombinedFieldsQuery combined_fields}
	 * {@code Query} variant.
	 */
	public static CombinedFieldsQuery.Builder combinedFields() {
		return new CombinedFieldsQuery.Builder();
	}

	/**
	 * Creates a builder for the {@link ConstantScoreQuery constant_score}
	 * {@code Query} variant.
	 */
	public static ConstantScoreQuery.Builder constantScore() {
		return new ConstantScoreQuery.Builder();
	}

	/**
	 * Creates a builder for the {@link DisMaxQuery dis_max} {@code Query} variant.
	 */
	public static DisMaxQuery.Builder disMax() {
		return new DisMaxQuery.Builder();
	}

	/**
	 * Creates a builder for the {@link DistanceFeatureQuery distance_feature}
	 * {@code Query} variant.
	 */
	public static DistanceFeatureQuery.Builder distanceFeature() {
		return new DistanceFeatureQuery.Builder();
	}

	/**
	 * Creates a builder for the {@link ExistsQuery exists} {@code Query} variant.
	 */
	public static ExistsQuery.Builder exists() {
		return new ExistsQuery.Builder();
	}

	/**
	 * Creates a builder for the {@link FunctionScoreQuery function_score}
	 * {@code Query} variant.
	 */
	public static FunctionScoreQuery.Builder functionScore() {
		return new FunctionScoreQuery.Builder();
	}

	/**
	 * Creates a builder for the {@link FuzzyQuery fuzzy} {@code Query} variant.
	 */
	public static FuzzyQuery.Builder fuzzy() {
		return new FuzzyQuery.Builder();
	}

	/**
	 * Creates a builder for the {@link GeoBoundingBoxQuery geo_bounding_box}
	 * {@code Query} variant.
	 */
	public static GeoBoundingBoxQuery.Builder geoBoundingBox() {
		return new GeoBoundingBoxQuery.Builder();
	}

	/**
	 * Creates a builder for the {@link GeoDistanceQuery geo_distance} {@code Query}
	 * variant.
	 */
	public static GeoDistanceQuery.Builder geoDistance() {
		return new GeoDistanceQuery.Builder();
	}

	/**
	 * Creates a builder for the {@link GeoPolygonQuery geo_polygon} {@code Query}
	 * variant.
	 */
	public static GeoPolygonQuery.Builder geoPolygon() {
		return new GeoPolygonQuery.Builder();
	}

	/**
	 * Creates a builder for the {@link GeoShapeQuery geo_shape} {@code Query}
	 * variant.
	 */
	public static GeoShapeQuery.Builder geoShape() {
		return new GeoShapeQuery.Builder();
	}

	/**
	 * Creates a builder for the {@link HasChildQuery has_child} {@code Query}
	 * variant.
	 */
	public static HasChildQuery.Builder hasChild() {
		return new HasChildQuery.Builder();
	}

	/**
	 * Creates a builder for the {@link HasParentQuery has_parent} {@code Query}
	 * variant.
	 */
	public static HasParentQuery.Builder hasParent() {
		return new HasParentQuery.Builder();
	}

	/**
	 * Creates a builder for the {@link IdsQuery ids} {@code Query} variant.
	 */
	public static IdsQuery.Builder ids() {
		return new IdsQuery.Builder();
	}

	/**
	 * Creates a builder for the {@link IntervalsQuery intervals} {@code Query}
	 * variant.
	 */
	public static IntervalsQuery.Builder intervals() {
		return new IntervalsQuery.Builder();
	}

	/**
	 * Creates a builder for the {@link MatchQuery match} {@code Query} variant.
	 */
	public static MatchQuery.Builder match() {
		return new MatchQuery.Builder();
	}

	/**
	 * Creates a builder for the {@link MatchAllQuery match_all} {@code Query}
	 * variant.
	 */
	public static MatchAllQuery.Builder matchAll() {
		return new MatchAllQuery.Builder();
	}

	/**
	 * Creates a builder for the {@link MatchBoolPrefixQuery match_bool_prefix}
	 * {@code Query} variant.
	 */
	public static MatchBoolPrefixQuery.Builder matchBoolPrefix() {
		return new MatchBoolPrefixQuery.Builder();
	}

	/**
	 * Creates a builder for the {@link MatchNoneQuery match_none} {@code Query}
	 * variant.
	 */
	public static MatchNoneQuery.Builder matchNone() {
		return new MatchNoneQuery.Builder();
	}

	/**
	 * Creates a builder for the {@link MatchPhraseQuery match_phrase} {@code Query}
	 * variant.
	 */
	public static MatchPhraseQuery.Builder matchPhrase() {
		return new MatchPhraseQuery.Builder();
	}

	/**
	 * Creates a builder for the {@link MatchPhrasePrefixQuery match_phrase_prefix}
	 * {@code Query} variant.
	 */
	public static MatchPhrasePrefixQuery.Builder matchPhrasePrefix() {
		return new MatchPhrasePrefixQuery.Builder();
	}

	/**
	 * Creates a builder for the {@link MoreLikeThisQuery more_like_this}
	 * {@code Query} variant.
	 */
	public static MoreLikeThisQuery.Builder moreLikeThis() {
		return new MoreLikeThisQuery.Builder();
	}

	/**
	 * Creates a builder for the {@link MultiMatchQuery multi_match} {@code Query}
	 * variant.
	 */
	public static MultiMatchQuery.Builder multiMatch() {
		return new MultiMatchQuery.Builder();
	}

	/**
	 * Creates a builder for the {@link NestedQuery nested} {@code Query} variant.
	 */
	public static NestedQuery.Builder nested() {
		return new NestedQuery.Builder();
	}

	/**
	 * Creates a builder for the {@link ParentIdQuery parent_id} {@code Query}
	 * variant.
	 */
	public static ParentIdQuery.Builder parentId() {
		return new ParentIdQuery.Builder();
	}

	/**
	 * Creates a builder for the {@link PercolateQuery percolate} {@code Query}
	 * variant.
	 */
	public static PercolateQuery.Builder percolate() {
		return new PercolateQuery.Builder();
	}

	/**
	 * Creates a builder for the {@link PinnedQuery pinned} {@code Query} variant.
	 */
	public static PinnedQuery.Builder pinned() {
		return new PinnedQuery.Builder();
	}

	/**
	 * Creates a builder for the {@link PrefixQuery prefix} {@code Query} variant.
	 */
	public static PrefixQuery.Builder prefix() {
		return new PrefixQuery.Builder();
	}

	/**
	 * Creates a builder for the {@link QueryStringQuery query_string} {@code Query}
	 * variant.
	 */
	public static QueryStringQuery.Builder queryString() {
		return new QueryStringQuery.Builder();
	}

	/**
	 * Creates a builder for the {@link RangeQuery range} {@code Query} variant.
	 */
	public static RangeQuery.Builder range() {
		return new RangeQuery.Builder();
	}

	/**
	 * Creates a builder for the {@link RankFeatureQuery rank_feature} {@code Query}
	 * variant.
	 */
	public static RankFeatureQuery.Builder rankFeature() {
		return new RankFeatureQuery.Builder();
	}

	/**
	 * Creates a builder for the {@link RegexpQuery regexp} {@code Query} variant.
	 */
	public static RegexpQuery.Builder regexp() {
		return new RegexpQuery.Builder();
	}

	/**
	 * Creates a builder for the {@link ScriptQuery script} {@code Query} variant.
	 */
	public static ScriptQuery.Builder script() {
		return new ScriptQuery.Builder();
	}

	/**
	 * Creates a builder for the {@link ScriptScoreQuery script_score} {@code Query}
	 * variant.
	 */
	public static ScriptScoreQuery.Builder scriptScore() {
		return new ScriptScoreQuery.Builder();
	}

	/**
	 * Creates a builder for the {@link ShapeQuery shape} {@code Query} variant.
	 */
	public static ShapeQuery.Builder shape() {
		return new ShapeQuery.Builder();
	}

	/**
	 * Creates a builder for the {@link SimpleQueryStringQuery simple_query_string}
	 * {@code Query} variant.
	 */
	public static SimpleQueryStringQuery.Builder simpleQueryString() {
		return new SimpleQueryStringQuery.Builder();
	}

	/**
	 * Creates a builder for the {@link SpanContainingQuery span_containing}
	 * {@code Query} variant.
	 */
	public static SpanContainingQuery.Builder spanContaining() {
		return new SpanContainingQuery.Builder();
	}

	/**
	 * Creates a builder for the {@link SpanFieldMaskingQuery field_masking_span}
	 * {@code Query} variant.
	 */
	public static SpanFieldMaskingQuery.Builder fieldMaskingSpan() {
		return new SpanFieldMaskingQuery.Builder();
	}

	/**
	 * Creates a builder for the {@link SpanFirstQuery span_first} {@code Query}
	 * variant.
	 */
	public static SpanFirstQuery.Builder spanFirst() {
		return new SpanFirstQuery.Builder();
	}

	/**
	 * Creates a builder for the {@link SpanMultiTermQuery span_multi} {@code Query}
	 * variant.
	 */
	public static SpanMultiTermQuery.Builder spanMulti() {
		return new SpanMultiTermQuery.Builder();
	}

	/**
	 * Creates a builder for the {@link SpanNearQuery span_near} {@code Query}
	 * variant.
	 */
	public static SpanNearQuery.Builder spanNear() {
		return new SpanNearQuery.Builder();
	}

	/**
	 * Creates a builder for the {@link SpanNotQuery span_not} {@code Query}
	 * variant.
	 */
	public static SpanNotQuery.Builder spanNot() {
		return new SpanNotQuery.Builder();
	}

	/**
	 * Creates a builder for the {@link SpanOrQuery span_or} {@code Query} variant.
	 */
	public static SpanOrQuery.Builder spanOr() {
		return new SpanOrQuery.Builder();
	}

	/**
	 * Creates a builder for the {@link SpanTermQuery span_term} {@code Query}
	 * variant.
	 */
	public static SpanTermQuery.Builder spanTerm() {
		return new SpanTermQuery.Builder();
	}

	/**
	 * Creates a builder for the {@link SpanWithinQuery span_within} {@code Query}
	 * variant.
	 */
	public static SpanWithinQuery.Builder spanWithin() {
		return new SpanWithinQuery.Builder();
	}

	/**
	 * Creates a builder for the {@link TermQuery term} {@code Query} variant.
	 */
	public static TermQuery.Builder term() {
		return new TermQuery.Builder();
	}

	/**
	 * Creates a builder for the {@link TermsQuery terms} {@code Query} variant.
	 */
	public static TermsQuery.Builder terms() {
		return new TermsQuery.Builder();
	}

	/**
	 * Creates a builder for the {@link TermsSetQuery terms_set} {@code Query}
	 * variant.
	 */
	public static TermsSetQuery.Builder termsSet() {
		return new TermsSetQuery.Builder();
	}

	/**
	 * Creates a builder for the {@link WildcardQuery wildcard} {@code Query}
	 * variant.
	 */
	public static WildcardQuery.Builder wildcard() {
		return new WildcardQuery.Builder();
	}

	/**
	 * Creates a builder for the {@link TypeQuery type} {@code Query} variant.
	 */
	public static TypeQuery.Builder type() {
		return new TypeQuery.Builder();
	}

}
