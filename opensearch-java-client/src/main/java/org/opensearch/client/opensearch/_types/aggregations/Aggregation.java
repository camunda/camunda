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

import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.json.JsonData;
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
import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nullable;

// typedef: _types.aggregations.AggregationContainer

@JsonpDeserializable
public class Aggregation implements TaggedUnion<Aggregation.Kind, Object>, JsonpSerializable {

	/**
	 * {@link Aggregation} variant kinds.
	 */
	/**
	 * {@link Aggregation} variant kinds.
	 */

	public enum Kind implements JsonEnum {
		AdjacencyMatrix("adjacency_matrix"),

		AutoDateHistogram("auto_date_histogram"),

		Avg("avg"),

		AvgBucket("avg_bucket"),

		Boxplot("boxplot"),

		BucketScript("bucket_script"),

		BucketSelector("bucket_selector"),

		BucketSort("bucket_sort"),

		Cardinality("cardinality"),

		Children("children"),

		Composite("composite"),

		CumulativeCardinality("cumulative_cardinality"),

		CumulativeSum("cumulative_sum"),

		DateHistogram("date_histogram"),

		DateRange("date_range"),

		Derivative("derivative"),

		DiversifiedSampler("diversified_sampler"),

		ExtendedStats("extended_stats"),

		ExtendedStatsBucket("extended_stats_bucket"),

		Filter("filter"),

		Filters("filters"),

		GeoBounds("geo_bounds"),

		GeoCentroid("geo_centroid"),

		GeoDistance("geo_distance"),

		GeohashGrid("geohash_grid"),

		GeoLine("geo_line"),

		GeotileGrid("geotile_grid"),

		Global("global"),

		Histogram("histogram"),

		IpRange("ip_range"),

		Inference("inference"),

		MatrixStats("matrix_stats"),

		Max("max"),

		MaxBucket("max_bucket"),

		MedianAbsoluteDeviation("median_absolute_deviation"),

		Min("min"),

		MinBucket("min_bucket"),

		Missing("missing"),

		MovingAvg("moving_avg"),

		MovingPercentiles("moving_percentiles"),

		MovingFn("moving_fn"),

		MultiTerms("multi_terms"),

		Nested("nested"),

		Normalize("normalize"),

		Parent("parent"),

		PercentileRanks("percentile_ranks"),

		Percentiles("percentiles"),

		PercentilesBucket("percentiles_bucket"),

		Range("range"),

		RareTerms("rare_terms"),

		Rate("rate"),

		ReverseNested("reverse_nested"),

		Sampler("sampler"),

		ScriptedMetric("scripted_metric"),

		SerialDiff("serial_diff"),

		SignificantTerms("significant_terms"),

		SignificantText("significant_text"),

		Stats("stats"),

		StatsBucket("stats_bucket"),

		StringStats("string_stats"),

		Sum("sum"),

		SumBucket("sum_bucket"),

		Terms("terms"),

		TopHits("top_hits"),

		TTest("t_test"),

		TopMetrics("top_metrics"),

		ValueCount("value_count"),

		WeightedAvg("weighted_avg"),

		VariableWidthHistogram("variable_width_histogram"),

		;

		private final String jsonValue;

		Kind(String jsonValue) {
			this.jsonValue = jsonValue;
		}

		public String jsonValue() {
			return this.jsonValue;
		}

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

	private final Map<String, Aggregation> aggregations;

	private final Map<String, JsonData> meta;

	public Aggregation(AggregationVariant value) {

		this._kind = ApiTypeHelper.requireNonNull(value._aggregationKind(), this, "<variant kind>");
		this._value = ApiTypeHelper.requireNonNull(value, this, "<variant value>");

		this.aggregations = null;
		this.meta = null;

	}

	private Aggregation(Builder builder) {

		this._kind = ApiTypeHelper.requireNonNull(builder._kind, builder, "<variant kind>");
		this._value = ApiTypeHelper.requireNonNull(builder._value, builder, "<variant value>");

		this.aggregations = ApiTypeHelper.unmodifiable(builder.aggregations);
		this.meta = ApiTypeHelper.unmodifiable(builder.meta);

	}

