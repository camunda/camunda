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

/**
 * Builders for {@link Aggregation} variants.
 */
public class AggregationBuilders {
	private AggregationBuilders() {
	}

	/**
	 * Creates a builder for the {@link AdjacencyMatrixAggregation adjacency_matrix}
	 * {@code Aggregation} variant.
	 */
	public static AdjacencyMatrixAggregation.Builder adjacencyMatrix() {
		return new AdjacencyMatrixAggregation.Builder();
	}

	/**
	 * Creates a builder for the {@link AutoDateHistogramAggregation
	 * auto_date_histogram} {@code Aggregation} variant.
	 */
	public static AutoDateHistogramAggregation.Builder autoDateHistogram() {
		return new AutoDateHistogramAggregation.Builder();
	}

	/**
	 * Creates a builder for the {@link AverageAggregation avg} {@code Aggregation}
	 * variant.
	 */
	public static AverageAggregation.Builder avg() {
		return new AverageAggregation.Builder();
	}

	/**
	 * Creates a builder for the {@link AverageBucketAggregation avg_bucket}
	 * {@code Aggregation} variant.
	 */
	public static AverageBucketAggregation.Builder avgBucket() {
		return new AverageBucketAggregation.Builder();
	}

	/**
	 * Creates a builder for the {@link BoxplotAggregation boxplot}
	 * {@code Aggregation} variant.
	 */
	public static BoxplotAggregation.Builder boxplot() {
		return new BoxplotAggregation.Builder();
	}

	/**
	 * Creates a builder for the {@link BucketScriptAggregation bucket_script}
	 * {@code Aggregation} variant.
	 */
	public static BucketScriptAggregation.Builder bucketScript() {
		return new BucketScriptAggregation.Builder();
	}

	/**
	 * Creates a builder for the {@link BucketSelectorAggregation bucket_selector}
	 * {@code Aggregation} variant.
	 */
	public static BucketSelectorAggregation.Builder bucketSelector() {
		return new BucketSelectorAggregation.Builder();
	}

	/**
	 * Creates a builder for the {@link BucketSortAggregation bucket_sort}
	 * {@code Aggregation} variant.
	 */
	public static BucketSortAggregation.Builder bucketSort() {
		return new BucketSortAggregation.Builder();
	}

	/**
	 * Creates a builder for the {@link CardinalityAggregation cardinality}
	 * {@code Aggregation} variant.
	 */
	public static CardinalityAggregation.Builder cardinality() {
		return new CardinalityAggregation.Builder();
	}

	/**
	 * Creates a builder for the {@link ChildrenAggregation children}
	 * {@code Aggregation} variant.
	 */
	public static ChildrenAggregation.Builder children() {
		return new ChildrenAggregation.Builder();
	}

	/**
	 * Creates a builder for the {@link CompositeAggregation composite}
	 * {@code Aggregation} variant.
	 */
	public static CompositeAggregation.Builder composite() {
		return new CompositeAggregation.Builder();
	}

	/**
	 * Creates a builder for the {@link CumulativeCardinalityAggregation
	 * cumulative_cardinality} {@code Aggregation} variant.
	 */
	public static CumulativeCardinalityAggregation.Builder cumulativeCardinality() {
		return new CumulativeCardinalityAggregation.Builder();
	}

	/**
	 * Creates a builder for the {@link CumulativeSumAggregation cumulative_sum}
	 * {@code Aggregation} variant.
	 */
	public static CumulativeSumAggregation.Builder cumulativeSum() {
		return new CumulativeSumAggregation.Builder();
	}

	/**
	 * Creates a builder for the {@link DateHistogramAggregation date_histogram}
	 * {@code Aggregation} variant.
	 */
	public static DateHistogramAggregation.Builder dateHistogram() {
		return new DateHistogramAggregation.Builder();
	}

	/**
	 * Creates a builder for the {@link DateRangeAggregation date_range}
	 * {@code Aggregation} variant.
	 */
	public static DateRangeAggregation.Builder dateRange() {
		return new DateRangeAggregation.Builder();
	}

	/**
	 * Creates a builder for the {@link DerivativeAggregation derivative}
	 * {@code Aggregation} variant.
	 */
	public static DerivativeAggregation.Builder derivative() {
		return new DerivativeAggregation.Builder();
	}

