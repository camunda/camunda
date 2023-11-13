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

/**
 * Builders for {@link Aggregate} variants.
 */
public class AggregateBuilders {
	private AggregateBuilders() {
	}

	/**
	 * Creates a builder for the {@link AdjacencyMatrixAggregate adjacency_matrix}
	 * {@code Aggregate} variant.
	 */
	public static AdjacencyMatrixAggregate.Builder adjacencyMatrix() {
		return new AdjacencyMatrixAggregate.Builder();
	}

	/**
	 * Creates a builder for the {@link AutoDateHistogramAggregate
	 * auto_date_histogram} {@code Aggregate} variant.
	 */
	public static AutoDateHistogramAggregate.Builder autoDateHistogram() {
		return new AutoDateHistogramAggregate.Builder();
	}

	/**
	 * Creates a builder for the {@link AvgAggregate avg} {@code Aggregate} variant.
	 */
	public static AvgAggregate.Builder avg() {
		return new AvgAggregate.Builder();
	}

	/**
	 * Creates a builder for the {@link BoxPlotAggregate box_plot} {@code Aggregate}
	 * variant.
	 */
	public static BoxPlotAggregate.Builder boxPlot() {
		return new BoxPlotAggregate.Builder();
	}

	/**
	 * Creates a builder for the {@link BucketMetricValueAggregate
	 * bucket_metric_value} {@code Aggregate} variant.
	 */
	public static BucketMetricValueAggregate.Builder bucketMetricValue() {
		return new BucketMetricValueAggregate.Builder();
	}

	/**
	 * Creates a builder for the {@link CardinalityAggregate cardinality}
	 * {@code Aggregate} variant.
	 */
	public static CardinalityAggregate.Builder cardinality() {
		return new CardinalityAggregate.Builder();
	}

	/**
	 * Creates a builder for the {@link ChildrenAggregate children}
	 * {@code Aggregate} variant.
	 */
	public static ChildrenAggregate.Builder children() {
		return new ChildrenAggregate.Builder();
	}

	/**
	 * Creates a builder for the {@link CompositeAggregate composite}
	 * {@code Aggregate} variant.
	 */
	public static CompositeAggregate.Builder composite() {
		return new CompositeAggregate.Builder();
	}

	/**
	 * Creates a builder for the {@link CumulativeCardinalityAggregate
	 * simple_long_value} {@code Aggregate} variant.
	 */
	public static CumulativeCardinalityAggregate.Builder simpleLongValue() {
		return new CumulativeCardinalityAggregate.Builder();
	}

	/**
	 * Creates a builder for the {@link DateHistogramAggregate date_histogram}
	 * {@code Aggregate} variant.
	 */
	public static DateHistogramAggregate.Builder dateHistogram() {
		return new DateHistogramAggregate.Builder();
	}

	/**
	 * Creates a builder for the {@link DateRangeAggregate date_range}
	 * {@code Aggregate} variant.
	 */
	public static DateRangeAggregate.Builder dateRange() {
		return new DateRangeAggregate.Builder();
	}

	/**
	 * Creates a builder for the {@link DerivativeAggregate derivative}
	 * {@code Aggregate} variant.
	 */
	public static DerivativeAggregate.Builder derivative() {
		return new DerivativeAggregate.Builder();
	}

	/**
	 * Creates a builder for the {@link DoubleTermsAggregate dterms}
	 * {@code Aggregate} variant.
	 */
	public static DoubleTermsAggregate.Builder dterms() {
		return new DoubleTermsAggregate.Builder();
	}

	/**
	 * Creates a builder for the {@link ExtendedStatsAggregate extended_stats}
	 * {@code Aggregate} variant.
	 */
	public static ExtendedStatsAggregate.Builder extendedStats() {
		return new ExtendedStatsAggregate.Builder();
	}

	/**
	 * Creates a builder for the {@link ExtendedStatsBucketAggregate
	 * extended_stats_bucket} {@code Aggregate} variant.
	 */
	public static ExtendedStatsBucketAggregate.Builder extendedStatsBucket() {
		return new ExtendedStatsBucketAggregate.Builder();
	}