	public static Aggregation of(Function<Builder, ObjectBuilder<Aggregation>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Sub-aggregations for this aggregation. Only applies to bucket aggregations.
	 * <p>
	 * API name: {@code aggregations}
	 */
	public final Map<String, Aggregation> aggregations() {
		return this.aggregations;
	}

	/**
	 * API name: {@code meta}
	 */
	public final Map<String, JsonData> meta() {
		return this.meta;
	}

	/**
	 * Is this variant instance of kind {@code adjacency_matrix}?
	 */
	public boolean isAdjacencyMatrix() {
		return _kind == Kind.AdjacencyMatrix;
	}

	/**
	 * Get the {@code adjacency_matrix} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code adjacency_matrix}
	 *             kind.
	 */
	public AdjacencyMatrixAggregation adjacencyMatrix() {
		return TaggedUnionUtils.get(this, Kind.AdjacencyMatrix);
	}

	/**
	 * Is this variant instance of kind {@code auto_date_histogram}?
	 */
	public boolean isAutoDateHistogram() {
		return _kind == Kind.AutoDateHistogram;
	}

	/**
	 * Get the {@code auto_date_histogram} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code auto_date_histogram}
	 *             kind.
	 */
	public AutoDateHistogramAggregation autoDateHistogram() {
		return TaggedUnionUtils.get(this, Kind.AutoDateHistogram);
	}

	/**
	 * Is this variant instance of kind {@code avg}?
	 */
	public boolean isAvg() {
		return _kind == Kind.Avg;
	}

	/**
	 * Get the {@code avg} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code avg} kind.
	 */
	public AverageAggregation avg() {
		return TaggedUnionUtils.get(this, Kind.Avg);
	}

	/**
	 * Is this variant instance of kind {@code avg_bucket}?
	 */
	public boolean isAvgBucket() {
		return _kind == Kind.AvgBucket;
	}

	/**
	 * Get the {@code avg_bucket} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code avg_bucket} kind.
	 */
	public AverageBucketAggregation avgBucket() {
		return TaggedUnionUtils.get(this, Kind.AvgBucket);
	}

	/**
	 * Is this variant instance of kind {@code boxplot}?
	 */
	public boolean isBoxplot() {
		return _kind == Kind.Boxplot;
	}

	/**
	 * Get the {@code boxplot} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code boxplot} kind.
	 */
	public BoxplotAggregation boxplot() {
		return TaggedUnionUtils.get(this, Kind.Boxplot);
	}

	/**
	 * Is this variant instance of kind {@code bucket_script}?
	 */
	public boolean isBucketScript() {
		return _kind == Kind.BucketScript;
	}

	/**
	 * Get the {@code bucket_script} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code bucket_script} kind.
	 */
	public BucketScriptAggregation bucketScript() {
		return TaggedUnionUtils.get(this, Kind.BucketScript);
	}

	/**
	 * Is this variant instance of kind {@code bucket_selector}?
	 */
	public boolean isBucketSelector() {
		return _kind == Kind.BucketSelector;
	}

	/**
	 * Get the {@code bucket_selector} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code bucket_selector}
	 *             kind.
	 */
	public BucketSelectorAggregation bucketSelector() {
		return TaggedUnionUtils.get(this, Kind.BucketSelector);
	}

	/**
	 * Is this variant instance of kind {@code bucket_sort}?
	 */
	public boolean isBucketSort() {
		return _kind == Kind.BucketSort;
	}

	/**
	 * Get the {@code bucket_sort} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code bucket_sort} kind.
	 */
	public BucketSortAggregation bucketSort() {
		return TaggedUnionUtils.get(this, Kind.BucketSort);
	}

	/**
	 * Is this variant instance of kind {@code cardinality}?
	 */
	public boolean isCardinality() {
		return _kind == Kind.Cardinality;
	}

	/**
	 * Get the {@code cardinality} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code cardinality} kind.
	 */
	public CardinalityAggregation cardinality() {
		return TaggedUnionUtils.get(this, Kind.Cardinality);
	}

	/**
	 * Is this variant instance of kind {@code children}?
	 */
	public boolean isChildren() {
		return _kind == Kind.Children;
	}

	/**
	 * Get the {@code children} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code children} kind.
	 */
	public ChildrenAggregation children() {
		return TaggedUnionUtils.get(this, Kind.Children);
	}

	/**
	 * Is this variant instance of kind {@code composite}?
	 */
	public boolean isComposite() {
		return _kind == Kind.Composite;
	}

	/**
	 * Get the {@code composite} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code composite} kind.
	 */
	public CompositeAggregation composite() {
		return TaggedUnionUtils.get(this, Kind.Composite);
	}

	/**
	 * Is this variant instance of kind {@code cumulative_cardinality}?
	 */
	public boolean isCumulativeCardinality() {
		return _kind == Kind.CumulativeCardinality;
	}

	/**
	 * Get the {@code cumulative_cardinality} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the
	 *             {@code cumulative_cardinality} kind.
	 */
	public CumulativeCardinalityAggregation cumulativeCardinality() {
		return TaggedUnionUtils.get(this, Kind.CumulativeCardinality);
	}

	/**
	 * Is this variant instance of kind {@code cumulative_sum}?
	 */
	public boolean isCumulativeSum() {
		return _kind == Kind.CumulativeSum;
	}

	/**
	 * Get the {@code cumulative_sum} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code cumulative_sum} kind.
	 */
	public CumulativeSumAggregation cumulativeSum() {
		return TaggedUnionUtils.get(this, Kind.CumulativeSum);
	}

	/**
	 * Is this variant instance of kind {@code date_histogram}?
	 */
	public boolean isDateHistogram() {
		return _kind == Kind.DateHistogram;
	}

	/**
	 * Get the {@code date_histogram} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code date_histogram} kind.
	 */
	public DateHistogramAggregation dateHistogram() {
		return TaggedUnionUtils.get(this, Kind.DateHistogram);
	}

	/**
	 * Is this variant instance of kind {@code date_range}?
	 */
	public boolean isDateRange() {
		return _kind == Kind.DateRange;
	}

	/**
	 * Get the {@code date_range} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code date_range} kind.
	 */
	public DateRangeAggregation dateRange() {
		return TaggedUnionUtils.get(this, Kind.DateRange);
	}

	/**
	 * Is this variant instance of kind {@code derivative}?
	 */
	public boolean isDerivative() {
		return _kind == Kind.Derivative;
	}

	/**
	 * Get the {@code derivative} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code derivative} kind.
	 */
	public DerivativeAggregation derivative() {
		return TaggedUnionUtils.get(this, Kind.Derivative);
	}

	/**
	 * Is this variant instance of kind {@code diversified_sampler}?
	 */
	public boolean isDiversifiedSampler() {
		return _kind == Kind.DiversifiedSampler;
	}

	/**
	 * Get the {@code diversified_sampler} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code diversified_sampler}
	 *             kind.
	 */
	public DiversifiedSamplerAggregation diversifiedSampler() {
		return TaggedUnionUtils.get(this, Kind.DiversifiedSampler);
	}

	/**
	 * Is this variant instance of kind {@code extended_stats}?
	 */
	public boolean isExtendedStats() {
		return _kind == Kind.ExtendedStats;
	}

	/**
	 * Get the {@code extended_stats} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code extended_stats} kind.
	 */
	public ExtendedStatsAggregation extendedStats() {
		return TaggedUnionUtils.get(this, Kind.ExtendedStats);
	}

	/**
	 * Is this variant instance of kind {@code extended_stats_bucket}?
	 */
	public boolean isExtendedStatsBucket() {
		return _kind == Kind.ExtendedStatsBucket;
	}

	/**
	 * Get the {@code extended_stats_bucket} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the
	 *             {@code extended_stats_bucket} kind.
	 */
	public ExtendedStatsBucketAggregation extendedStatsBucket() {
		return TaggedUnionUtils.get(this, Kind.ExtendedStatsBucket);
	}

	/**
	 * Is this variant instance of kind {@code filter}?
	 */
	public boolean isFilter() {
		return _kind == Kind.Filter;
	}

	/**
	 * Get the {@code filter} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code filter} kind.
	 */
	public Query filter() {
		return TaggedUnionUtils.get(this, Kind.Filter);
	}

	/**
	 * Is this variant instance of kind {@code filters}?
	 */
	public boolean isFilters() {
		return _kind == Kind.Filters;
	}

	/**
	 * Get the {@code filters} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code filters} kind.
	 */
	public FiltersAggregation filters() {
		return TaggedUnionUtils.get(this, Kind.Filters);
	}

	/**
	 * Is this variant instance of kind {@code geo_bounds}?
	 */
	public boolean isGeoBounds() {
		return _kind == Kind.GeoBounds;
	}

	/**
	 * Get the {@code geo_bounds} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code geo_bounds} kind.
	 */
	public GeoBoundsAggregation geoBounds() {
		return TaggedUnionUtils.get(this, Kind.GeoBounds);
	}

	/**
	 * Is this variant instance of kind {@code geo_centroid}?
	 */
	public boolean isGeoCentroid() {
		return _kind == Kind.GeoCentroid;
	}

	/**
	 * Get the {@code geo_centroid} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code geo_centroid} kind.
	 */
	public GeoCentroidAggregation geoCentroid() {
		return TaggedUnionUtils.get(this, Kind.GeoCentroid);
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
	public GeoDistanceAggregation geoDistance() {
		return TaggedUnionUtils.get(this, Kind.GeoDistance);
	}

	/**
	 * Is this variant instance of kind {@code geohash_grid}?
	 */
	public boolean isGeohashGrid() {
		return _kind == Kind.GeohashGrid;
	}

	/**
	 * Get the {@code geohash_grid} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code geohash_grid} kind.
	 */
	public GeoHashGridAggregation geohashGrid() {
		return TaggedUnionUtils.get(this, Kind.GeohashGrid);
	}

	/**
	 * Is this variant instance of kind {@code geo_line}?
	 */
	public boolean isGeoLine() {
		return _kind == Kind.GeoLine;
	}

	/**
	 * Get the {@code geo_line} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code geo_line} kind.
	 */
	public GeoLineAggregation geoLine() {
		return TaggedUnionUtils.get(this, Kind.GeoLine);
	}

	/**
	 * Is this variant instance of kind {@code geotile_grid}?
	 */
	public boolean isGeotileGrid() {
		return _kind == Kind.GeotileGrid;
	}

	/**
	 * Get the {@code geotile_grid} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code geotile_grid} kind.
	 */
	public GeoTileGridAggregation geotileGrid() {
		return TaggedUnionUtils.get(this, Kind.GeotileGrid);
	}

	/**
	 * Is this variant instance of kind {@code global}?
	 */
	public boolean isGlobal() {
		return _kind == Kind.Global;
	}

	/**
	 * Get the {@code global} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code global} kind.
	 */
	public GlobalAggregation global() {
		return TaggedUnionUtils.get(this, Kind.Global);
	}

	/**
	 * Is this variant instance of kind {@code histogram}?
	 */
	public boolean isHistogram() {
		return _kind == Kind.Histogram;
	}

	/**
	 * Get the {@code histogram} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code histogram} kind.
	 */
	public HistogramAggregation histogram() {
		return TaggedUnionUtils.get(this, Kind.Histogram);
	}

	/**
	 * Is this variant instance of kind {@code ip_range}?
	 */
	public boolean isIpRange() {
		return _kind == Kind.IpRange;
	}

	/**
	 * Get the {@code ip_range} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code ip_range} kind.
	 */
	public IpRangeAggregation ipRange() {
		return TaggedUnionUtils.get(this, Kind.IpRange);
	}

	/**
	 * Is this variant instance of kind {@code inference}?
	 */
	public boolean isInference() {
		return _kind == Kind.Inference;
	}

	/**
	 * Get the {@code inference} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code inference} kind.
	 */
	public InferenceAggregation inference() {
		return TaggedUnionUtils.get(this, Kind.Inference);
	}

	/**
	 * Is this variant instance of kind {@code matrix_stats}?
	 */
	public boolean isMatrixStats() {
		return _kind == Kind.MatrixStats;
	}

	/**
	 * Get the {@code matrix_stats} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code matrix_stats} kind.
	 */
	public MatrixStatsAggregation matrixStats() {
		return TaggedUnionUtils.get(this, Kind.MatrixStats);
	}

	/**
	 * Is this variant instance of kind {@code max}?
	 */
	public boolean isMax() {
		return _kind == Kind.Max;
	}

	/**
	 * Get the {@code max} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code max} kind.
	 */
	public MaxAggregation max() {
		return TaggedUnionUtils.get(this, Kind.Max);
	}

	/**
	 * Is this variant instance of kind {@code max_bucket}?
	 */
	public boolean isMaxBucket() {
		return _kind == Kind.MaxBucket;
	}

	/**
	 * Get the {@code max_bucket} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code max_bucket} kind.
	 */
	public MaxBucketAggregation maxBucket() {
		return TaggedUnionUtils.get(this, Kind.MaxBucket);
	}

	/**
	 * Is this variant instance of kind {@code median_absolute_deviation}?
	 */
	public boolean isMedianAbsoluteDeviation() {
		return _kind == Kind.MedianAbsoluteDeviation;
	}

	/**
	 * Get the {@code median_absolute_deviation} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the
	 *             {@code median_absolute_deviation} kind.
	 */
	public MedianAbsoluteDeviationAggregation medianAbsoluteDeviation() {
		return TaggedUnionUtils.get(this, Kind.MedianAbsoluteDeviation);
	}

	/**
	 * Is this variant instance of kind {@code min}?
	 */
	public boolean isMin() {
		return _kind == Kind.Min;
	}

	/**
	 * Get the {@code min} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code min} kind.
	 */
	public MinAggregation min() {
		return TaggedUnionUtils.get(this, Kind.Min);
	}

	/**
	 * Is this variant instance of kind {@code min_bucket}?
	 */
	public boolean isMinBucket() {
		return _kind == Kind.MinBucket;
	}

	/**
	 * Get the {@code min_bucket} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code min_bucket} kind.
	 */
	public MinBucketAggregation minBucket() {
		return TaggedUnionUtils.get(this, Kind.MinBucket);
	}

	/**
	 * Is this variant instance of kind {@code missing}?
	 */
	public boolean isMissing() {
		return _kind == Kind.Missing;
	}

	/**
	 * Get the {@code missing} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code missing} kind.
	 */
	public MissingAggregation missing() {
		return TaggedUnionUtils.get(this, Kind.Missing);
	}

	/**
	 * Is this variant instance of kind {@code moving_avg}?
	 */
	public boolean isMovingAvg() {
		return _kind == Kind.MovingAvg;
	}

	/**
	 * Get the {@code moving_avg} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code moving_avg} kind.
	 */
	public MovingAverageAggregation movingAvg() {
		return TaggedUnionUtils.get(this, Kind.MovingAvg);
	}

	/**
	 * Is this variant instance of kind {@code moving_percentiles}?
	 */
	public boolean isMovingPercentiles() {
		return _kind == Kind.MovingPercentiles;
	}

	/**
	 * Get the {@code moving_percentiles} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code moving_percentiles}
	 *             kind.
	 */
	public MovingPercentilesAggregation movingPercentiles() {
		return TaggedUnionUtils.get(this, Kind.MovingPercentiles);
	}

	/**
	 * Is this variant instance of kind {@code moving_fn}?
	 */
	public boolean isMovingFn() {
		return _kind == Kind.MovingFn;
	}

	/**
	 * Get the {@code moving_fn} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code moving_fn} kind.
	 */
	public MovingFunctionAggregation movingFn() {
		return TaggedUnionUtils.get(this, Kind.MovingFn);
	}

	/**
	 * Is this variant instance of kind {@code multi_terms}?
	 */
	public boolean isMultiTerms() {
		return _kind == Kind.MultiTerms;
	}

	/**
	 * Get the {@code multi_terms} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code multi_terms} kind.
	 */
	public MultiTermsAggregation multiTerms() {
		return TaggedUnionUtils.get(this, Kind.MultiTerms);
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
	public NestedAggregation nested() {
		return TaggedUnionUtils.get(this, Kind.Nested);
	}

	/**
	 * Is this variant instance of kind {@code normalize}?
	 */
	public boolean isNormalize() {
		return _kind == Kind.Normalize;
	}

	/**
	 * Get the {@code normalize} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code normalize} kind.
	 */
	public NormalizeAggregation normalize() {
		return TaggedUnionUtils.get(this, Kind.Normalize);
	}

	/**
	 * Is this variant instance of kind {@code parent}?
	 */
	public boolean isParent() {
		return _kind == Kind.Parent;
	}

	/**
	 * Get the {@code parent} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code parent} kind.
	 */
	public ParentAggregation parent() {
		return TaggedUnionUtils.get(this, Kind.Parent);
	}

	/**
	 * Is this variant instance of kind {@code percentile_ranks}?
	 */
	public boolean isPercentileRanks() {
		return _kind == Kind.PercentileRanks;
	}

	/**
	 * Get the {@code percentile_ranks} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code percentile_ranks}
	 *             kind.
	 */
	public PercentileRanksAggregation percentileRanks() {
		return TaggedUnionUtils.get(this, Kind.PercentileRanks);
	}

	/**
	 * Is this variant instance of kind {@code percentiles}?
	 */
	public boolean isPercentiles() {
		return _kind == Kind.Percentiles;
	}

	/**
	 * Get the {@code percentiles} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code percentiles} kind.
	 */
	public PercentilesAggregation percentiles() {
		return TaggedUnionUtils.get(this, Kind.Percentiles);
	}

	/**
	 * Is this variant instance of kind {@code percentiles_bucket}?
	 */
	public boolean isPercentilesBucket() {
		return _kind == Kind.PercentilesBucket;
	}

	/**
	 * Get the {@code percentiles_bucket} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code percentiles_bucket}
	 *             kind.
	 */
	public PercentilesBucketAggregation percentilesBucket() {
		return TaggedUnionUtils.get(this, Kind.PercentilesBucket);
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
	public RangeAggregation range() {
		return TaggedUnionUtils.get(this, Kind.Range);
	}

	/**
	 * Is this variant instance of kind {@code rare_terms}?
	 */
	public boolean isRareTerms() {
		return _kind == Kind.RareTerms;
	}

	/**
	 * Get the {@code rare_terms} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code rare_terms} kind.
	 */
	public RareTermsAggregation rareTerms() {
		return TaggedUnionUtils.get(this, Kind.RareTerms);
	}

	/**
	 * Is this variant instance of kind {@code rate}?
	 */
	public boolean isRate() {
		return _kind == Kind.Rate;
	}

	/**
	 * Get the {@code rate} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code rate} kind.
	 */
	public RateAggregation rate() {
		return TaggedUnionUtils.get(this, Kind.Rate);
	}

	/**
	 * Is this variant instance of kind {@code reverse_nested}?
	 */
	public boolean isReverseNested() {
		return _kind == Kind.ReverseNested;
	}

	/**
	 * Get the {@code reverse_nested} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code reverse_nested} kind.
	 */
	public ReverseNestedAggregation reverseNested() {
		return TaggedUnionUtils.get(this, Kind.ReverseNested);
	}

	/**
	 * Is this variant instance of kind {@code sampler}?
	 */
	public boolean isSampler() {
		return _kind == Kind.Sampler;
	}

	/**
	 * Get the {@code sampler} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code sampler} kind.
	 */
	public SamplerAggregation sampler() {
		return TaggedUnionUtils.get(this, Kind.Sampler);
	}

	/**
	 * Is this variant instance of kind {@code scripted_metric}?
	 */
	public boolean isScriptedMetric() {
		return _kind == Kind.ScriptedMetric;
	}

	/**
	 * Get the {@code scripted_metric} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code scripted_metric}
	 *             kind.
	 */
	public ScriptedMetricAggregation scriptedMetric() {
		return TaggedUnionUtils.get(this, Kind.ScriptedMetric);
	}

	/**
	 * Is this variant instance of kind {@code serial_diff}?
	 */
	public boolean isSerialDiff() {
		return _kind == Kind.SerialDiff;
	}

	/**
	 * Get the {@code serial_diff} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code serial_diff} kind.
	 */
	public SerialDifferencingAggregation serialDiff() {
		return TaggedUnionUtils.get(this, Kind.SerialDiff);
	}

	/**
	 * Is this variant instance of kind {@code significant_terms}?
	 */
	public boolean isSignificantTerms() {
		return _kind == Kind.SignificantTerms;
	}

	/**
	 * Get the {@code significant_terms} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code significant_terms}
	 *             kind.
	 */
	public SignificantTermsAggregation significantTerms() {
		return TaggedUnionUtils.get(this, Kind.SignificantTerms);
	}

	/**
	 * Is this variant instance of kind {@code significant_text}?
	 */
	public boolean isSignificantText() {
		return _kind == Kind.SignificantText;
	}

	/**
	 * Get the {@code significant_text} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code significant_text}
	 *             kind.
	 */
	public SignificantTextAggregation significantText() {
		return TaggedUnionUtils.get(this, Kind.SignificantText);
	}

	/**
	 * Is this variant instance of kind {@code stats}?
	 */
	public boolean isStats() {
		return _kind == Kind.Stats;
	}

	/**
	 * Get the {@code stats} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code stats} kind.
	 */
	public StatsAggregation stats() {
		return TaggedUnionUtils.get(this, Kind.Stats);
	}

	/**
	 * Is this variant instance of kind {@code stats_bucket}?
	 */
	public boolean isStatsBucket() {
		return _kind == Kind.StatsBucket;
	}

	/**
	 * Get the {@code stats_bucket} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code stats_bucket} kind.
	 */
	public StatsBucketAggregation statsBucket() {
		return TaggedUnionUtils.get(this, Kind.StatsBucket);
	}

	/**
	 * Is this variant instance of kind {@code string_stats}?
	 */
	public boolean isStringStats() {
		return _kind == Kind.StringStats;
	}

	/**
	 * Get the {@code string_stats} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code string_stats} kind.
	 */
	public StringStatsAggregation stringStats() {
		return TaggedUnionUtils.get(this, Kind.StringStats);
	}

	/**
	 * Is this variant instance of kind {@code sum}?
	 */
	public boolean isSum() {
		return _kind == Kind.Sum;
	}

	/**
	 * Get the {@code sum} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code sum} kind.
	 */
	public SumAggregation sum() {
		return TaggedUnionUtils.get(this, Kind.Sum);
	}

	/**
	 * Is this variant instance of kind {@code sum_bucket}?
	 */
	public boolean isSumBucket() {
		return _kind == Kind.SumBucket;
	}

	/**
	 * Get the {@code sum_bucket} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code sum_bucket} kind.
	 */
	public SumBucketAggregation sumBucket() {
		return TaggedUnionUtils.get(this, Kind.SumBucket);
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
	public TermsAggregation terms() {
		return TaggedUnionUtils.get(this, Kind.Terms);
	}

	/**
	 * Is this variant instance of kind {@code top_hits}?
	 */
	public boolean isTopHits() {
		return _kind == Kind.TopHits;
	}

	/**
	 * Get the {@code top_hits} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code top_hits} kind.
	 */
	public TopHitsAggregation topHits() {
		return TaggedUnionUtils.get(this, Kind.TopHits);
	}

	/**
	 * Is this variant instance of kind {@code t_test}?
	 */
	public boolean isTTest() {
		return _kind == Kind.TTest;
	}

	/**
	 * Get the {@code t_test} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code t_test} kind.
	 */
	public TTestAggregation tTest() {
		return TaggedUnionUtils.get(this, Kind.TTest);
	}

	/**
	 * Is this variant instance of kind {@code top_metrics}?
	 */
	public boolean isTopMetrics() {
		return _kind == Kind.TopMetrics;
	}

	/**
	 * Get the {@code top_metrics} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code top_metrics} kind.
	 */
	public TopMetricsAggregation topMetrics() {
		return TaggedUnionUtils.get(this, Kind.TopMetrics);
	}

	/**
	 * Is this variant instance of kind {@code value_count}?
	 */
	public boolean isValueCount() {
		return _kind == Kind.ValueCount;
	}

	/**
	 * Get the {@code value_count} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code value_count} kind.
	 */
	public ValueCountAggregation valueCount() {
		return TaggedUnionUtils.get(this, Kind.ValueCount);
	}

	/**
	 * Is this variant instance of kind {@code weighted_avg}?
	 */
	public boolean isWeightedAvg() {
		return _kind == Kind.WeightedAvg;
	}

	/**
	 * Get the {@code weighted_avg} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code weighted_avg} kind.
	 */
	public WeightedAverageAggregation weightedAvg() {
		return TaggedUnionUtils.get(this, Kind.WeightedAvg);
	}

	/**
	 * Is this variant instance of kind {@code variable_width_histogram}?
	 */
	public boolean isVariableWidthHistogram() {
		return _kind == Kind.VariableWidthHistogram;
	}

	/**
	 * Get the {@code variable_width_histogram} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the
	 *             {@code variable_width_histogram} kind.
	 */
	public VariableWidthHistogramAggregation variableWidthHistogram() {
		return TaggedUnionUtils.get(this, Kind.VariableWidthHistogram);
	}

	@Override
	@SuppressWarnings("unchecked")
	public void serialize(JsonGenerator generator, JsonpMapper mapper) {

		generator.writeStartObject();

		if (ApiTypeHelper.isDefined(this.aggregations)) {
			generator.writeKey("aggregations");
			generator.writeStartObject();
			for (Map.Entry<String, Aggregation> item0 : this.aggregations.entrySet()) {
				generator.writeKey(item0.getKey());
				item0.getValue().serialize(generator, mapper);

			}
			generator.writeEnd();

		}
		if (ApiTypeHelper.isDefined(this.meta)) {
			generator.writeKey("meta");
			generator.writeStartObject();
			for (Map.Entry<String, JsonData> item0 : this.meta.entrySet()) {
				generator.writeKey(item0.getKey());
				item0.getValue().serialize(generator, mapper);

			}
			generator.writeEnd();

		}

		generator.writeKey(_kind.jsonValue());
		if (_value instanceof JsonpSerializable) {
			((JsonpSerializable) _value).serialize(generator, mapper);
		}

		generator.writeEnd();

	}

	public static class Builder extends ObjectBuilderBase {
		private Kind _kind;
		private Object _value;

		@Nullable
		private Map<String, Aggregation> aggregations;

		@Nullable
		private Map<String, JsonData> meta;

		/**
		 * Sub-aggregations for this aggregation. Only applies to bucket aggregations.
		 * <p>
		 * API name: {@code aggregations}
		 * <p>
		 * Adds all entries of <code>map</code> to <code>aggregations</code>.
		 */
		public final Builder aggregations(Map<String, Aggregation> map) {
			this.aggregations = _mapPutAll(this.aggregations, map);
			return this;
		}

		/**
		 * Sub-aggregations for this aggregation. Only applies to bucket aggregations.
		 * <p>
		 * API name: {@code aggregations}
		 * <p>
		 * Adds an entry to <code>aggregations</code>.
		 */
		public final Builder aggregations(String key, Aggregation value) {
			this.aggregations = _mapPut(this.aggregations, key, value);
			return this;
		}

		/**
		 * Sub-aggregations for this aggregation. Only applies to bucket aggregations.
		 * <p>
		 * API name: {@code aggregations}
		 * <p>
		 * Adds an entry to <code>aggregations</code> using a builder lambda.
		 */
		public final Builder aggregations(String key, Function<Aggregation.Builder, ObjectBuilder<Aggregation>> fn) {
			return aggregations(key, fn.apply(new Aggregation.Builder()).build());
		}

		/**
		 * API name: {@code meta}
		 * <p>
		 * Adds all entries of <code>map</code> to <code>meta</code>.
		 */
		public final Builder meta(Map<String, JsonData> map) {
			this.meta = _mapPutAll(this.meta, map);
			return this;
		}

		/**
		 * API name: {@code meta}
		 * <p>
		 * Adds an entry to <code>meta</code>.
		 */
		public final Builder meta(String key, JsonData value) {
			this.meta = _mapPut(this.meta, key, value);
			return this;
		}

		public ContainerBuilder adjacencyMatrix(AdjacencyMatrixAggregation v) {
			this._kind = Kind.AdjacencyMatrix;
			this._value = v;
			return new ContainerBuilder();
		}

		public ContainerBuilder adjacencyMatrix(
				Function<AdjacencyMatrixAggregation.Builder, ObjectBuilder<AdjacencyMatrixAggregation>> fn) {
			return this.adjacencyMatrix(fn.apply(new AdjacencyMatrixAggregation.Builder()).build());
		}

		public ContainerBuilder autoDateHistogram(AutoDateHistogramAggregation v) {
			this._kind = Kind.AutoDateHistogram;
			this._value = v;
			return new ContainerBuilder();
		}

		public ContainerBuilder autoDateHistogram(
				Function<AutoDateHistogramAggregation.Builder, ObjectBuilder<AutoDateHistogramAggregation>> fn) {
			return this.autoDateHistogram(fn.apply(new AutoDateHistogramAggregation.Builder()).build());
		}

		public ContainerBuilder avg(AverageAggregation v) {
			this._kind = Kind.Avg;
			this._value = v;
			return new ContainerBuilder();
		}

		public ContainerBuilder avg(Function<AverageAggregation.Builder, ObjectBuilder<AverageAggregation>> fn) {
			return this.avg(fn.apply(new AverageAggregation.Builder()).build());
		}

		public ContainerBuilder avgBucket(AverageBucketAggregation v) {
			this._kind = Kind.AvgBucket;
			this._value = v;
			return new ContainerBuilder();
		}

		public ContainerBuilder avgBucket(
				Function<AverageBucketAggregation.Builder, ObjectBuilder<AverageBucketAggregation>> fn) {
			return this.avgBucket(fn.apply(new AverageBucketAggregation.Builder()).build());
		}

		public ContainerBuilder boxplot(BoxplotAggregation v) {
			this._kind = Kind.Boxplot;
			this._value = v;
			return new ContainerBuilder();
		}

		public ContainerBuilder boxplot(Function<BoxplotAggregation.Builder, ObjectBuilder<BoxplotAggregation>> fn) {
			return this.boxplot(fn.apply(new BoxplotAggregation.Builder()).build());
		}

		public ContainerBuilder bucketScript(BucketScriptAggregation v) {
			this._kind = Kind.BucketScript;
			this._value = v;
			return new ContainerBuilder();
		}

		public ContainerBuilder bucketScript(
				Function<BucketScriptAggregation.Builder, ObjectBuilder<BucketScriptAggregation>> fn) {
			return this.bucketScript(fn.apply(new BucketScriptAggregation.Builder()).build());
		}

		public ContainerBuilder bucketSelector(BucketSelectorAggregation v) {
			this._kind = Kind.BucketSelector;
			this._value = v;
			return new ContainerBuilder();
		}

		public ContainerBuilder bucketSelector(
				Function<BucketSelectorAggregation.Builder, ObjectBuilder<BucketSelectorAggregation>> fn) {
			return this.bucketSelector(fn.apply(new BucketSelectorAggregation.Builder()).build());
		}

		public ContainerBuilder bucketSort(BucketSortAggregation v) {
			this._kind = Kind.BucketSort;
			this._value = v;
			return new ContainerBuilder();
		}

		public ContainerBuilder bucketSort(
				Function<BucketSortAggregation.Builder, ObjectBuilder<BucketSortAggregation>> fn) {
			return this.bucketSort(fn.apply(new BucketSortAggregation.Builder()).build());
		}

		public ContainerBuilder cardinality(CardinalityAggregation v) {
			this._kind = Kind.Cardinality;
			this._value = v;
			return new ContainerBuilder();
		}

		public ContainerBuilder cardinality(
				Function<CardinalityAggregation.Builder, ObjectBuilder<CardinalityAggregation>> fn) {
			return this.cardinality(fn.apply(new CardinalityAggregation.Builder()).build());
		}

		public ContainerBuilder children(ChildrenAggregation v) {
			this._kind = Kind.Children;
			this._value = v;
			return new ContainerBuilder();
		}

		public ContainerBuilder children(Function<ChildrenAggregation.Builder, ObjectBuilder<ChildrenAggregation>> fn) {
			return this.children(fn.apply(new ChildrenAggregation.Builder()).build());
		}

		public ContainerBuilder composite(CompositeAggregation v) {
			this._kind = Kind.Composite;
			this._value = v;
			return new ContainerBuilder();
		}

		public ContainerBuilder composite(
				Function<CompositeAggregation.Builder, ObjectBuilder<CompositeAggregation>> fn) {
			return this.composite(fn.apply(new CompositeAggregation.Builder()).build());
		}

		public ContainerBuilder cumulativeCardinality(CumulativeCardinalityAggregation v) {
			this._kind = Kind.CumulativeCardinality;
			this._value = v;
			return new ContainerBuilder();
		}

		public ContainerBuilder cumulativeCardinality(
				Function<CumulativeCardinalityAggregation.Builder, ObjectBuilder<CumulativeCardinalityAggregation>> fn) {
			return this.cumulativeCardinality(fn.apply(new CumulativeCardinalityAggregation.Builder()).build());
		}

		public ContainerBuilder cumulativeSum(CumulativeSumAggregation v) {
			this._kind = Kind.CumulativeSum;
			this._value = v;
			return new ContainerBuilder();
		}

		public ContainerBuilder cumulativeSum(
				Function<CumulativeSumAggregation.Builder, ObjectBuilder<CumulativeSumAggregation>> fn) {
			return this.cumulativeSum(fn.apply(new CumulativeSumAggregation.Builder()).build());
		}

		public ContainerBuilder dateHistogram(DateHistogramAggregation v) {
			this._kind = Kind.DateHistogram;
			this._value = v;
			return new ContainerBuilder();
		}

		public ContainerBuilder dateHistogram(
				Function<DateHistogramAggregation.Builder, ObjectBuilder<DateHistogramAggregation>> fn) {
			return this.dateHistogram(fn.apply(new DateHistogramAggregation.Builder()).build());
		}

		public ContainerBuilder dateRange(DateRangeAggregation v) {
			this._kind = Kind.DateRange;
			this._value = v;
			return new ContainerBuilder();
		}

		public ContainerBuilder dateRange(
				Function<DateRangeAggregation.Builder, ObjectBuilder<DateRangeAggregation>> fn) {
			return this.dateRange(fn.apply(new DateRangeAggregation.Builder()).build());
		}

		public ContainerBuilder derivative(DerivativeAggregation v) {
			this._kind = Kind.Derivative;
			this._value = v;
			return new ContainerBuilder();
		}

		public ContainerBuilder derivative(
				Function<DerivativeAggregation.Builder, ObjectBuilder<DerivativeAggregation>> fn) {
			return this.derivative(fn.apply(new DerivativeAggregation.Builder()).build());
		}

		public ContainerBuilder diversifiedSampler(DiversifiedSamplerAggregation v) {
			this._kind = Kind.DiversifiedSampler;
			this._value = v;
			return new ContainerBuilder();
		}

		public ContainerBuilder diversifiedSampler(
				Function<DiversifiedSamplerAggregation.Builder, ObjectBuilder<DiversifiedSamplerAggregation>> fn) {
			return this.diversifiedSampler(fn.apply(new DiversifiedSamplerAggregation.Builder()).build());
		}

		public ContainerBuilder extendedStats(ExtendedStatsAggregation v) {
			this._kind = Kind.ExtendedStats;
			this._value = v;
			return new ContainerBuilder();
		}

		public ContainerBuilder extendedStats(
				Function<ExtendedStatsAggregation.Builder, ObjectBuilder<ExtendedStatsAggregation>> fn) {
			return this.extendedStats(fn.apply(new ExtendedStatsAggregation.Builder()).build());
		}

		public ContainerBuilder extendedStatsBucket(ExtendedStatsBucketAggregation v) {
			this._kind = Kind.ExtendedStatsBucket;
			this._value = v;
			return new ContainerBuilder();
		}

		public ContainerBuilder extendedStatsBucket(
				Function<ExtendedStatsBucketAggregation.Builder, ObjectBuilder<ExtendedStatsBucketAggregation>> fn) {
			return this.extendedStatsBucket(fn.apply(new ExtendedStatsBucketAggregation.Builder()).build());
		}

		public ContainerBuilder filter(Query v) {
			this._kind = Kind.Filter;
			this._value = v;
			return new ContainerBuilder();
		}

		public ContainerBuilder filter(Function<Query.Builder, ObjectBuilder<Query>> fn) {
			return this.filter(fn.apply(new Query.Builder()).build());
		}

		public ContainerBuilder filters(FiltersAggregation v) {
			this._kind = Kind.Filters;
			this._value = v;
			return new ContainerBuilder();
		}

		public ContainerBuilder filters(Function<FiltersAggregation.Builder, ObjectBuilder<FiltersAggregation>> fn) {
			return this.filters(fn.apply(new FiltersAggregation.Builder()).build());
		}

		public ContainerBuilder geoBounds(GeoBoundsAggregation v) {
			this._kind = Kind.GeoBounds;
			this._value = v;
			return new ContainerBuilder();
		}

		public ContainerBuilder geoBounds(
				Function<GeoBoundsAggregation.Builder, ObjectBuilder<GeoBoundsAggregation>> fn) {
			return this.geoBounds(fn.apply(new GeoBoundsAggregation.Builder()).build());
		}

		public ContainerBuilder geoCentroid(GeoCentroidAggregation v) {
			this._kind = Kind.GeoCentroid;
			this._value = v;
			return new ContainerBuilder();
		}

		public ContainerBuilder geoCentroid(
				Function<GeoCentroidAggregation.Builder, ObjectBuilder<GeoCentroidAggregation>> fn) {
			return this.geoCentroid(fn.apply(new GeoCentroidAggregation.Builder()).build());
		}

		public ContainerBuilder geoDistance(GeoDistanceAggregation v) {
			this._kind = Kind.GeoDistance;
			this._value = v;
			return new ContainerBuilder();
		}

		public ContainerBuilder geoDistance(
				Function<GeoDistanceAggregation.Builder, ObjectBuilder<GeoDistanceAggregation>> fn) {
			return this.geoDistance(fn.apply(new GeoDistanceAggregation.Builder()).build());
		}

		public ContainerBuilder geohashGrid(GeoHashGridAggregation v) {
			this._kind = Kind.GeohashGrid;
			this._value = v;
			return new ContainerBuilder();
		}

		public ContainerBuilder geohashGrid(
				Function<GeoHashGridAggregation.Builder, ObjectBuilder<GeoHashGridAggregation>> fn) {
			return this.geohashGrid(fn.apply(new GeoHashGridAggregation.Builder()).build());
		}

		public ContainerBuilder geoLine(GeoLineAggregation v) {
			this._kind = Kind.GeoLine;
			this._value = v;
			return new ContainerBuilder();
		}

		public ContainerBuilder geoLine(Function<GeoLineAggregation.Builder, ObjectBuilder<GeoLineAggregation>> fn) {
			return this.geoLine(fn.apply(new GeoLineAggregation.Builder()).build());
		}

		public ContainerBuilder geotileGrid(GeoTileGridAggregation v) {
			this._kind = Kind.GeotileGrid;
			this._value = v;
			return new ContainerBuilder();
		}

		public ContainerBuilder geotileGrid(
				Function<GeoTileGridAggregation.Builder, ObjectBuilder<GeoTileGridAggregation>> fn) {
			return this.geotileGrid(fn.apply(new GeoTileGridAggregation.Builder()).build());
		}

		public ContainerBuilder global(GlobalAggregation v) {
			this._kind = Kind.Global;
			this._value = v;
			return new ContainerBuilder();
		}

		public ContainerBuilder global(Function<GlobalAggregation.Builder, ObjectBuilder<GlobalAggregation>> fn) {
			return this.global(fn.apply(new GlobalAggregation.Builder()).build());
		}

		public ContainerBuilder histogram(HistogramAggregation v) {
			this._kind = Kind.Histogram;
			this._value = v;
			return new ContainerBuilder();
		}

		public ContainerBuilder histogram(
				Function<HistogramAggregation.Builder, ObjectBuilder<HistogramAggregation>> fn) {
			return this.histogram(fn.apply(new HistogramAggregation.Builder()).build());
		}

		public ContainerBuilder ipRange(IpRangeAggregation v) {
			this._kind = Kind.IpRange;
			this._value = v;
			return new ContainerBuilder();
		}

		public ContainerBuilder ipRange(Function<IpRangeAggregation.Builder, ObjectBuilder<IpRangeAggregation>> fn) {
			return this.ipRange(fn.apply(new IpRangeAggregation.Builder()).build());
		}

		public ContainerBuilder inference(InferenceAggregation v) {
			this._kind = Kind.Inference;
			this._value = v;
			return new ContainerBuilder();
		}

		public ContainerBuilder inference(
				Function<InferenceAggregation.Builder, ObjectBuilder<InferenceAggregation>> fn) {
			return this.inference(fn.apply(new InferenceAggregation.Builder()).build());
		}

		public ContainerBuilder matrixStats(MatrixStatsAggregation v) {
			this._kind = Kind.MatrixStats;
			this._value = v;
			return new ContainerBuilder();
		}

		public ContainerBuilder matrixStats(
				Function<MatrixStatsAggregation.Builder, ObjectBuilder<MatrixStatsAggregation>> fn) {
			return this.matrixStats(fn.apply(new MatrixStatsAggregation.Builder()).build());
		}

		public ContainerBuilder max(MaxAggregation v) {
			this._kind = Kind.Max;
			this._value = v;
			return new ContainerBuilder();
		}

		public ContainerBuilder max(Function<MaxAggregation.Builder, ObjectBuilder<MaxAggregation>> fn) {
			return this.max(fn.apply(new MaxAggregation.Builder()).build());
		}

		public ContainerBuilder maxBucket(MaxBucketAggregation v) {
			this._kind = Kind.MaxBucket;
			this._value = v;
			return new ContainerBuilder();
		}

		public ContainerBuilder maxBucket(
				Function<MaxBucketAggregation.Builder, ObjectBuilder<MaxBucketAggregation>> fn) {
			return this.maxBucket(fn.apply(new MaxBucketAggregation.Builder()).build());
		}

		public ContainerBuilder medianAbsoluteDeviation(MedianAbsoluteDeviationAggregation v) {
			this._kind = Kind.MedianAbsoluteDeviation;
			this._value = v;
			return new ContainerBuilder();
		}

		public ContainerBuilder medianAbsoluteDeviation(
				Function<MedianAbsoluteDeviationAggregation.Builder,
						ObjectBuilder<MedianAbsoluteDeviationAggregation>> fn) {
			return this.medianAbsoluteDeviation(fn.apply(new MedianAbsoluteDeviationAggregation.Builder()).build());
		}

		public ContainerBuilder min(MinAggregation v) {
			this._kind = Kind.Min;
			this._value = v;
			return new ContainerBuilder();
		}

		public ContainerBuilder min(Function<MinAggregation.Builder, ObjectBuilder<MinAggregation>> fn) {
			return this.min(fn.apply(new MinAggregation.Builder()).build());
		}

		public ContainerBuilder minBucket(MinBucketAggregation v) {
			this._kind = Kind.MinBucket;
			this._value = v;
			return new ContainerBuilder();
		}

		public ContainerBuilder minBucket(
				Function<MinBucketAggregation.Builder, ObjectBuilder<MinBucketAggregation>> fn) {
			return this.minBucket(fn.apply(new MinBucketAggregation.Builder()).build());
		}

		public ContainerBuilder missing(MissingAggregation v) {
			this._kind = Kind.Missing;
			this._value = v;
			return new ContainerBuilder();
		}

		public ContainerBuilder missing(Function<MissingAggregation.Builder, ObjectBuilder<MissingAggregation>> fn) {
			return this.missing(fn.apply(new MissingAggregation.Builder()).build());
		}

		public ContainerBuilder movingAvg(MovingAverageAggregation v) {
			this._kind = Kind.MovingAvg;
			this._value = v;
			return new ContainerBuilder();
		}

		public ContainerBuilder movingAvg(
				Function<MovingAverageAggregation.Builder, ObjectBuilder<MovingAverageAggregation>> fn) {
			return this.movingAvg(fn.apply(new MovingAverageAggregation.Builder()).build());
		}

		public ContainerBuilder movingPercentiles(MovingPercentilesAggregation v) {
			this._kind = Kind.MovingPercentiles;
			this._value = v;
			return new ContainerBuilder();
		}

		public ContainerBuilder movingPercentiles(
				Function<MovingPercentilesAggregation.Builder, ObjectBuilder<MovingPercentilesAggregation>> fn) {
			return this.movingPercentiles(fn.apply(new MovingPercentilesAggregation.Builder()).build());
		}

		public ContainerBuilder movingFn(MovingFunctionAggregation v) {
			this._kind = Kind.MovingFn;
			this._value = v;
			return new ContainerBuilder();
		}

		public ContainerBuilder movingFn(
				Function<MovingFunctionAggregation.Builder, ObjectBuilder<MovingFunctionAggregation>> fn) {
			return this.movingFn(fn.apply(new MovingFunctionAggregation.Builder()).build());
		}

		public ContainerBuilder multiTerms(MultiTermsAggregation v) {
			this._kind = Kind.MultiTerms;
			this._value = v;
			return new ContainerBuilder();
		}

		public ContainerBuilder multiTerms(
				Function<MultiTermsAggregation.Builder, ObjectBuilder<MultiTermsAggregation>> fn) {
			return this.multiTerms(fn.apply(new MultiTermsAggregation.Builder()).build());
		}

		public ContainerBuilder nested(NestedAggregation v) {
			this._kind = Kind.Nested;
			this._value = v;
			return new ContainerBuilder();
		}

		public ContainerBuilder nested(Function<NestedAggregation.Builder, ObjectBuilder<NestedAggregation>> fn) {
			return this.nested(fn.apply(new NestedAggregation.Builder()).build());
		}

		public ContainerBuilder normalize(NormalizeAggregation v) {
			this._kind = Kind.Normalize;
			this._value = v;
			return new ContainerBuilder();
		}

		public ContainerBuilder normalize(
				Function<NormalizeAggregation.Builder, ObjectBuilder<NormalizeAggregation>> fn) {
			return this.normalize(fn.apply(new NormalizeAggregation.Builder()).build());
		}

		public ContainerBuilder parent(ParentAggregation v) {
			this._kind = Kind.Parent;
			this._value = v;
			return new ContainerBuilder();
		}

		public ContainerBuilder parent(Function<ParentAggregation.Builder, ObjectBuilder<ParentAggregation>> fn) {
			return this.parent(fn.apply(new ParentAggregation.Builder()).build());
		}

		public ContainerBuilder percentileRanks(PercentileRanksAggregation v) {
			this._kind = Kind.PercentileRanks;
			this._value = v;
			return new ContainerBuilder();
		}

		public ContainerBuilder percentileRanks(
				Function<PercentileRanksAggregation.Builder, ObjectBuilder<PercentileRanksAggregation>> fn) {
			return this.percentileRanks(fn.apply(new PercentileRanksAggregation.Builder()).build());
		}

		public ContainerBuilder percentiles(PercentilesAggregation v) {
			this._kind = Kind.Percentiles;
			this._value = v;
			return new ContainerBuilder();
		}

		public ContainerBuilder percentiles(
				Function<PercentilesAggregation.Builder, ObjectBuilder<PercentilesAggregation>> fn) {
			return this.percentiles(fn.apply(new PercentilesAggregation.Builder()).build());
		}

		public ContainerBuilder percentilesBucket(PercentilesBucketAggregation v) {
			this._kind = Kind.PercentilesBucket;
			this._value = v;
			return new ContainerBuilder();
		}

		public ContainerBuilder percentilesBucket(
				Function<PercentilesBucketAggregation.Builder, ObjectBuilder<PercentilesBucketAggregation>> fn) {
			return this.percentilesBucket(fn.apply(new PercentilesBucketAggregation.Builder()).build());
		}

		public ContainerBuilder range(RangeAggregation v) {
			this._kind = Kind.Range;
			this._value = v;
			return new ContainerBuilder();
		}

		public ContainerBuilder range(Function<RangeAggregation.Builder, ObjectBuilder<RangeAggregation>> fn) {
			return this.range(fn.apply(new RangeAggregation.Builder()).build());
		}

		public ContainerBuilder rareTerms(RareTermsAggregation v) {
			this._kind = Kind.RareTerms;
			this._value = v;
			return new ContainerBuilder();
		}

		public ContainerBuilder rareTerms(
				Function<RareTermsAggregation.Builder, ObjectBuilder<RareTermsAggregation>> fn) {
			return this.rareTerms(fn.apply(new RareTermsAggregation.Builder()).build());
		}

		public ContainerBuilder rate(RateAggregation v) {
			this._kind = Kind.Rate;
			this._value = v;
			return new ContainerBuilder();
		}

		public ContainerBuilder rate(Function<RateAggregation.Builder, ObjectBuilder<RateAggregation>> fn) {
			return this.rate(fn.apply(new RateAggregation.Builder()).build());
		}

		public ContainerBuilder reverseNested(ReverseNestedAggregation v) {
			this._kind = Kind.ReverseNested;
			this._value = v;
			return new ContainerBuilder();
		}

		public ContainerBuilder reverseNested(
				Function<ReverseNestedAggregation.Builder, ObjectBuilder<ReverseNestedAggregation>> fn) {
			return this.reverseNested(fn.apply(new ReverseNestedAggregation.Builder()).build());
		}

		public ContainerBuilder sampler(SamplerAggregation v) {
			this._kind = Kind.Sampler;
			this._value = v;
			return new ContainerBuilder();
		}

		public ContainerBuilder sampler(Function<SamplerAggregation.Builder, ObjectBuilder<SamplerAggregation>> fn) {
			return this.sampler(fn.apply(new SamplerAggregation.Builder()).build());
		}

		public ContainerBuilder scriptedMetric(ScriptedMetricAggregation v) {
			this._kind = Kind.ScriptedMetric;
			this._value = v;
			return new ContainerBuilder();
		}

		public ContainerBuilder scriptedMetric(
				Function<ScriptedMetricAggregation.Builder, ObjectBuilder<ScriptedMetricAggregation>> fn) {
			return this.scriptedMetric(fn.apply(new ScriptedMetricAggregation.Builder()).build());
		}

		public ContainerBuilder serialDiff(SerialDifferencingAggregation v) {
			this._kind = Kind.SerialDiff;
			this._value = v;
			return new ContainerBuilder();
		}

		public ContainerBuilder serialDiff(
				Function<SerialDifferencingAggregation.Builder, ObjectBuilder<SerialDifferencingAggregation>> fn) {
			return this.serialDiff(fn.apply(new SerialDifferencingAggregation.Builder()).build());
		}

		public ContainerBuilder significantTerms(SignificantTermsAggregation v) {
			this._kind = Kind.SignificantTerms;
			this._value = v;
			return new ContainerBuilder();
		}

		public ContainerBuilder significantTerms(
				Function<SignificantTermsAggregation.Builder, ObjectBuilder<SignificantTermsAggregation>> fn) {
			return this.significantTerms(fn.apply(new SignificantTermsAggregation.Builder()).build());
		}

		public ContainerBuilder significantText(SignificantTextAggregation v) {
			this._kind = Kind.SignificantText;
			this._value = v;
			return new ContainerBuilder();
		}

		public ContainerBuilder significantText(
				Function<SignificantTextAggregation.Builder, ObjectBuilder<SignificantTextAggregation>> fn) {
			return this.significantText(fn.apply(new SignificantTextAggregation.Builder()).build());
		}

		public ContainerBuilder stats(StatsAggregation v) {
			this._kind = Kind.Stats;
			this._value = v;
			return new ContainerBuilder();
		}

		public ContainerBuilder stats(Function<StatsAggregation.Builder, ObjectBuilder<StatsAggregation>> fn) {
			return this.stats(fn.apply(new StatsAggregation.Builder()).build());
		}

		public ContainerBuilder statsBucket(StatsBucketAggregation v) {
			this._kind = Kind.StatsBucket;
			this._value = v;
			return new ContainerBuilder();
		}

		public ContainerBuilder statsBucket(
				Function<StatsBucketAggregation.Builder, ObjectBuilder<StatsBucketAggregation>> fn) {
			return this.statsBucket(fn.apply(new StatsBucketAggregation.Builder()).build());
		}

		public ContainerBuilder stringStats(StringStatsAggregation v) {
			this._kind = Kind.StringStats;
			this._value = v;
			return new ContainerBuilder();
		}

		public ContainerBuilder stringStats(
				Function<StringStatsAggregation.Builder, ObjectBuilder<StringStatsAggregation>> fn) {
			return this.stringStats(fn.apply(new StringStatsAggregation.Builder()).build());
		}

		public ContainerBuilder sum(SumAggregation v) {
			this._kind = Kind.Sum;
			this._value = v;
			return new ContainerBuilder();
		}

		public ContainerBuilder sum(Function<SumAggregation.Builder, ObjectBuilder<SumAggregation>> fn) {
			return this.sum(fn.apply(new SumAggregation.Builder()).build());
		}

		public ContainerBuilder sumBucket(SumBucketAggregation v) {
			this._kind = Kind.SumBucket;
			this._value = v;
			return new ContainerBuilder();
		}

		public ContainerBuilder sumBucket(
				Function<SumBucketAggregation.Builder, ObjectBuilder<SumBucketAggregation>> fn) {
			return this.sumBucket(fn.apply(new SumBucketAggregation.Builder()).build());
		}

		public ContainerBuilder terms(TermsAggregation v) {
			this._kind = Kind.Terms;
			this._value = v;
			return new ContainerBuilder();
		}

		public ContainerBuilder terms(Function<TermsAggregation.Builder, ObjectBuilder<TermsAggregation>> fn) {
			return this.terms(fn.apply(new TermsAggregation.Builder()).build());
		}

		public ContainerBuilder topHits(TopHitsAggregation v) {
			this._kind = Kind.TopHits;
			this._value = v;
			return new ContainerBuilder();
		}

		public ContainerBuilder topHits(Function<TopHitsAggregation.Builder, ObjectBuilder<TopHitsAggregation>> fn) {
			return this.topHits(fn.apply(new TopHitsAggregation.Builder()).build());
		}

		public ContainerBuilder tTest(TTestAggregation v) {
			this._kind = Kind.TTest;
			this._value = v;
			return new ContainerBuilder();
		}

		public ContainerBuilder tTest(Function<TTestAggregation.Builder, ObjectBuilder<TTestAggregation>> fn) {
			return this.tTest(fn.apply(new TTestAggregation.Builder()).build());
		}

		public ContainerBuilder topMetrics(TopMetricsAggregation v) {
			this._kind = Kind.TopMetrics;
			this._value = v;
			return new ContainerBuilder();
		}

		public ContainerBuilder topMetrics(
				Function<TopMetricsAggregation.Builder, ObjectBuilder<TopMetricsAggregation>> fn) {
			return this.topMetrics(fn.apply(new TopMetricsAggregation.Builder()).build());
		}

		public ContainerBuilder valueCount(ValueCountAggregation v) {
			this._kind = Kind.ValueCount;
			this._value = v;
			return new ContainerBuilder();
		}

		public ContainerBuilder valueCount(
				Function<ValueCountAggregation.Builder, ObjectBuilder<ValueCountAggregation>> fn) {
			return this.valueCount(fn.apply(new ValueCountAggregation.Builder()).build());
		}

		public ContainerBuilder weightedAvg(WeightedAverageAggregation v) {
			this._kind = Kind.WeightedAvg;
			this._value = v;
			return new ContainerBuilder();
		}

		public ContainerBuilder weightedAvg(
				Function<WeightedAverageAggregation.Builder, ObjectBuilder<WeightedAverageAggregation>> fn) {
			return this.weightedAvg(fn.apply(new WeightedAverageAggregation.Builder()).build());
		}

		public ContainerBuilder variableWidthHistogram(VariableWidthHistogramAggregation v) {
			this._kind = Kind.VariableWidthHistogram;
			this._value = v;
			return new ContainerBuilder();
		}

		public ContainerBuilder variableWidthHistogram(
				Function<VariableWidthHistogramAggregation.Builder, ObjectBuilder<VariableWidthHistogramAggregation>> fn) {
			return this.variableWidthHistogram(fn.apply(new VariableWidthHistogramAggregation.Builder()).build());
		}

		protected Aggregation build() {
			_checkSingleUse();
			return new Aggregation(this);
		}

		public class ContainerBuilder implements ObjectBuilder<Aggregation> {

			/**
			 * Sub-aggregations for this aggregation. Only applies to bucket aggregations.
			 * <p>
			 * API name: {@code aggregations}
			 * <p>
			 * Adds all entries of <code>map</code> to <code>aggregations</code>.
			 */
			public final ContainerBuilder aggregations(Map<String, Aggregation> map) {
				Builder.this.aggregations = _mapPutAll(Builder.this.aggregations, map);
				return this;
			}

			/**
			 * Sub-aggregations for this aggregation. Only applies to bucket aggregations.
			 * <p>
			 * API name: {@code aggregations}
			 * <p>
			 * Adds an entry to <code>aggregations</code>.
			 */
			public final ContainerBuilder aggregations(String key, Aggregation value) {
				Builder.this.aggregations = _mapPut(Builder.this.aggregations, key, value);
				return this;
			}

			/**
			 * Sub-aggregations for this aggregation. Only applies to bucket aggregations.
			 * <p>
			 * API name: {@code aggregations}
			 * <p>
			 * Adds an entry to <code>aggregations</code> using a builder lambda.
			 */
			public final ContainerBuilder aggregations(String key,
					Function<Aggregation.Builder, ObjectBuilder<Aggregation>> fn) {
				return aggregations(key, fn.apply(new Aggregation.Builder()).build());
			}

			/**
			 * API name: {@code meta}
			 * <p>
			 * Adds all entries of <code>map</code> to <code>meta</code>.
			 */
			public final ContainerBuilder meta(Map<String, JsonData> map) {
				Builder.this.meta = _mapPutAll(Builder.this.meta, map);
				return this;
			}

			/**
			 * API name: {@code meta}
			 * <p>
			 * Adds an entry to <code>meta</code>.
			 */
			public final ContainerBuilder meta(String key, JsonData value) {
				Builder.this.meta = _mapPut(Builder.this.meta, key, value);
				return this;
			}

			public Aggregation build() {
				return Builder.this.build();
			}
		}
	}

	protected static void setupAggregationDeserializer(ObjectDeserializer<Builder> op) {

		op.add(Builder::aggregations, JsonpDeserializer.stringMapDeserializer(Aggregation._DESERIALIZER),
				"aggregations", "aggs");
		op.add(Builder::meta, JsonpDeserializer.stringMapDeserializer(JsonData._DESERIALIZER), "meta");
		op.add(Builder::adjacencyMatrix, AdjacencyMatrixAggregation._DESERIALIZER, "adjacency_matrix");
		op.add(Builder::autoDateHistogram, AutoDateHistogramAggregation._DESERIALIZER, "auto_date_histogram");
		op.add(Builder::avg, AverageAggregation._DESERIALIZER, "avg");
		op.add(Builder::avgBucket, AverageBucketAggregation._DESERIALIZER, "avg_bucket");
		op.add(Builder::boxplot, BoxplotAggregation._DESERIALIZER, "boxplot");
		op.add(Builder::bucketScript, BucketScriptAggregation._DESERIALIZER, "bucket_script");
		op.add(Builder::bucketSelector, BucketSelectorAggregation._DESERIALIZER, "bucket_selector");
		op.add(Builder::bucketSort, BucketSortAggregation._DESERIALIZER, "bucket_sort");
		op.add(Builder::cardinality, CardinalityAggregation._DESERIALIZER, "cardinality");
		op.add(Builder::children, ChildrenAggregation._DESERIALIZER, "children");
		op.add(Builder::composite, CompositeAggregation._DESERIALIZER, "composite");
		op.add(Builder::cumulativeCardinality, CumulativeCardinalityAggregation._DESERIALIZER,
				"cumulative_cardinality");
		op.add(Builder::cumulativeSum, CumulativeSumAggregation._DESERIALIZER, "cumulative_sum");
		op.add(Builder::dateHistogram, DateHistogramAggregation._DESERIALIZER, "date_histogram");
		op.add(Builder::dateRange, DateRangeAggregation._DESERIALIZER, "date_range");
		op.add(Builder::derivative, DerivativeAggregation._DESERIALIZER, "derivative");
		op.add(Builder::diversifiedSampler, DiversifiedSamplerAggregation._DESERIALIZER, "diversified_sampler");
		op.add(Builder::extendedStats, ExtendedStatsAggregation._DESERIALIZER, "extended_stats");
		op.add(Builder::extendedStatsBucket, ExtendedStatsBucketAggregation._DESERIALIZER, "extended_stats_bucket");
		op.add(Builder::filter, Query._DESERIALIZER, "filter");
		op.add(Builder::filters, FiltersAggregation._DESERIALIZER, "filters");
		op.add(Builder::geoBounds, GeoBoundsAggregation._DESERIALIZER, "geo_bounds");
		op.add(Builder::geoCentroid, GeoCentroidAggregation._DESERIALIZER, "geo_centroid");
		op.add(Builder::geoDistance, GeoDistanceAggregation._DESERIALIZER, "geo_distance");
		op.add(Builder::geohashGrid, GeoHashGridAggregation._DESERIALIZER, "geohash_grid");
		op.add(Builder::geoLine, GeoLineAggregation._DESERIALIZER, "geo_line");
		op.add(Builder::geotileGrid, GeoTileGridAggregation._DESERIALIZER, "geotile_grid");
		op.add(Builder::global, GlobalAggregation._DESERIALIZER, "global");
		op.add(Builder::histogram, HistogramAggregation._DESERIALIZER, "histogram");
		op.add(Builder::ipRange, IpRangeAggregation._DESERIALIZER, "ip_range");
		op.add(Builder::inference, InferenceAggregation._DESERIALIZER, "inference");
		op.add(Builder::matrixStats, MatrixStatsAggregation._DESERIALIZER, "matrix_stats");
		op.add(Builder::max, MaxAggregation._DESERIALIZER, "max");
		op.add(Builder::maxBucket, MaxBucketAggregation._DESERIALIZER, "max_bucket");
		op.add(Builder::medianAbsoluteDeviation, MedianAbsoluteDeviationAggregation._DESERIALIZER,
				"median_absolute_deviation");
		op.add(Builder::min, MinAggregation._DESERIALIZER, "min");
		op.add(Builder::minBucket, MinBucketAggregation._DESERIALIZER, "min_bucket");
		op.add(Builder::missing, MissingAggregation._DESERIALIZER, "missing");
		op.add(Builder::movingAvg, MovingAverageAggregation._DESERIALIZER, "moving_avg");
		op.add(Builder::movingPercentiles, MovingPercentilesAggregation._DESERIALIZER, "moving_percentiles");
		op.add(Builder::movingFn, MovingFunctionAggregation._DESERIALIZER, "moving_fn");
		op.add(Builder::multiTerms, MultiTermsAggregation._DESERIALIZER, "multi_terms");
		op.add(Builder::nested, NestedAggregation._DESERIALIZER, "nested");
		op.add(Builder::normalize, NormalizeAggregation._DESERIALIZER, "normalize");
		op.add(Builder::parent, ParentAggregation._DESERIALIZER, "parent");
		op.add(Builder::percentileRanks, PercentileRanksAggregation._DESERIALIZER, "percentile_ranks");
		op.add(Builder::percentiles, PercentilesAggregation._DESERIALIZER, "percentiles");
		op.add(Builder::percentilesBucket, PercentilesBucketAggregation._DESERIALIZER, "percentiles_bucket");
		op.add(Builder::range, RangeAggregation._DESERIALIZER, "range");
		op.add(Builder::rareTerms, RareTermsAggregation._DESERIALIZER, "rare_terms");
		op.add(Builder::rate, RateAggregation._DESERIALIZER, "rate");
		op.add(Builder::reverseNested, ReverseNestedAggregation._DESERIALIZER, "reverse_nested");
		op.add(Builder::sampler, SamplerAggregation._DESERIALIZER, "sampler");
		op.add(Builder::scriptedMetric, ScriptedMetricAggregation._DESERIALIZER, "scripted_metric");
		op.add(Builder::serialDiff, SerialDifferencingAggregation._DESERIALIZER, "serial_diff");
		op.add(Builder::significantTerms, SignificantTermsAggregation._DESERIALIZER, "significant_terms");
		op.add(Builder::significantText, SignificantTextAggregation._DESERIALIZER, "significant_text");
		op.add(Builder::stats, StatsAggregation._DESERIALIZER, "stats");
		op.add(Builder::statsBucket, StatsBucketAggregation._DESERIALIZER, "stats_bucket");
		op.add(Builder::stringStats, StringStatsAggregation._DESERIALIZER, "string_stats");
		op.add(Builder::sum, SumAggregation._DESERIALIZER, "sum");
		op.add(Builder::sumBucket, SumBucketAggregation._DESERIALIZER, "sum_bucket");
		op.add(Builder::terms, TermsAggregation._DESERIALIZER, "terms");
		op.add(Builder::topHits, TopHitsAggregation._DESERIALIZER, "top_hits");
		op.add(Builder::tTest, TTestAggregation._DESERIALIZER, "t_test");
		op.add(Builder::topMetrics, TopMetricsAggregation._DESERIALIZER, "top_metrics");
		op.add(Builder::valueCount, ValueCountAggregation._DESERIALIZER, "value_count");
		op.add(Builder::weightedAvg, WeightedAverageAggregation._DESERIALIZER, "weighted_avg");
		op.add(Builder::variableWidthHistogram, VariableWidthHistogramAggregation._DESERIALIZER,
				"variable_width_histogram");

	}

	public static final JsonpDeserializer<Aggregation> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			Aggregation::setupAggregationDeserializer, Builder::build);
}