	/**
	 * Creates a builder for the {@link DiversifiedSamplerAggregation
	 * diversified_sampler} {@code Aggregation} variant.
	 */
	public static DiversifiedSamplerAggregation.Builder diversifiedSampler() {
		return new DiversifiedSamplerAggregation.Builder();
	}

	/**
	 * Creates a builder for the {@link ExtendedStatsAggregation extended_stats}
	 * {@code Aggregation} variant.
	 */
	public static ExtendedStatsAggregation.Builder extendedStats() {
		return new ExtendedStatsAggregation.Builder();
	}

	/**
	 * Creates a builder for the {@link ExtendedStatsBucketAggregation
	 * extended_stats_bucket} {@code Aggregation} variant.
	 */
	public static ExtendedStatsBucketAggregation.Builder extendedStatsBucket() {
		return new ExtendedStatsBucketAggregation.Builder();
	}

	/**
	 * Creates a builder for the {@link Query filter} {@code Aggregation} variant.
	 */
	public static Query.Builder filter() {
		return new Query.Builder();
	}

	/**
	 * Creates a builder for the {@link FiltersAggregation filters}
	 * {@code Aggregation} variant.
	 */
	public static FiltersAggregation.Builder filters() {
		return new FiltersAggregation.Builder();
	}

	/**
	 * Creates a builder for the {@link GeoBoundsAggregation geo_bounds}
	 * {@code Aggregation} variant.
	 */
	public static GeoBoundsAggregation.Builder geoBounds() {
		return new GeoBoundsAggregation.Builder();
	}

	/**
	 * Creates a builder for the {@link GeoCentroidAggregation geo_centroid}
	 * {@code Aggregation} variant.
	 */
	public static GeoCentroidAggregation.Builder geoCentroid() {
		return new GeoCentroidAggregation.Builder();
	}

	/**
	 * Creates a builder for the {@link GeoDistanceAggregation geo_distance}
	 * {@code Aggregation} variant.
	 */
	public static GeoDistanceAggregation.Builder geoDistance() {
		return new GeoDistanceAggregation.Builder();
	}

	/**
	 * Creates a builder for the {@link GeoHashGridAggregation geohash_grid}
	 * {@code Aggregation} variant.
	 */
	public static GeoHashGridAggregation.Builder geohashGrid() {
		return new GeoHashGridAggregation.Builder();
	}

	/**
	 * Creates a builder for the {@link GeoLineAggregation geo_line}
	 * {@code Aggregation} variant.
	 */
	public static GeoLineAggregation.Builder geoLine() {
		return new GeoLineAggregation.Builder();
	}

	/**
	 * Creates a builder for the {@link GeoTileGridAggregation geotile_grid}
	 * {@code Aggregation} variant.
	 */
	public static GeoTileGridAggregation.Builder geotileGrid() {
		return new GeoTileGridAggregation.Builder();
	}

	/**
	 * Creates a builder for the {@link GlobalAggregation global}
	 * {@code Aggregation} variant.
	 */
	public static GlobalAggregation.Builder global() {
		return new GlobalAggregation.Builder();
	}

	/**
	 * Creates a builder for the {@link HistogramAggregation histogram}
	 * {@code Aggregation} variant.
	 */
	public static HistogramAggregation.Builder histogram() {
		return new HistogramAggregation.Builder();
	}

	/**
	 * Creates a builder for the {@link IpRangeAggregation ip_range}
	 * {@code Aggregation} variant.
	 */
	public static IpRangeAggregation.Builder ipRange() {
		return new IpRangeAggregation.Builder();
	}

	/**
	 * Creates a builder for the {@link InferenceAggregation inference}
	 * {@code Aggregation} variant.
	 */
	public static InferenceAggregation.Builder inference() {
		return new InferenceAggregation.Builder();
	}

	/**
	 * Creates a builder for the {@link MatrixStatsAggregation matrix_stats}
	 * {@code Aggregation} variant.
	 */
	public static MatrixStatsAggregation.Builder matrixStats() {
		return new MatrixStatsAggregation.Builder();
	}

	/**
	 * Creates a builder for the {@link MaxAggregation max} {@code Aggregation}
	 * variant.
	 */
	public static MaxAggregation.Builder max() {
		return new MaxAggregation.Builder();
	}

	/**
	 * Creates a builder for the {@link MaxBucketAggregation max_bucket}
	 * {@code Aggregation} variant.
	 */
	public static MaxBucketAggregation.Builder maxBucket() {
		return new MaxBucketAggregation.Builder();
	}

