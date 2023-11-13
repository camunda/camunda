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

import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.AggregationVariant;
import org.opensearch.client.json.JsonEnum;
import org.opensearch.client.json.JsonpDeserializable;
import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.JsonpSerializable;
import org.opensearch.client.json.ObjectBuilderDeserializer;
import org.opensearch.client.json.ObjectDeserializer;
import org.opensearch.client.util.ApiTypeHelper;
import org.opensearch.client.util.ObjectBuilder;
import org.opensearch.client.util.ObjectBuilderBase;
import org.opensearch.client.util.TaggedUnion;
import org.opensearch.client.util.TaggedUnionUtils;
import jakarta.json.stream.JsonGenerator;

import java.util.function.Function;

// typedef: _types.query_dsl.QueryContainer

@JsonpDeserializable
public class Query implements TaggedUnion<Query.Kind, Object>, AggregationVariant, JsonpSerializable {

	/**
	 * {@link Query} variant kinds.
	 */
	/**
	 * {@link Query} variant kinds.
	 */

	public enum Kind implements JsonEnum {
		Bool("bool"),

		Boosting("boosting"),

		Common("common"),

		CombinedFields("combined_fields"),

		ConstantScore("constant_score"),

		DisMax("dis_max"),

		DistanceFeature("distance_feature"),

		Exists("exists"),

		FunctionScore("function_score"),

		Fuzzy("fuzzy"),

		GeoBoundingBox("geo_bounding_box"),

		GeoDistance("geo_distance"),

		GeoPolygon("geo_polygon"),

		GeoShape("geo_shape"),

		HasChild("has_child"),

		HasParent("has_parent"),

		Ids("ids"),

		Intervals("intervals"),

		Match("match"),

		MatchAll("match_all"),

		MatchBoolPrefix("match_bool_prefix"),

		MatchNone("match_none"),

		MatchPhrase("match_phrase"),

		MatchPhrasePrefix("match_phrase_prefix"),

		MoreLikeThis("more_like_this"),

		MultiMatch("multi_match"),

		Nested("nested"),

		ParentId("parent_id"),

		Percolate("percolate"),

		Pinned("pinned"),

		Prefix("prefix"),

		QueryString("query_string"),

		Range("range"),

		RankFeature("rank_feature"),

		Regexp("regexp"),

		Script("script"),

		ScriptScore("script_score"),

		Shape("shape"),

		SimpleQueryString("simple_query_string"),

		SpanContaining("span_containing"),

		FieldMaskingSpan("field_masking_span"),

		SpanFirst("span_first"),

		SpanMulti("span_multi"),

		SpanNear("span_near"),

		SpanNot("span_not"),

		SpanOr("span_or"),

		SpanTerm("span_term"),

		SpanWithin("span_within"),

		Term("term"),

		Terms("terms"),

		TermsSet("terms_set"),

		Wildcard("wildcard"),

		Type("type"),

		;

		private final String jsonValue;

		Kind(String jsonValue) {
			this.jsonValue = jsonValue;
		}

		public String jsonValue() {
			return this.jsonValue;
		}

	}

	/**
	 * Aggregation variant kind.
	 */
	@Override
	public Aggregation.Kind _aggregationKind() {
		return Aggregation.Kind.Filter;
	}

	private final Kind _kind;
	private final Object _value;

	@Override
	public final Kind _kind() {
		return _kind;
	}

	@Override
	public final Object _get() {
		return _value;
	}

	public Query(QueryVariant value) {

		this._kind = ApiTypeHelper.requireNonNull(value._queryKind(), this, "<variant kind>");
		this._value = ApiTypeHelper.requireNonNull(value, this, "<variant value>");

	}

	private Query(Builder builder) {

		this._kind = ApiTypeHelper.requireNonNull(builder._kind, builder, "<variant kind>");
		this._value = ApiTypeHelper.requireNonNull(builder._value, builder, "<variant value>");

	}

