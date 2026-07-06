/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.meter;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.metric.ExecutionTimeAccumulator;
import io.camunda.analytics.metric.ExecutionTimeResult;
import io.camunda.analytics.metric.RatioAccumulator;
import io.camunda.analytics.metric.RatioResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The pushdown contract declared on the {@link MeterCatalog}: which types push, and round-trips.
 */
final class MeterCatalogPushdownTest {

  private final MeterCatalog catalog = MeterCatalog.withDefaults();

  @Test
  void shouldExposePushdownForWhollyAdditiveTypes() {
    // given / when / then — the additive counters/level and the fully-additive execution-time and
    // ratio meters decompose into numeric columns
    assertThat(bind(MeterCatalog.COUNT, null).pushdown()).isPresent();
    assertThat(bind(MeterCatalog.SUM, "durationMs").pushdown()).isPresent();
    assertThat(bind(MeterCatalog.LEVEL, "delta").pushdown()).isPresent();
    assertThat(bind(MeterCatalog.EXECUTION_TIME, "durationMs").pushdown()).isPresent();
    assertThat(ratio().pushdown()).isPresent();
  }

  @Test
  void shouldNotExposePushdownForSketchesOrSketchBundlingSummaries() {
    // given / when / then — sketches (and any summary that bundles a KLL sketch, plus the opaque
    // histogram blob) stay app-merged: no pushdown
    assertThat(bind(MeterCatalog.PERCENTILE, "durationMs").pushdown()).isEmpty();
    assertThat(bind(MeterCatalog.DISTINCT, "bpmnProcessId").pushdown()).isEmpty();
    assertThat(bind(MeterCatalog.TOP_K, "bpmnProcessId").pushdown()).isEmpty();
    assertThat(bind(MeterCatalog.EXECUTION_TIME_SUMMARY, "durationMs").pushdown()).isEmpty();
    assertThat(bind(MeterCatalog.LIFECYCLE_SUMMARY, "durationMs").pushdown()).isEmpty();
    assertThat(histogram().pushdown()).isEmpty();
  }

  @Test
  void shouldDescribeCountAsOneSummedColumn() {
    // given the count meter's spec
    final PushdownSpec<?, ?> spec = bind(MeterCatalog.COUNT, null).pushdown().orElseThrow();

    // then it is a single SUM column with the empty (own-name) suffix
    assertThat(spec.columns())
        .singleElement()
        .satisfies(
            column -> {
              assertThat(column.suffix()).isEmpty();
              assertThat(column.agg()).isEqualTo(Agg.SUM);
            });
  }

  @Test
  void shouldRoundTripCountThroughAStoreAggregate() {
    // given two partial counts; when decomposed, SUMmed, and recomposed
    // then it matches the app-merge + getResult (5)
    assertRoundTrips(bind(MeterCatalog.COUNT, null), 3L, 2L);
  }

  @Test
  void shouldRoundTripSumThroughAStoreAggregate() {
    assertRoundTrips(bind(MeterCatalog.SUM, "durationMs"), 100L, 50L);
  }

  @Test
  void shouldRoundTripRatioThroughAStoreAggregate() {
    // given matched/total partials; SUM/SUM then recompose equals the merged RatioResult
    assertRoundTrips(ratio(), new RatioAccumulator(3L, 4L), new RatioAccumulator(1L, 2L));

    // and the recomposed ratio itself is exact (4 of 6)
    @SuppressWarnings("unchecked")
    final PushdownSpec<Object, Object> spec =
        (PushdownSpec<Object, Object>) ratio().pushdown().orElseThrow();
    final RatioResult result = (RatioResult) spec.recompose().apply(List.of(4L, 6L));
    assertThat(result.matched()).isEqualTo(4L);
    assertThat(result.total()).isEqualTo(6L);
    assertThat(result.ratio()).isEqualTo(4.0 / 6.0);
  }

  @Test
  void shouldRoundTripExecutionTimeThroughAStoreAggregate() {
    // given count/total (SUM) and min/max (MIN/MAX) partials
    assertRoundTrips(
        bind(MeterCatalog.EXECUTION_TIME, "durationMs"),
        new ExecutionTimeAccumulator(2L, 300L, 100L, 200L),
        new ExecutionTimeAccumulator(3L, 600L, 50L, 400L));

    // and the recomposed result derives the average and keeps min/max exact
    @SuppressWarnings("unchecked")
    final PushdownSpec<Object, Object> spec =
        (PushdownSpec<Object, Object>)
            bind(MeterCatalog.EXECUTION_TIME, "durationMs").pushdown().orElseThrow();
    final ExecutionTimeResult result =
        (ExecutionTimeResult) spec.recompose().apply(List.of(5L, 900L, 50L, 400L));
    assertThat(result.count()).isEqualTo(5L);
    assertThat(result.averageMs()).isEqualTo(180.0);
    assertThat(result.minMs()).isEqualTo(50L);
    assertThat(result.maxMs()).isEqualTo(400L);
  }

  @Test
  void shouldRecomposeEmptyExecutionTimeRollupToZero() {
    @SuppressWarnings("unchecked")
    final PushdownSpec<Object, Object> spec =
        (PushdownSpec<Object, Object>)
            bind(MeterCatalog.EXECUTION_TIME, "durationMs").pushdown().orElseThrow();
    // a group with no matched rows aggregates to nulls / zero count -> all-zero result
    assertThat(spec.recompose().apply(java.util.Arrays.asList(0L, null, null, null)))
        .isEqualTo(new ExecutionTimeResult(0L, 0.0, 0L, 0L));
  }

  private BoundMeter<?, ?> bind(final String type, final String measureField) {
    return catalog.bind(new Meter("m", type, measureField, Map.of()));
  }

  private BoundMeter<?, ?> ratio() {
    return catalog.bind(
        new Meter(
            "r", MeterCatalog.RATIO, "durationMs", Map.of("op", "LE", "threshold", "300000")));
  }

  private BoundMeter<?, ?> histogram() {
    return catalog.bind(
        new Meter("h", MeterCatalog.HISTOGRAM, "durationMs", Map.of("thresholds", "100,200,300")));
  }

  /** Decompose both partials, simulate the store's column aggregate, recompose, and compare. */
  @SuppressWarnings("unchecked")
  private static void assertRoundTrips(
      final BoundMeter<?, ?> boundRaw, final Object a, final Object b) {
    final BoundMeter<Object, Object> bound = (BoundMeter<Object, Object>) boundRaw;
    final PushdownSpec<Object, Object> spec =
        (PushdownSpec<Object, Object>) bound.pushdown().orElseThrow();
    final List<Object> aggregated =
        simulate(spec.columns(), spec.decompose().apply(a), spec.decompose().apply(b));
    final Object pushed = spec.recompose().apply(aggregated);
    final Object appMerged = bound.aggregate().getResult(bound.aggregate().merge(a, b));
    assertThat(pushed).isEqualTo(appMerged);
  }

  /** Applies each column's {@link Agg} to the two decomposed rows — a stand-in for the SQL agg. */
  private static List<Object> simulate(
      final List<PushdownColumn> columns, final List<Object> a, final List<Object> b) {
    final List<Object> out = new ArrayList<>(columns.size());
    for (int i = 0; i < columns.size(); i++) {
      final long left = ((Number) a.get(i)).longValue();
      final long right = ((Number) b.get(i)).longValue();
      out.add(
          switch (columns.get(i).agg()) {
            case SUM -> left + right;
            case MIN -> Math.min(left, right);
            case MAX -> Math.max(left, right);
          });
    }
    return out;
  }
}