	/**
	 * Creates a builder for the {@link MedianAbsoluteDeviationAggregation
	 * median_absolute_deviation} {@code Aggregation} variant.
	 */
	public static MedianAbsoluteDeviationAggregation.Builder medianAbsoluteDeviation() {
		return new MedianAbsoluteDeviationAggregation.Builder();
	}

	/**
	 * Creates a builder for the {@link MinAggregation min} {@code Aggregation}
	 * variant.
	 */
	public static MinAggregation.Builder min() {
		return new MinAggregation.Builder();
	}

	/**
	 * Creates a builder for the {@link MinBucketAggregation min_bucket}
	 * {@code Aggregation} variant.
	 */
	public static MinBucketAggregation.Builder minBucket() {
		return new MinBucketAggregation.Builder();
	}

	/**
	 * Creates a builder for the {@link MissingAggregation missing}
	 * {@code Aggregation} variant.
	 */
	public static MissingAggregation.Builder missing() {
		return new MissingAggregation.Builder();
	}

	/**
	 * Creates a builder for the {@link MovingAverageAggregation moving_avg}
	 * {@code Aggregation} variant.
	 */
	public static MovingAverageAggregation.Builder movingAvg() {
		return new MovingAverageAggregation.Builder();
	}

	/**
	 * Creates a builder for the {@link MovingPercentilesAggregation
	 * moving_percentiles} {@code Aggregation} variant.
	 */
	public static MovingPercentilesAggregation.Builder movingPercentiles() {
		return new MovingPercentilesAggregation.Builder();
	}

	/**
	 * Creates a builder for the {@link MovingFunctionAggregation moving_fn}
	 * {@code Aggregation} variant.
	 */
	public static MovingFunctionAggregation.Builder movingFn() {
		return new MovingFunctionAggregation.Builder();
	}

	/**
	 * Creates a builder for the {@link MultiTermsAggregation multi_terms}
	 * {@code Aggregation} variant.
	 */
	public static MultiTermsAggregation.Builder multiTerms() {
		return new MultiTermsAggregation.Builder();
	}

	/**
	 * Creates a builder for the {@link NestedAggregation nested}
	 * {@code Aggregation} variant.
	 */
	public static NestedAggregation.Builder nested() {
		return new NestedAggregation.Builder();
	}

	/**
	 * Creates a builder for the {@link NormalizeAggregation normalize}
	 * {@code Aggregation} variant.
	 */
	public static NormalizeAggregation.Builder normalize() {
		return new NormalizeAggregation.Builder();
	}

	/**
	 * Creates a builder for the {@link ParentAggregation parent}
	 * {@code Aggregation} variant.
	 */
	public static ParentAggregation.Builder parent() {
		return new ParentAggregation.Builder();
	}

	/**
	 * Creates a builder for the {@link PercentileRanksAggregation percentile_ranks}
	 * {@code Aggregation} variant.
	 */
	public static PercentileRanksAggregation.Builder percentileRanks() {
		return new PercentileRanksAggregation.Builder();
	}

	/**
	 * Creates a builder for the {@link PercentilesAggregation percentiles}
	 * {@code Aggregation} variant.
	 */
	public static PercentilesAggregation.Builder percentiles() {
		return new PercentilesAggregation.Builder();
	}

	/**
	 * Creates a builder for the {@link PercentilesBucketAggregation
	 * percentiles_bucket} {@code Aggregation} variant.
	 */
	public static PercentilesBucketAggregation.Builder percentilesBucket() {
		return new PercentilesBucketAggregation.Builder();
	}

	/**
	 * Creates a builder for the {@link RangeAggregation range} {@code Aggregation}
	 * variant.
	 */
	public static RangeAggregation.Builder range() {
		return new RangeAggregation.Builder();
	}

	/**
	 * Creates a builder for the {@link RareTermsAggregation rare_terms}
	 * {@code Aggregation} variant.
	 */
	public static RareTermsAggregation.Builder rareTerms() {
		return new RareTermsAggregation.Builder();
	}

	/**
	 * Creates a builder for the {@link RateAggregation rate} {@code Aggregation}
	 * variant.
	 */
	public static RateAggregation.Builder rate() {
		return new RateAggregation.Builder();
	}