	public static Query of(Function<Builder, ObjectBuilder<Query>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Is this variant instance of kind {@code bool}?
	 */
	public boolean isBool() {
		return _kind == Kind.Bool;
	}

	/**
	 * Get the {@code bool} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code bool} kind.
	 */
	public BoolQuery bool() {
		return TaggedUnionUtils.get(this, Kind.Bool);
	}

	/**
	 * Is this variant instance of kind {@code boosting}?
	 */
	public boolean isBoosting() {
		return _kind == Kind.Boosting;
	}

	/**
	 * Get the {@code boosting} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code boosting} kind.
	 */
	public BoostingQuery boosting() {
		return TaggedUnionUtils.get(this, Kind.Boosting);
	}

	/**
	 * Is this variant instance of kind {@code common}?
	 */
	public boolean isCommon() {
		return _kind == Kind.Common;
	}

	/**
	 * Get the {@code common} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code common} kind.
	 */
	public CommonTermsQuery common() {
		return TaggedUnionUtils.get(this, Kind.Common);
	}

	/**
	 * Is this variant instance of kind {@code combined_fields}?
	 */
	public boolean isCombinedFields() {
		return _kind == Kind.CombinedFields;
	}

	/**
	 * Get the {@code combined_fields} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code combined_fields}
	 *             kind.
	 */
	public CombinedFieldsQuery combinedFields() {
		return TaggedUnionUtils.get(this, Kind.CombinedFields);
	}

	/**
	 * Is this variant instance of kind {@code constant_score}?
	 */
	public boolean isConstantScore() {
		return _kind == Kind.ConstantScore;
	}

	/**
	 * Get the {@code constant_score} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code constant_score} kind.
	 */
	public ConstantScoreQuery constantScore() {
		return TaggedUnionUtils.get(this, Kind.ConstantScore);
	}

	/**
	 * Is this variant instance of kind {@code dis_max}?
	 */
	public boolean isDisMax() {
		return _kind == Kind.DisMax;
	}

	/**
	 * Get the {@code dis_max} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code dis_max} kind.
	 */
	public DisMaxQuery disMax() {
		return TaggedUnionUtils.get(this, Kind.DisMax);
	}

	/**
	 * Is this variant instance of kind {@code distance_feature}?
	 */
	public boolean isDistanceFeature() {
		return _kind == Kind.DistanceFeature;
	}

	/**
	 * Get the {@code distance_feature} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code distance_feature}
	 *             kind.
	 */
	public DistanceFeatureQuery distanceFeature() {
		return TaggedUnionUtils.get(this, Kind.DistanceFeature);
	}

	/**
	 * Is this variant instance of kind {@code exists}?
	 */
	public boolean isExists() {
		return _kind == Kind.Exists;
	}

	/**
	 * Get the {@code exists} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code exists} kind.
	 */
	public ExistsQuery exists() {
		return TaggedUnionUtils.get(this, Kind.Exists);
	}

	/**
	 * Is this variant instance of kind {@code function_score}?
	 */
	public boolean isFunctionScore() {
		return _kind == Kind.FunctionScore;
	}

	/**
	 * Get the {@code function_score} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code function_score} kind.
	 */
	public FunctionScoreQuery functionScore() {
		return TaggedUnionUtils.get(this, Kind.FunctionScore);
	}

	/**
	 * Is this variant instance of kind {@code fuzzy}?
	 */
	public boolean isFuzzy() {
		return _kind == Kind.Fuzzy;
	}

	/**
	 * Get the {@code fuzzy} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code fuzzy} kind.
	 */
	public FuzzyQuery fuzzy() {
		return TaggedUnionUtils.get(this, Kind.Fuzzy);
	}

	/**
	 * Is this variant instance of kind {@code geo_bounding_box}?
	 */
	public boolean isGeoBoundingBox() {
		return _kind == Kind.GeoBoundingBox;
	}

	/**
	 * Get the {@code geo_bounding_box} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code geo_bounding_box}
	 *             kind.
	 */
	public GeoBoundingBoxQuery geoBoundingBox() {
		return TaggedUnionUtils.get(this, Kind.GeoBoundingBox);
	}

	/**
	 * Is this variant instance of kind {@code geo_distance}?
	 */
	public boolean isGeoDistance() {
		return _kind == Kind.GeoDistance;
	}

	/**
	 * Get the {@code geo_distance} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code geo_distance} kind.
	 */
	public GeoDistanceQuery geoDistance() {
		return TaggedUnionUtils.get(this, Kind.GeoDistance);
	}

	/**
	 * Is this variant instance of kind {@code geo_polygon}?
	 */
	public boolean isGeoPolygon() {
		return _kind == Kind.GeoPolygon;
	}

	/**
	 * Get the {@code geo_polygon} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code geo_polygon} kind.
	 */
	public GeoPolygonQuery geoPolygon() {
		return TaggedUnionUtils.get(this, Kind.GeoPolygon);
	}

	/**
	 * Is this variant instance of kind {@code geo_shape}?
	 */
	public boolean isGeoShape() {
		return _kind == Kind.GeoShape;
	}

	/**
	 * Get the {@code geo_shape} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code geo_shape} kind.
	 */
	public GeoShapeQuery geoShape() {
		return TaggedUnionUtils.get(this, Kind.GeoShape);
	}

	/**
	 * Is this variant instance of kind {@code has_child}?
	 */
	public boolean isHasChild() {
		return _kind == Kind.HasChild;
	}

	/**
	 * Get the {@code has_child} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code has_child} kind.
	 */
	public HasChildQuery hasChild() {
		return TaggedUnionUtils.get(this, Kind.HasChild);
	}

	/**
	 * Is this variant instance of kind {@code has_parent}?
	 */
	public boolean isHasParent() {
		return _kind == Kind.HasParent;
	}

	/**
	 * Get the {@code has_parent} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code has_parent} kind.
	 */
	public HasParentQuery hasParent() {
		return TaggedUnionUtils.get(this, Kind.HasParent);
	}

	/**
	 * Is this variant instance of kind {@code ids}?
	 */
	public boolean isIds() {
		return _kind == Kind.Ids;
	}

	/**
	 * Get the {@code ids} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code ids} kind.
	 */
	public IdsQuery ids() {
		return TaggedUnionUtils.get(this, Kind.Ids);
	}

	/**
	 * Is this variant instance of kind {@code intervals}?
	 */
	public boolean isIntervals() {
		return _kind == Kind.Intervals;
	}

	/**
	 * Get the {@code intervals} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code intervals} kind.
	 */
	public IntervalsQuery intervals() {
		return TaggedUnionUtils.get(this, Kind.Intervals);
	}

	/**
	 * Is this variant instance of kind {@code match}?
	 */
	public boolean isMatch() {
		return _kind == Kind.Match;
	}

	/**
	 * Get the {@code match} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code match} kind.
	 */
	public MatchQuery match() {
		return TaggedUnionUtils.get(this, Kind.Match);
	}

	/**
	 * Is this variant instance of kind {@code match_all}?
	 */
	public boolean isMatchAll() {
		return _kind == Kind.MatchAll;
	}

	/**
	 * Get the {@code match_all} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code match_all} kind.
	 */
	public MatchAllQuery matchAll() {
		return TaggedUnionUtils.get(this, Kind.MatchAll);
	}

	/**
	 * Is this variant instance of kind {@code match_bool_prefix}?
	 */
	public boolean isMatchBoolPrefix() {
		return _kind == Kind.MatchBoolPrefix;
	}

	/**
	 * Get the {@code match_bool_prefix} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code match_bool_prefix}
	 *             kind.
	 */
	public MatchBoolPrefixQuery matchBoolPrefix() {
		return TaggedUnionUtils.get(this, Kind.MatchBoolPrefix);
	}

	/**
	 * Is this variant instance of kind {@code match_none}?
	 */
	public boolean isMatchNone() {
		return _kind == Kind.MatchNone;
	}

	/**
	 * Get the {@code match_none} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code match_none} kind.
	 */
	public MatchNoneQuery matchNone() {
		return TaggedUnionUtils.get(this, Kind.MatchNone);
	}

	/**
	 * Is this variant instance of kind {@code match_phrase}?
	 */
	public boolean isMatchPhrase() {
		return _kind == Kind.MatchPhrase;
	}

	/**
	 * Get the {@code match_phrase} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code match_phrase} kind.
	 */
	public MatchPhraseQuery matchPhrase() {
		return TaggedUnionUtils.get(this, Kind.MatchPhrase);
	}

	/**
	 * Is this variant instance of kind {@code match_phrase_prefix}?
	 */
	public boolean isMatchPhrasePrefix() {
		return _kind == Kind.MatchPhrasePrefix;
	}

	/**
	 * Get the {@code match_phrase_prefix} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code match_phrase_prefix}
	 *             kind.
	 */
	public MatchPhrasePrefixQuery matchPhrasePrefix() {
		return TaggedUnionUtils.get(this, Kind.MatchPhrasePrefix);
	}

	/**
	 * Is this variant instance of kind {@code more_like_this}?
	 */
	public boolean isMoreLikeThis() {
		return _kind == Kind.MoreLikeThis;
	}

	/**
	 * Get the {@code more_like_this} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code more_like_this} kind.
	 */
	public MoreLikeThisQuery moreLikeThis() {
		return TaggedUnionUtils.get(this, Kind.MoreLikeThis);
	}

	/**
	 * Is this variant instance of kind {@code multi_match}?
	 */
	public boolean isMultiMatch() {
		return _kind == Kind.MultiMatch;
	}

	/**
	 * Get the {@code multi_match} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code multi_match} kind.
	 */
	public MultiMatchQuery multiMatch() {
		return TaggedUnionUtils.get(this, Kind.MultiMatch);
	}

	/**
	 * Is this variant instance of kind {@code nested}?
	 */
	public boolean isNested() {
		return _kind == Kind.Nested;
	}

	/**
	 * Get the {@code nested} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code nested} kind.
	 */
	public NestedQuery nested() {
		return TaggedUnionUtils.get(this, Kind.Nested);
	}

	/**
	 * Is this variant instance of kind {@code parent_id}?
	 */
	public boolean isParentId() {
		return _kind == Kind.ParentId;
	}

	/**
	 * Get the {@code parent_id} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code parent_id} kind.
	 */
	public ParentIdQuery parentId() {
		return TaggedUnionUtils.get(this, Kind.ParentId);
	}

	/**
	 * Is this variant instance of kind {@code percolate}?
	 */
	public boolean isPercolate() {
		return _kind == Kind.Percolate;
	}

	/**
	 * Get the {@code percolate} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code percolate} kind.
	 */
	public PercolateQuery percolate() {
		return TaggedUnionUtils.get(this, Kind.Percolate);
	}

	/**
	 * Is this variant instance of kind {@code pinned}?
	 */
	public boolean isPinned() {
		return _kind == Kind.Pinned;
	}

	/**
	 * Get the {@code pinned} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code pinned} kind.
	 */
	public PinnedQuery pinned() {
		return TaggedUnionUtils.get(this, Kind.Pinned);
	}

	/**
	 * Is this variant instance of kind {@code prefix}?
	 */
	public boolean isPrefix() {
		return _kind == Kind.Prefix;
	}

	/**
	 * Get the {@code prefix} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code prefix} kind.
	 */
	public PrefixQuery prefix() {
		return TaggedUnionUtils.get(this, Kind.Prefix);
	}

	/**
	 * Is this variant instance of kind {@code query_string}?
	 */
	public boolean isQueryString() {
		return _kind == Kind.QueryString;
	}

	/**
	 * Get the {@code query_string} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code query_string} kind.
	 */
	public QueryStringQuery queryString() {
		return TaggedUnionUtils.get(this, Kind.QueryString);
	}

	/**
	 * Is this variant instance of kind {@code range}?
	 */
	public boolean isRange() {
		return _kind == Kind.Range;
	}

	/**
	 * Get the {@code range} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code range} kind.
	 */
	public RangeQuery range() {
		return TaggedUnionUtils.get(this, Kind.Range);
	}

	/**
	 * Is this variant instance of kind {@code rank_feature}?
	 */
	public boolean isRankFeature() {
		return _kind == Kind.RankFeature;
	}

	/**
	 * Get the {@code rank_feature} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code rank_feature} kind.
	 */
	public RankFeatureQuery rankFeature() {
		return TaggedUnionUtils.get(this, Kind.RankFeature);
	}

	/**
	 * Is this variant instance of kind {@code regexp}?
	 */
	public boolean isRegexp() {
		return _kind == Kind.Regexp;
	}

	/**
	 * Get the {@code regexp} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code regexp} kind.
	 */
	public RegexpQuery regexp() {
		return TaggedUnionUtils.get(this, Kind.Regexp);
	}

	/**
	 * Is this variant instance of kind {@code script}?
	 */
	public boolean isScript() {
		return _kind == Kind.Script;
	}

	/**
	 * Get the {@code script} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code script} kind.
	 */
	public ScriptQuery script() {
		return TaggedUnionUtils.get(this, Kind.Script);
	}

	/**
	 * Is this variant instance of kind {@code script_score}?
	 */
	public boolean isScriptScore() {
		return _kind == Kind.ScriptScore;
	}

	/**
	 * Get the {@code script_score} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code script_score} kind.
	 */
	public ScriptScoreQuery scriptScore() {
		return TaggedUnionUtils.get(this, Kind.ScriptScore);
	}

	/**
	 * Is this variant instance of kind {@code shape}?
	 */
	public boolean isShape() {
		return _kind == Kind.Shape;
	}

	/**
	 * Get the {@code shape} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code shape} kind.
	 */
	public ShapeQuery shape() {
		return TaggedUnionUtils.get(this, Kind.Shape);
	}

	/**
	 * Is this variant instance of kind {@code simple_query_string}?
	 */
	public boolean isSimpleQueryString() {
		return _kind == Kind.SimpleQueryString;
	}

	/**
	 * Get the {@code simple_query_string} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code simple_query_string}
	 *             kind.
	 */
	public SimpleQueryStringQuery simpleQueryString() {
		return TaggedUnionUtils.get(this, Kind.SimpleQueryString);
	}

	/**
	 * Is this variant instance of kind {@code span_containing}?
	 */
	public boolean isSpanContaining() {
		return _kind == Kind.SpanContaining;
	}

	/**
	 * Get the {@code span_containing} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code span_containing}
	 *             kind.
	 */
	public SpanContainingQuery spanContaining() {
		return TaggedUnionUtils.get(this, Kind.SpanContaining);
	}

	/**
	 * Is this variant instance of kind {@code field_masking_span}?
	 */
	public boolean isFieldMaskingSpan() {
		return _kind == Kind.FieldMaskingSpan;
	}

	/**
	 * Get the {@code field_masking_span} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code field_masking_span}
	 *             kind.
	 */
	public SpanFieldMaskingQuery fieldMaskingSpan() {
		return TaggedUnionUtils.get(this, Kind.FieldMaskingSpan);
	}

	/**
	 * Is this variant instance of kind {@code span_first}?
	 */
	public boolean isSpanFirst() {
		return _kind == Kind.SpanFirst;
	}

	/**
	 * Get the {@code span_first} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code span_first} kind.
	 */
	public SpanFirstQuery spanFirst() {
		return TaggedUnionUtils.get(this, Kind.SpanFirst);
	}

	/**
	 * Is this variant instance of kind {@code span_multi}?
	 */
	public boolean isSpanMulti() {
		return _kind == Kind.SpanMulti;
	}

	/**
	 * Get the {@code span_multi} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code span_multi} kind.
	 */
	public SpanMultiTermQuery spanMulti() {
		return TaggedUnionUtils.get(this, Kind.SpanMulti);
	}

	/**
	 * Is this variant instance of kind {@code span_near}?
	 */
	public boolean isSpanNear() {
		return _kind == Kind.SpanNear;
	}

	/**
	 * Get the {@code span_near} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code span_near} kind.
	 */
	public SpanNearQuery spanNear() {
		return TaggedUnionUtils.get(this, Kind.SpanNear);
	}

	/**
	 * Is this variant instance of kind {@code span_not}?
	 */
	public boolean isSpanNot() {
		return _kind == Kind.SpanNot;
	}

	/**
	 * Get the {@code span_not} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code span_not} kind.
	 */
	public SpanNotQuery spanNot() {
		return TaggedUnionUtils.get(this, Kind.SpanNot);
	}

	/**
	 * Is this variant instance of kind {@code span_or}?
	 */
	public boolean isSpanOr() {
		return _kind == Kind.SpanOr;
	}

	/**
	 * Get the {@code span_or} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code span_or} kind.
	 */
	public SpanOrQuery spanOr() {
		return TaggedUnionUtils.get(this, Kind.SpanOr);
	}

	/**
	 * Is this variant instance of kind {@code span_term}?
	 */
	public boolean isSpanTerm() {
		return _kind == Kind.SpanTerm;
	}

	/**
	 * Get the {@code span_term} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code span_term} kind.
	 */
	public SpanTermQuery spanTerm() {
		return TaggedUnionUtils.get(this, Kind.SpanTerm);
	}

	/**
	 * Is this variant instance of kind {@code span_within}?
	 */
	public boolean isSpanWithin() {
		return _kind == Kind.SpanWithin;
	}

	/**
	 * Get the {@code span_within} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code span_within} kind.
	 */
	public SpanWithinQuery spanWithin() {
		return TaggedUnionUtils.get(this, Kind.SpanWithin);
	}

	/**
	 * Is this variant instance of kind {@code term}?
	 */
	public boolean isTerm() {
		return _kind == Kind.Term;
	}

	/**
	 * Get the {@code term} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code term} kind.
	 */
	public TermQuery term() {
		return TaggedUnionUtils.get(this, Kind.Term);
	}

	/**
	 * Is this variant instance of kind {@code terms}?
	 */
	public boolean isTerms() {
		return _kind == Kind.Terms;
	}

	/**
	 * Get the {@code terms} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code terms} kind.
	 */
	public TermsQuery terms() {
		return TaggedUnionUtils.get(this, Kind.Terms);
	}

	/**
	 * Is this variant instance of kind {@code terms_set}?
	 */
	public boolean isTermsSet() {
		return _kind == Kind.TermsSet;
	}

	/**
	 * Get the {@code terms_set} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code terms_set} kind.
	 */
	public TermsSetQuery termsSet() {
		return TaggedUnionUtils.get(this, Kind.TermsSet);
	}

	/**
	 * Is this variant instance of kind {@code wildcard}?
	 */
	public boolean isWildcard() {
		return _kind == Kind.Wildcard;
	}

	/**
	 * Get the {@code wildcard} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code wildcard} kind.
	 */
	public WildcardQuery wildcard() {
		return TaggedUnionUtils.get(this, Kind.Wildcard);
	}

	/**
	 * Is this variant instance of kind {@code type}?
	 */
	public boolean isType() {
		return _kind == Kind.Type;
	}

	/**
	 * Get the {@code type} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code type} kind.
	 */
	public TypeQuery type() {
		return TaggedUnionUtils.get(this, Kind.Type);
	}

	@Override
	@SuppressWarnings("unchecked")
	public void serialize(JsonGenerator generator, JsonpMapper mapper) {

		generator.writeStartObject();

		generator.writeKey(_kind.jsonValue());
		if (_value instanceof JsonpSerializable) {
			((JsonpSerializable) _value).serialize(generator, mapper);
		}

		generator.writeEnd();

	}

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<Query> {
		private Kind _kind;
		private Object _value;

		public ObjectBuilder<Query> bool(BoolQuery v) {
			this._kind = Kind.Bool;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Query> bool(Function<BoolQuery.Builder, ObjectBuilder<BoolQuery>> fn) {
			return this.bool(fn.apply(new BoolQuery.Builder()).build());
		}

		public ObjectBuilder<Query> boosting(BoostingQuery v) {
			this._kind = Kind.Boosting;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Query> boosting(Function<BoostingQuery.Builder, ObjectBuilder<BoostingQuery>> fn) {
			return this.boosting(fn.apply(new BoostingQuery.Builder()).build());
		}

		public ObjectBuilder<Query> common(CommonTermsQuery v) {
			this._kind = Kind.Common;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Query> common(Function<CommonTermsQuery.Builder, ObjectBuilder<CommonTermsQuery>> fn) {
			return this.common(fn.apply(new CommonTermsQuery.Builder()).build());
		}

		public ObjectBuilder<Query> combinedFields(CombinedFieldsQuery v) {
			this._kind = Kind.CombinedFields;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Query> combinedFields(
				Function<CombinedFieldsQuery.Builder, ObjectBuilder<CombinedFieldsQuery>> fn) {
			return this.combinedFields(fn.apply(new CombinedFieldsQuery.Builder()).build());
		}

		public ObjectBuilder<Query> constantScore(ConstantScoreQuery v) {
			this._kind = Kind.ConstantScore;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Query> constantScore(
				Function<ConstantScoreQuery.Builder, ObjectBuilder<ConstantScoreQuery>> fn) {
			return this.constantScore(fn.apply(new ConstantScoreQuery.Builder()).build());
		}

		public ObjectBuilder<Query> disMax(DisMaxQuery v) {
			this._kind = Kind.DisMax;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Query> disMax(Function<DisMaxQuery.Builder, ObjectBuilder<DisMaxQuery>> fn) {
			return this.disMax(fn.apply(new DisMaxQuery.Builder()).build());
		}

		public ObjectBuilder<Query> distanceFeature(DistanceFeatureQuery v) {
			this._kind = Kind.DistanceFeature;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Query> distanceFeature(
				Function<DistanceFeatureQuery.Builder, ObjectBuilder<DistanceFeatureQuery>> fn) {
			return this.distanceFeature(fn.apply(new DistanceFeatureQuery.Builder()).build());
		}

		public ObjectBuilder<Query> exists(ExistsQuery v) {
			this._kind = Kind.Exists;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Query> exists(Function<ExistsQuery.Builder, ObjectBuilder<ExistsQuery>> fn) {
			return this.exists(fn.apply(new ExistsQuery.Builder()).build());
		}

		public ObjectBuilder<Query> functionScore(FunctionScoreQuery v) {
			this._kind = Kind.FunctionScore;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Query> functionScore(
				Function<FunctionScoreQuery.Builder, ObjectBuilder<FunctionScoreQuery>> fn) {
			return this.functionScore(fn.apply(new FunctionScoreQuery.Builder()).build());
		}

		public ObjectBuilder<Query> fuzzy(FuzzyQuery v) {
			this._kind = Kind.Fuzzy;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Query> fuzzy(Function<FuzzyQuery.Builder, ObjectBuilder<FuzzyQuery>> fn) {
			return this.fuzzy(fn.apply(new FuzzyQuery.Builder()).build());
		}

		public ObjectBuilder<Query> geoBoundingBox(GeoBoundingBoxQuery v) {
			this._kind = Kind.GeoBoundingBox;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Query> geoBoundingBox(
				Function<GeoBoundingBoxQuery.Builder, ObjectBuilder<GeoBoundingBoxQuery>> fn) {
			return this.geoBoundingBox(fn.apply(new GeoBoundingBoxQuery.Builder()).build());
		}

		public ObjectBuilder<Query> geoDistance(GeoDistanceQuery v) {
			this._kind = Kind.GeoDistance;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Query> geoDistance(
				Function<GeoDistanceQuery.Builder, ObjectBuilder<GeoDistanceQuery>> fn) {
			return this.geoDistance(fn.apply(new GeoDistanceQuery.Builder()).build());
		}

		public ObjectBuilder<Query> geoPolygon(GeoPolygonQuery v) {
			this._kind = Kind.GeoPolygon;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Query> geoPolygon(Function<GeoPolygonQuery.Builder, ObjectBuilder<GeoPolygonQuery>> fn) {
			return this.geoPolygon(fn.apply(new GeoPolygonQuery.Builder()).build());
		}

		public ObjectBuilder<Query> geoShape(GeoShapeQuery v) {
			this._kind = Kind.GeoShape;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Query> geoShape(Function<GeoShapeQuery.Builder, ObjectBuilder<GeoShapeQuery>> fn) {
			return this.geoShape(fn.apply(new GeoShapeQuery.Builder()).build());
		}

		public ObjectBuilder<Query> hasChild(HasChildQuery v) {
			this._kind = Kind.HasChild;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Query> hasChild(Function<HasChildQuery.Builder, ObjectBuilder<HasChildQuery>> fn) {
			return this.hasChild(fn.apply(new HasChildQuery.Builder()).build());
		}

		public ObjectBuilder<Query> hasParent(HasParentQuery v) {
			this._kind = Kind.HasParent;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Query> hasParent(Function<HasParentQuery.Builder, ObjectBuilder<HasParentQuery>> fn) {
			return this.hasParent(fn.apply(new HasParentQuery.Builder()).build());
		}

		public ObjectBuilder<Query> ids(IdsQuery v) {
			this._kind = Kind.Ids;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Query> ids(Function<IdsQuery.Builder, ObjectBuilder<IdsQuery>> fn) {
			return this.ids(fn.apply(new IdsQuery.Builder()).build());
		}

		public ObjectBuilder<Query> intervals(IntervalsQuery v) {
			this._kind = Kind.Intervals;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Query> intervals(Function<IntervalsQuery.Builder, ObjectBuilder<IntervalsQuery>> fn) {
			return this.intervals(fn.apply(new IntervalsQuery.Builder()).build());
		}

		public ObjectBuilder<Query> match(MatchQuery v) {
			this._kind = Kind.Match;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Query> match(Function<MatchQuery.Builder, ObjectBuilder<MatchQuery>> fn) {
			return this.match(fn.apply(new MatchQuery.Builder()).build());
		}

		public ObjectBuilder<Query> matchAll(MatchAllQuery v) {
			this._kind = Kind.MatchAll;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Query> matchAll(Function<MatchAllQuery.Builder, ObjectBuilder<MatchAllQuery>> fn) {
			return this.matchAll(fn.apply(new MatchAllQuery.Builder()).build());
		}

		public ObjectBuilder<Query> matchBoolPrefix(MatchBoolPrefixQuery v) {
			this._kind = Kind.MatchBoolPrefix;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Query> matchBoolPrefix(
				Function<MatchBoolPrefixQuery.Builder, ObjectBuilder<MatchBoolPrefixQuery>> fn) {
			return this.matchBoolPrefix(fn.apply(new MatchBoolPrefixQuery.Builder()).build());
		}

		public ObjectBuilder<Query> matchNone(MatchNoneQuery v) {
			this._kind = Kind.MatchNone;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Query> matchNone(Function<MatchNoneQuery.Builder, ObjectBuilder<MatchNoneQuery>> fn) {
			return this.matchNone(fn.apply(new MatchNoneQuery.Builder()).build());
		}

		public ObjectBuilder<Query> matchPhrase(MatchPhraseQuery v) {
			this._kind = Kind.MatchPhrase;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Query> matchPhrase(
				Function<MatchPhraseQuery.Builder, ObjectBuilder<MatchPhraseQuery>> fn) {
			return this.matchPhrase(fn.apply(new MatchPhraseQuery.Builder()).build());
		}

		public ObjectBuilder<Query> matchPhrasePrefix(MatchPhrasePrefixQuery v) {
			this._kind = Kind.MatchPhrasePrefix;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Query> matchPhrasePrefix(
				Function<MatchPhrasePrefixQuery.Builder, ObjectBuilder<MatchPhrasePrefixQuery>> fn) {
			return this.matchPhrasePrefix(fn.apply(new MatchPhrasePrefixQuery.Builder()).build());
		}

		public ObjectBuilder<Query> moreLikeThis(MoreLikeThisQuery v) {
			this._kind = Kind.MoreLikeThis;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Query> moreLikeThis(
				Function<MoreLikeThisQuery.Builder, ObjectBuilder<MoreLikeThisQuery>> fn) {
			return this.moreLikeThis(fn.apply(new MoreLikeThisQuery.Builder()).build());
		}

		public ObjectBuilder<Query> multiMatch(MultiMatchQuery v) {
			this._kind = Kind.MultiMatch;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Query> multiMatch(Function<MultiMatchQuery.Builder, ObjectBuilder<MultiMatchQuery>> fn) {
			return this.multiMatch(fn.apply(new MultiMatchQuery.Builder()).build());
		}

		public ObjectBuilder<Query> nested(NestedQuery v) {
			this._kind = Kind.Nested;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Query> nested(Function<NestedQuery.Builder, ObjectBuilder<NestedQuery>> fn) {
			return this.nested(fn.apply(new NestedQuery.Builder()).build());
		}

		public ObjectBuilder<Query> parentId(ParentIdQuery v) {
			this._kind = Kind.ParentId;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Query> parentId(Function<ParentIdQuery.Builder, ObjectBuilder<ParentIdQuery>> fn) {
			return this.parentId(fn.apply(new ParentIdQuery.Builder()).build());
		}

		public ObjectBuilder<Query> percolate(PercolateQuery v) {
			this._kind = Kind.Percolate;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Query> percolate(Function<PercolateQuery.Builder, ObjectBuilder<PercolateQuery>> fn) {
			return this.percolate(fn.apply(new PercolateQuery.Builder()).build());
		}

		public ObjectBuilder<Query> pinned(PinnedQuery v) {
			this._kind = Kind.Pinned;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Query> pinned(Function<PinnedQuery.Builder, ObjectBuilder<PinnedQuery>> fn) {
			return this.pinned(fn.apply(new PinnedQuery.Builder()).build());
		}

		public ObjectBuilder<Query> prefix(PrefixQuery v) {
			this._kind = Kind.Prefix;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Query> prefix(Function<PrefixQuery.Builder, ObjectBuilder<PrefixQuery>> fn) {
			return this.prefix(fn.apply(new PrefixQuery.Builder()).build());
		}

		public ObjectBuilder<Query> queryString(QueryStringQuery v) {
			this._kind = Kind.QueryString;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Query> queryString(
				Function<QueryStringQuery.Builder, ObjectBuilder<QueryStringQuery>> fn) {
			return this.queryString(fn.apply(new QueryStringQuery.Builder()).build());
		}

		public ObjectBuilder<Query> range(RangeQuery v) {
			this._kind = Kind.Range;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Query> range(Function<RangeQuery.Builder, ObjectBuilder<RangeQuery>> fn) {
			return this.range(fn.apply(new RangeQuery.Builder()).build());
		}

		public ObjectBuilder<Query> rankFeature(RankFeatureQuery v) {
			this._kind = Kind.RankFeature;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Query> rankFeature(
				Function<RankFeatureQuery.Builder, ObjectBuilder<RankFeatureQuery>> fn) {
			return this.rankFeature(fn.apply(new RankFeatureQuery.Builder()).build());
		}

		public ObjectBuilder<Query> regexp(RegexpQuery v) {
			this._kind = Kind.Regexp;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Query> regexp(Function<RegexpQuery.Builder, ObjectBuilder<RegexpQuery>> fn) {
			return this.regexp(fn.apply(new RegexpQuery.Builder()).build());
		}

		public ObjectBuilder<Query> script(ScriptQuery v) {
			this._kind = Kind.Script;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Query> script(Function<ScriptQuery.Builder, ObjectBuilder<ScriptQuery>> fn) {
			return this.script(fn.apply(new ScriptQuery.Builder()).build());
		}

		public ObjectBuilder<Query> scriptScore(ScriptScoreQuery v) {
			this._kind = Kind.ScriptScore;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Query> scriptScore(
				Function<ScriptScoreQuery.Builder, ObjectBuilder<ScriptScoreQuery>> fn) {
			return this.scriptScore(fn.apply(new ScriptScoreQuery.Builder()).build());
		}

		public ObjectBuilder<Query> shape(ShapeQuery v) {
			this._kind = Kind.Shape;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Query> shape(Function<ShapeQuery.Builder, ObjectBuilder<ShapeQuery>> fn) {
			return this.shape(fn.apply(new ShapeQuery.Builder()).build());
		}

		public ObjectBuilder<Query> simpleQueryString(SimpleQueryStringQuery v) {
			this._kind = Kind.SimpleQueryString;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Query> simpleQueryString(
				Function<SimpleQueryStringQuery.Builder, ObjectBuilder<SimpleQueryStringQuery>> fn) {
			return this.simpleQueryString(fn.apply(new SimpleQueryStringQuery.Builder()).build());
		}

		public ObjectBuilder<Query> spanContaining(SpanContainingQuery v) {
			this._kind = Kind.SpanContaining;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Query> spanContaining(
				Function<SpanContainingQuery.Builder, ObjectBuilder<SpanContainingQuery>> fn) {
			return this.spanContaining(fn.apply(new SpanContainingQuery.Builder()).build());
		}

		public ObjectBuilder<Query> fieldMaskingSpan(SpanFieldMaskingQuery v) {
			this._kind = Kind.FieldMaskingSpan;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Query> fieldMaskingSpan(
				Function<SpanFieldMaskingQuery.Builder, ObjectBuilder<SpanFieldMaskingQuery>> fn) {
			return this.fieldMaskingSpan(fn.apply(new SpanFieldMaskingQuery.Builder()).build());
		}

		public ObjectBuilder<Query> spanFirst(SpanFirstQuery v) {
			this._kind = Kind.SpanFirst;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Query> spanFirst(Function<SpanFirstQuery.Builder, ObjectBuilder<SpanFirstQuery>> fn) {
			return this.spanFirst(fn.apply(new SpanFirstQuery.Builder()).build());
		}

		public ObjectBuilder<Query> spanMulti(SpanMultiTermQuery v) {
			this._kind = Kind.SpanMulti;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Query> spanMulti(
				Function<SpanMultiTermQuery.Builder, ObjectBuilder<SpanMultiTermQuery>> fn) {
			return this.spanMulti(fn.apply(new SpanMultiTermQuery.Builder()).build());
		}

		public ObjectBuilder<Query> spanNear(SpanNearQuery v) {
			this._kind = Kind.SpanNear;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Query> spanNear(Function<SpanNearQuery.Builder, ObjectBuilder<SpanNearQuery>> fn) {
			return this.spanNear(fn.apply(new SpanNearQuery.Builder()).build());
		}

		public ObjectBuilder<Query> spanNot(SpanNotQuery v) {
			this._kind = Kind.SpanNot;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Query> spanNot(Function<SpanNotQuery.Builder, ObjectBuilder<SpanNotQuery>> fn) {
			return this.spanNot(fn.apply(new SpanNotQuery.Builder()).build());
		}

		public ObjectBuilder<Query> spanOr(SpanOrQuery v) {
			this._kind = Kind.SpanOr;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Query> spanOr(Function<SpanOrQuery.Builder, ObjectBuilder<SpanOrQuery>> fn) {
			return this.spanOr(fn.apply(new SpanOrQuery.Builder()).build());
		}

		public ObjectBuilder<Query> spanTerm(SpanTermQuery v) {
			this._kind = Kind.SpanTerm;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Query> spanTerm(Function<SpanTermQuery.Builder, ObjectBuilder<SpanTermQuery>> fn) {
			return this.spanTerm(fn.apply(new SpanTermQuery.Builder()).build());
		}

		public ObjectBuilder<Query> spanWithin(SpanWithinQuery v) {
			this._kind = Kind.SpanWithin;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Query> spanWithin(Function<SpanWithinQuery.Builder, ObjectBuilder<SpanWithinQuery>> fn) {
			return this.spanWithin(fn.apply(new SpanWithinQuery.Builder()).build());
		}

		public ObjectBuilder<Query> term(TermQuery v) {
			this._kind = Kind.Term;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Query> term(Function<TermQuery.Builder, ObjectBuilder<TermQuery>> fn) {
			return this.term(fn.apply(new TermQuery.Builder()).build());
		}

		public ObjectBuilder<Query> terms(TermsQuery v) {
			this._kind = Kind.Terms;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Query> terms(Function<TermsQuery.Builder, ObjectBuilder<TermsQuery>> fn) {
			return this.terms(fn.apply(new TermsQuery.Builder()).build());
		}

		public ObjectBuilder<Query> termsSet(TermsSetQuery v) {
			this._kind = Kind.TermsSet;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Query> termsSet(Function<TermsSetQuery.Builder, ObjectBuilder<TermsSetQuery>> fn) {
			return this.termsSet(fn.apply(new TermsSetQuery.Builder()).build());
		}

		public ObjectBuilder<Query> wildcard(WildcardQuery v) {
			this._kind = Kind.Wildcard;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Query> wildcard(Function<WildcardQuery.Builder, ObjectBuilder<WildcardQuery>> fn) {
			return this.wildcard(fn.apply(new WildcardQuery.Builder()).build());
		}

		public ObjectBuilder<Query> type(TypeQuery v) {
			this._kind = Kind.Type;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Query> type(Function<TypeQuery.Builder, ObjectBuilder<TypeQuery>> fn) {
			return this.type(fn.apply(new TypeQuery.Builder()).build());
		}

		public Query build() {
			_checkSingleUse();
			return new Query(this);
		}

	}

	protected static void setupQueryDeserializer(ObjectDeserializer<Builder> op) {

		op.add(Builder::bool, BoolQuery._DESERIALIZER, "bool");
		op.add(Builder::boosting, BoostingQuery._DESERIALIZER, "boosting");
		op.add(Builder::common, CommonTermsQuery._DESERIALIZER, "common");
		op.add(Builder::combinedFields, CombinedFieldsQuery._DESERIALIZER, "combined_fields");
		op.add(Builder::constantScore, ConstantScoreQuery._DESERIALIZER, "constant_score");
		op.add(Builder::disMax, DisMaxQuery._DESERIALIZER, "dis_max");
		op.add(Builder::distanceFeature, DistanceFeatureQuery._DESERIALIZER, "distance_feature");
		op.add(Builder::exists, ExistsQuery._DESERIALIZER, "exists");
		op.add(Builder::functionScore, FunctionScoreQuery._DESERIALIZER, "function_score");
		op.add(Builder::fuzzy, FuzzyQuery._DESERIALIZER, "fuzzy");
		op.add(Builder::geoBoundingBox, GeoBoundingBoxQuery._DESERIALIZER, "geo_bounding_box");
		op.add(Builder::geoDistance, GeoDistanceQuery._DESERIALIZER, "geo_distance");
		op.add(Builder::geoPolygon, GeoPolygonQuery._DESERIALIZER, "geo_polygon");
		op.add(Builder::geoShape, GeoShapeQuery._DESERIALIZER, "geo_shape");
		op.add(Builder::hasChild, HasChildQuery._DESERIALIZER, "has_child");
		op.add(Builder::hasParent, HasParentQuery._DESERIALIZER, "has_parent");
		op.add(Builder::ids, IdsQuery._DESERIALIZER, "ids");
		op.add(Builder::intervals, IntervalsQuery._DESERIALIZER, "intervals");
		op.add(Builder::match, MatchQuery._DESERIALIZER, "match");
		op.add(Builder::matchAll, MatchAllQuery._DESERIALIZER, "match_all");
		op.add(Builder::matchBoolPrefix, MatchBoolPrefixQuery._DESERIALIZER, "match_bool_prefix");
		op.add(Builder::matchNone, MatchNoneQuery._DESERIALIZER, "match_none");
		op.add(Builder::matchPhrase, MatchPhraseQuery._DESERIALIZER, "match_phrase");
		op.add(Builder::matchPhrasePrefix, MatchPhrasePrefixQuery._DESERIALIZER, "match_phrase_prefix");
		op.add(Builder::moreLikeThis, MoreLikeThisQuery._DESERIALIZER, "more_like_this");
		op.add(Builder::multiMatch, MultiMatchQuery._DESERIALIZER, "multi_match");
		op.add(Builder::nested, NestedQuery._DESERIALIZER, "nested");
		op.add(Builder::parentId, ParentIdQuery._DESERIALIZER, "parent_id");
		op.add(Builder::percolate, PercolateQuery._DESERIALIZER, "percolate");
		op.add(Builder::pinned, PinnedQuery._DESERIALIZER, "pinned");
		op.add(Builder::prefix, PrefixQuery._DESERIALIZER, "prefix");
		op.add(Builder::queryString, QueryStringQuery._DESERIALIZER, "query_string");
		op.add(Builder::range, RangeQuery._DESERIALIZER, "range");
		op.add(Builder::rankFeature, RankFeatureQuery._DESERIALIZER, "rank_feature");
		op.add(Builder::regexp, RegexpQuery._DESERIALIZER, "regexp");
		op.add(Builder::script, ScriptQuery._DESERIALIZER, "script");
		op.add(Builder::scriptScore, ScriptScoreQuery._DESERIALIZER, "script_score");
		op.add(Builder::shape, ShapeQuery._DESERIALIZER, "shape");
		op.add(Builder::simpleQueryString, SimpleQueryStringQuery._DESERIALIZER, "simple_query_string");
		op.add(Builder::spanContaining, SpanContainingQuery._DESERIALIZER, "span_containing");
		op.add(Builder::fieldMaskingSpan, SpanFieldMaskingQuery._DESERIALIZER, "field_masking_span");
		op.add(Builder::spanFirst, SpanFirstQuery._DESERIALIZER, "span_first");
		op.add(Builder::spanMulti, SpanMultiTermQuery._DESERIALIZER, "span_multi");
		op.add(Builder::spanNear, SpanNearQuery._DESERIALIZER, "span_near");
		op.add(Builder::spanNot, SpanNotQuery._DESERIALIZER, "span_not");
		op.add(Builder::spanOr, SpanOrQuery._DESERIALIZER, "span_or");
		op.add(Builder::spanTerm, SpanTermQuery._DESERIALIZER, "span_term");
		op.add(Builder::spanWithin, SpanWithinQuery._DESERIALIZER, "span_within");
		op.add(Builder::term, TermQuery._DESERIALIZER, "term");
		op.add(Builder::terms, TermsQuery._DESERIALIZER, "terms");
		op.add(Builder::termsSet, TermsSetQuery._DESERIALIZER, "terms_set");
		op.add(Builder::wildcard, WildcardQuery._DESERIALIZER, "wildcard");
		op.add(Builder::type, TypeQuery._DESERIALIZER, "type");

	}

	public static final JsonpDeserializer<Query> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			Query::setupQueryDeserializer, Builder::build);
}