	/**
	 * Creates a builder for the {@link FilterAggregate filter} {@code Aggregate}
	 * variant.
	 */
	public static FilterAggregate.Builder filter() {
		return new FilterAggregate.Builder();
	}

	/**
	 * Creates a builder for the {@link FiltersAggregate filters} {@code Aggregate}
	 * variant.
	 */
	public static FiltersAggregate.Builder filters() {
		return new FiltersAggregate.Builder();
	}

	/**
	 * Creates a builder for the {@link GeoBoundsAggregate geo_bounds}
	 * {@code Aggregate} variant.
	 */
	public static GeoBoundsAggregate.Builder geoBounds() {
		return new GeoBoundsAggregate.Builder();
	}

	/**
	 * Creates a builder for the {@link GeoCentroidAggregate geo_centroid}
	 * {@code Aggregate} variant.
	 */
	public static GeoCentroidAggregate.Builder geoCentroid() {
		return new GeoCentroidAggregate.Builder();
	}

	/**
	 * Creates a builder for the {@link GeoDistanceAggregate geo_distance}
	 * {@code Aggregate} variant.
	 */
	public static GeoDistanceAggregate.Builder geoDistance() {
		return new GeoDistanceAggregate.Builder();
	}

	/**
	 * Creates a builder for the {@link GeoHashGridAggregate geohash_grid}
	 * {@code Aggregate} variant.
	 */
	public static GeoHashGridAggregate.Builder geohashGrid() {
		return new GeoHashGridAggregate.Builder();
	}

	/**
	 * Creates a builder for the {@link GeoLineAggregate geo_line} {@code Aggregate}
	 * variant.
	 */
	public static GeoLineAggregate.Builder geoLine() {
		return new GeoLineAggregate.Builder();
	}

	/**
	 * Creates a builder for the {@link GeoTileGridAggregate geotile_grid}
	 * {@code Aggregate} variant.
	 */
	public static GeoTileGridAggregate.Builder geotileGrid() {
		return new GeoTileGridAggregate.Builder();
	}

	/**
	 * Creates a builder for the {@link GlobalAggregate global} {@code Aggregate}
	 * variant.
	 */
	public static GlobalAggregate.Builder global() {
		return new GlobalAggregate.Builder();
	}

	/**
	 * Creates a builder for the {@link HdrPercentileRanksAggregate
	 * hdr_percentile_ranks} {@code Aggregate} variant.
	 */
	public static HdrPercentileRanksAggregate.Builder hdrPercentileRanks() {
		return new HdrPercentileRanksAggregate.Builder();
	}

	/**
	 * Creates a builder for the {@link HdrPercentilesAggregate hdr_percentiles}
	 * {@code Aggregate} variant.
	 */
	public static HdrPercentilesAggregate.Builder hdrPercentiles() {
		return new HdrPercentilesAggregate.Builder();
	}

	/**
	 * Creates a builder for the {@link HistogramAggregate histogram}
	 * {@code Aggregate} variant.
	 */
	public static HistogramAggregate.Builder histogram() {
		return new HistogramAggregate.Builder();
	}

	/**
	 * Creates a builder for the {@link InferenceAggregate inference}
	 * {@code Aggregate} variant.
	 */
	public static InferenceAggregate.Builder inference() {
		return new InferenceAggregate.Builder();
	}

	/**
	 * Creates a builder for the {@link IpRangeAggregate ip_range} {@code Aggregate}
	 * variant.
	 */
	public static IpRangeAggregate.Builder ipRange() {
		return new IpRangeAggregate.Builder();
	}

	/**
	 * Creates a builder for the {@link LongRareTermsAggregate lrareterms}
	 * {@code Aggregate} variant.
	 */
	public static LongRareTermsAggregate.Builder lrareterms() {
		return new LongRareTermsAggregate.Builder();
	}

	/**
	 * Creates a builder for the {@link LongTermsAggregate lterms} {@code Aggregate}
	 * variant.
	 */
	public static LongTermsAggregate.Builder lterms() {
		return new LongTermsAggregate.Builder();
	}