	/**
	 * Creates a builder for the {@link ReverseNestedAggregation reverse_nested}
	 * {@code Aggregation} variant.
	 */
	public static ReverseNestedAggregation.Builder reverseNested() {
		return new ReverseNestedAggregation.Builder();
	}

	/**
	 * Creates a builder for the {@link SamplerAggregation sampler}
	 * {@code Aggregation} variant.
	 */
	public static SamplerAggregation.Builder sampler() {
		return new SamplerAggregation.Builder();
	}

	/**
	 * Creates a builder for the {@link ScriptedMetricAggregation scripted_metric}
	 * {@code Aggregation} variant.
	 */
	public static ScriptedMetricAggregation.Builder scriptedMetric() {
		return new ScriptedMetricAggregation.Builder();
	}

	/**
	 * Creates a builder for the {@link SerialDifferencingAggregation serial_diff}
	 * {@code Aggregation} variant.
	 */
	public static SerialDifferencingAggregation.Builder serialDiff() {
		return new SerialDifferencingAggregation.Builder();
	}

	/**
	 * Creates a builder for the {@link SignificantTermsAggregation
	 * significant_terms} {@code Aggregation} variant.
	 */
	public static SignificantTermsAggregation.Builder significantTerms() {
		return new SignificantTermsAggregation.Builder();
	}

	/**
	 * Creates a builder for the {@link SignificantTextAggregation significant_text}
	 * {@code Aggregation} variant.
	 */
	public static SignificantTextAggregation.Builder significantText() {
		return new SignificantTextAggregation.Builder();
	}

	/**
	 * Creates a builder for the {@link StatsAggregation stats} {@code Aggregation}
	 * variant.
	 */
	public static StatsAggregation.Builder stats() {
		return new StatsAggregation.Builder();
	}

	/**
	 * Creates a builder for the {@link StatsBucketAggregation stats_bucket}
	 * {@code Aggregation} variant.
	 */
	public static StatsBucketAggregation.Builder statsBucket() {
		return new StatsBucketAggregation.Builder();
	}

	/**
	 * Creates a builder for the {@link StringStatsAggregation string_stats}
	 * {@code Aggregation} variant.
	 */
	public static StringStatsAggregation.Builder stringStats() {
		return new StringStatsAggregation.Builder();
	}

	/**
	 * Creates a builder for the {@link SumAggregation sum} {@code Aggregation}
	 * variant.
	 */
	public static SumAggregation.Builder sum() {
		return new SumAggregation.Builder();
	}

	/**
	 * Creates a builder for the {@link SumBucketAggregation sum_bucket}
	 * {@code Aggregation} variant.
	 */
	public static SumBucketAggregation.Builder sumBucket() {
		return new SumBucketAggregation.Builder();
	}

	/**
	 * Creates a builder for the {@link TermsAggregation terms} {@code Aggregation}
	 * variant.
	 */
	public static TermsAggregation.Builder terms() {
		return new TermsAggregation.Builder();
	}

	/**
	 * Creates a builder for the {@link TopHitsAggregation top_hits}
	 * {@code Aggregation} variant.
	 */
	public static TopHitsAggregation.Builder topHits() {
		return new TopHitsAggregation.Builder();
	}

	/**
	 * Creates a builder for the {@link TTestAggregation t_test} {@code Aggregation}
	 * variant.
	 */
	public static TTestAggregation.Builder tTest() {
		return new TTestAggregation.Builder();
	}

	/**
	 * Creates a builder for the {@link TopMetricsAggregation top_metrics}
	 * {@code Aggregation} variant.
	 */
	public static TopMetricsAggregation.Builder topMetrics() {
		return new TopMetricsAggregation.Builder();
	}

	/**
	 * Creates a builder for the {@link ValueCountAggregation value_count}
	 * {@code Aggregation} variant.
	 */
	public static ValueCountAggregation.Builder valueCount() {
		return new ValueCountAggregation.Builder();
	}

	/**
	 * Creates a builder for the {@link WeightedAverageAggregation weighted_avg}
	 * {@code Aggregation} variant.
	 */
	public static WeightedAverageAggregation.Builder weightedAvg() {
		return new WeightedAverageAggregation.Builder();
	}

	/**
	 * Creates a builder for the {@link VariableWidthHistogramAggregation
	 * variable_width_histogram} {@code Aggregation} variant.
	 */
	public static VariableWidthHistogramAggregation.Builder variableWidthHistogram() {
		return new VariableWidthHistogramAggregation.Builder();
	}

}