	/**
	 * Creates a builder for the {@link MatrixStatsAggregate matrix_stats}
	 * {@code Aggregate} variant.
	 */
	public static MatrixStatsAggregate.Builder matrixStats() {
		return new MatrixStatsAggregate.Builder();
	}

	/**
	 * Creates a builder for the {@link MaxAggregate max} {@code Aggregate} variant.
	 */
	public static MaxAggregate.Builder max() {
		return new MaxAggregate.Builder();
	}

	/**
	 * Creates a builder for the {@link MedianAbsoluteDeviationAggregate
	 * median_absolute_deviation} {@code Aggregate} variant.
	 */
	public static MedianAbsoluteDeviationAggregate.Builder medianAbsoluteDeviation() {
		return new MedianAbsoluteDeviationAggregate.Builder();
	}

	/**
	 * Creates a builder for the {@link MinAggregate min} {@code Aggregate} variant.
	 */
	public static MinAggregate.Builder min() {
		return new MinAggregate.Builder();
	}

	/**
	 * Creates a builder for the {@link MissingAggregate missing} {@code Aggregate}
	 * variant.
	 */
	public static MissingAggregate.Builder missing() {
		return new MissingAggregate.Builder();
	}

	/**
	 * Creates a builder for the {@link MultiTermsAggregate multi_terms}
	 * {@code Aggregate} variant.
	 */
	public static MultiTermsAggregate.Builder multiTerms() {
		return new MultiTermsAggregate.Builder();
	}

	/**
	 * Creates a builder for the {@link NestedAggregate nested} {@code Aggregate}
	 * variant.
	 */
	public static NestedAggregate.Builder nested() {
		return new NestedAggregate.Builder();
	}

	/**
	 * Creates a builder for the {@link PercentilesBucketAggregate
	 * percentiles_bucket} {@code Aggregate} variant.
	 */
	public static PercentilesBucketAggregate.Builder percentilesBucket() {
		return new PercentilesBucketAggregate.Builder();
	}

	/**
	 * Creates a builder for the {@link RangeAggregate range} {@code Aggregate}
	 * variant.
	 */
	public static RangeAggregate.Builder range() {
		return new RangeAggregate.Builder();
	}

	/**
	 * Creates a builder for the {@link RateAggregate rate} {@code Aggregate}
	 * variant.
	 */
	public static RateAggregate.Builder rate() {
		return new RateAggregate.Builder();
	}

	/**
	 * Creates a builder for the {@link ReverseNestedAggregate reverse_nested}
	 * {@code Aggregate} variant.
	 */
	public static ReverseNestedAggregate.Builder reverseNested() {
		return new ReverseNestedAggregate.Builder();
	}

	/**
	 * Creates a builder for the {@link SamplerAggregate sampler} {@code Aggregate}
	 * variant.
	 */
	public static SamplerAggregate.Builder sampler() {
		return new SamplerAggregate.Builder();
	}

	/**
	 * Creates a builder for the {@link ScriptedMetricAggregate scripted_metric}
	 * {@code Aggregate} variant.
	 */
	public static ScriptedMetricAggregate.Builder scriptedMetric() {
		return new ScriptedMetricAggregate.Builder();
	}

	/**
	 * Creates a builder for the {@link SignificantLongTermsAggregate siglterms}
	 * {@code Aggregate} variant.
	 */
	public static SignificantLongTermsAggregate.Builder siglterms() {
		return new SignificantLongTermsAggregate.Builder();
	}

	/**
	 * Creates a builder for the {@link SignificantStringTermsAggregate sigsterms}
	 * {@code Aggregate} variant.
	 */
	public static SignificantStringTermsAggregate.Builder sigsterms() {
		return new SignificantStringTermsAggregate.Builder();
	}

	/**
	 * Creates a builder for the {@link SimpleValueAggregate simple_value}
	 * {@code Aggregate} variant.
	 */
	public static SimpleValueAggregate.Builder simpleValue() {
		return new SimpleValueAggregate.Builder();
	}

	/**
	 * Creates a builder for the {@link StatsAggregate stats} {@code Aggregate}
	 * variant.
	 */
	public static StatsAggregate.Builder stats() {
		return new StatsAggregate.Builder();
	}

	/**
	 * Creates a builder for the {@link StatsBucketAggregate stats_bucket}
	 * {@code Aggregate} variant.
	 */
	public static StatsBucketAggregate.Builder statsBucket() {
		return new StatsBucketAggregate.Builder();
	}

	/**
	 * Creates a builder for the {@link StringRareTermsAggregate srareterms}
	 * {@code Aggregate} variant.
	 */
	public static StringRareTermsAggregate.Builder srareterms() {
		return new StringRareTermsAggregate.Builder();
	}

	/**
	 * Creates a builder for the {@link StringStatsAggregate string_stats}
	 * {@code Aggregate} variant.
	 */
	public static StringStatsAggregate.Builder stringStats() {
		return new StringStatsAggregate.Builder();
	}

	/**
	 * Creates a builder for the {@link StringTermsAggregate sterms}
	 * {@code Aggregate} variant.
	 */
	public static StringTermsAggregate.Builder sterms() {
		return new StringTermsAggregate.Builder();
	}

	/**
	 * Creates a builder for the {@link SumAggregate sum} {@code Aggregate} variant.
	 */
	public static SumAggregate.Builder sum() {
		return new SumAggregate.Builder();
	}

	/**
	 * Creates a builder for the {@link TDigestPercentileRanksAggregate
	 * tdigest_percentile_ranks} {@code Aggregate} variant.
	 */
	public static TDigestPercentileRanksAggregate.Builder tdigestPercentileRanks() {
		return new TDigestPercentileRanksAggregate.Builder();
	}

	/**
	 * Creates a builder for the {@link TDigestPercentilesAggregate
	 * tdigest_percentiles} {@code Aggregate} variant.
	 */
	public static TDigestPercentilesAggregate.Builder tdigestPercentiles() {
		return new TDigestPercentilesAggregate.Builder();
	}

	/**
	 * Creates a builder for the {@link TTestAggregate t_test} {@code Aggregate}
	 * variant.
	 */
	public static TTestAggregate.Builder tTest() {
		return new TTestAggregate.Builder();
	}

	/**
	 * Creates a builder for the {@link TopHitsAggregate top_hits} {@code Aggregate}
	 * variant.
	 */
	public static TopHitsAggregate.Builder topHits() {
		return new TopHitsAggregate.Builder();
	}

	/**
	 * Creates a builder for the {@link TopMetricsAggregate top_metrics}
	 * {@code Aggregate} variant.
	 */
	public static TopMetricsAggregate.Builder topMetrics() {
		return new TopMetricsAggregate.Builder();
	}

	/**
	 * Creates a builder for the {@link UnmappedRareTermsAggregate umrareterms}
	 * {@code Aggregate} variant.
	 */
	public static UnmappedRareTermsAggregate.Builder umrareterms() {
		return new UnmappedRareTermsAggregate.Builder();
	}

	/**
	 * Creates a builder for the {@link UnmappedSignificantTermsAggregate
	 * umsigterms} {@code Aggregate} variant.
	 */
	public static UnmappedSignificantTermsAggregate.Builder umsigterms() {
		return new UnmappedSignificantTermsAggregate.Builder();
	}

	/**
	 * Creates a builder for the {@link UnmappedTermsAggregate umterms}
	 * {@code Aggregate} variant.
	 */
	public static UnmappedTermsAggregate.Builder umterms() {
		return new UnmappedTermsAggregate.Builder();
	}

	/**
	 * Creates a builder for the {@link ValueCountAggregate value_count}
	 * {@code Aggregate} variant.
	 */
	public static ValueCountAggregate.Builder valueCount() {
		return new ValueCountAggregate.Builder();
	}

	/**
	 * Creates a builder for the {@link VariableWidthHistogramAggregate
	 * variable_width_histogram} {@code Aggregate} variant.
	 */
	public static VariableWidthHistogramAggregate.Builder variableWidthHistogram() {
		return new VariableWidthHistogramAggregate.Builder();
	}

	/**
	 * Creates a builder for the {@link WeightedAvgAggregate weighted_avg}
	 * {@code Aggregate} variant.
	 */
	public static WeightedAvgAggregate.Builder weightedAvg() {
		return new WeightedAvgAggregate.Builder();
	}

}
