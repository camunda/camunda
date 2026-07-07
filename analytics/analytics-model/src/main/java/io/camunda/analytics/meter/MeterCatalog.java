/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.meter;

import io.camunda.analytics.dimension.DimensionType;
import io.camunda.analytics.metric.ExecutionTimeAccumulator;
import io.camunda.analytics.metric.ExecutionTimeAccumulatorValue;
import io.camunda.analytics.metric.ExecutionTimeAggregateFunction;
import io.camunda.analytics.metric.ExecutionTimeResult;
import io.camunda.analytics.metric.ExecutionTimeSummaryAggregateFunction;
import io.camunda.analytics.metric.ExecutionTimeSummaryValue;
import io.camunda.analytics.metric.HistogramAggregateFunction;
import io.camunda.analytics.metric.HistogramValue;
import io.camunda.analytics.metric.LifecycleSummaryAggregateFunction;
import io.camunda.analytics.metric.LifecycleSummaryValue;
import io.camunda.analytics.metric.RatioAccumulator;
import io.camunda.analytics.metric.RatioAccumulatorValue;
import io.camunda.analytics.metric.RatioAggregateFunction;
import io.camunda.analytics.metric.RatioResult;
import io.camunda.analytics.sketch.DistinctCountAggregateFunction;
import io.camunda.analytics.sketch.HllSketchValue;
import io.camunda.analytics.sketch.ItemsSketchValue;
import io.camunda.analytics.sketch.KllDoublesSketchValue;
import io.camunda.analytics.sketch.QuantileAggregateFunction;
import io.camunda.analytics.sketch.TopKAggregateFunction;
import io.camunda.eventbridge.streaming.aggregate.LongRecordValue;
import io.camunda.eventbridge.streaming.aggregate.SumAggregateFunction;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The registry of known meter kinds, resolving a {@link Meter} declaration to its {@link
 * BoundMeter}. This replaces the hand-written {@code Metrics.specs()} catalog on the metric axis: a
 * meter is data (a type id + measure + params), so a new metric is a declaration, not a new Java
 * class. Kinds are grouped by merge semantics:
 *
 * <ul>
 *   <li><b>additive</b> — {@link #COUNT}, {@link #SUM}, {@link #LEVEL} (a running level = sum of
 *       signed ±1 deltas; covers counters and level-gauges), {@link #EXECUTION_TIME} (count/total/
 *       min/max, average derived on read), {@link #HISTOGRAM} (bucket counts over thresholds);
 *   <li><b>mergeable-sketch</b> — {@link #PERCENTILE} (KLL), {@link #DISTINCT} (HLL), {@link
 *       #TOP_K} (frequent-items);
 *   <li><b>ratio</b> — {@link #RATIO} (matched/total under a threshold predicate), the generic
 *       part-of-whole meter that expresses SLA-compliance and no-incident cohorts as declarations.
 * </ul>
 *
 * A non-additive last-value gauge is deferred to the declaration-driven metric port.
 */
public final class MeterCatalog {

  public static final String COUNT = "count";
  public static final String SUM = "sum";
  public static final String LEVEL = "level";
  public static final String EXECUTION_TIME = "execution_time";
  public static final String EXECUTION_TIME_SUMMARY = "execution_time_summary";
  public static final String LIFECYCLE_SUMMARY = "lifecycle_summary";
  public static final String HISTOGRAM = "histogram";
  public static final String PERCENTILE = "percentile";
  public static final String DISTINCT = "distinct";
  public static final String TOP_K = "top_k";
  public static final String RATIO = "ratio";

  private final Map<String, MeterType<?, ?>> types = new HashMap<>();

  public MeterCatalog register(final MeterType<?, ?> type) {
    if (types.putIfAbsent(type.id(), type) != null) {
      throw new IllegalArgumentException("duplicate meter type id: " + type.id());
    }
    return this;
  }

  public boolean contains(final String id) {
    return types.containsKey(id);
  }

  public Set<String> ids() {
    return Set.copyOf(types.keySet());
  }

  /**
   * Resolves a meter declaration to its aggregate + accumulator codec; throws if the type is
   * unknown.
   */
  public BoundMeter<?, ?> bind(final Meter meter) {
    Objects.requireNonNull(meter, "meter");
    final MeterType<?, ?> type = types.get(meter.type());
    if (type == null) {
      throw new IllegalArgumentException(
          "unknown meter type '" + meter.type() + "'; known: " + ids());
    }
    return type.bind(meter);
  }

  /** A catalog with all built-in meter kinds registered. */
  public static MeterCatalog withDefaults() {
    return new MeterCatalog()
        .register(
            new MeterType<>(
                COUNT,
                m -> new SumAggregateFunction<>(fact -> 1L),
                m -> new LongRecordValue(),
                sumSpec()))
        .register(
            new MeterType<>(
                SUM,
                m -> new SumAggregateFunction<>(m.requireMeasure()::asLong),
                m -> new LongRecordValue(),
                sumSpec()))
        .register(
            new MeterType<>(
                LEVEL,
                m -> new SumAggregateFunction<>(m.requireMeasure()::asLong),
                m -> new LongRecordValue(),
                sumSpec()))
        .register(
            new MeterType<>(
                EXECUTION_TIME,
                m -> new ExecutionTimeAggregateFunction<>(m.requireMeasure()::asLong),
                m -> new ExecutionTimeAccumulatorValue(),
                executionTimeSpec()))
        .register(
            new MeterType<>(
                EXECUTION_TIME_SUMMARY,
                m ->
                    new ExecutionTimeSummaryAggregateFunction<>(
                        m.requireMeasure()::asLong,
                        m.doubleArrayParam("ranks", QuantileAggregateFunction.DEFAULT_RANKS)),
                m -> new ExecutionTimeSummaryValue()))
        .register(
            new MeterType<>(
                LIFECYCLE_SUMMARY,
                m ->
                    new LifecycleSummaryAggregateFunction(
                        m.requireMeasure(),
                        m.doubleArrayParam("ranks", QuantileAggregateFunction.DEFAULT_RANKS)),
                m -> new LifecycleSummaryValue()))
        .register(
            new MeterType<>(
                HISTOGRAM,
                m -> new HistogramAggregateFunction<>(m.requireMeasure()::asLong, thresholds(m)),
                m -> new HistogramValue()))
        .register(
            new MeterType<>(
                PERCENTILE,
                m ->
                    new QuantileAggregateFunction<>(
                        m.requireMeasure()::asDouble,
                        m.doubleArrayParam("ranks", QuantileAggregateFunction.DEFAULT_RANKS)),
                m -> new KllDoublesSketchValue()))
        .register(
            new MeterType<>(
                DISTINCT,
                m -> new DistinctCountAggregateFunction<>(m.requireMeasure()::asString),
                m -> new HllSketchValue()))
        .register(
            new MeterType<>(
                TOP_K,
                m ->
                    new TopKAggregateFunction<>(
                        m.requireMeasure()::asString,
                        m.intParam("k", TopKAggregateFunction.DEFAULT_K),
                        TopKAggregateFunction.DEFAULT_MAX_MAP_SIZE),
                m -> new ItemsSketchValue()))
        .register(
            new MeterType<>(
                RATIO,
                m ->
                    new RatioAggregateFunction<>(
                        m.requireMeasure()::asDouble,
                        comparison(m),
                        m.doubleParam("threshold", 0.0)),
                m -> new RatioAccumulatorValue(),
                ratioSpec()));
  }

  /** The {@code ratio} numerator predicate; rejects an unknown op naming the known values. */
  private static RatioAggregateFunction.Comparison comparison(final Meter meter) {
    final String op = meter.requireParam("op");
    try {
      return RatioAggregateFunction.Comparison.valueOf(op.toUpperCase(Locale.ROOT));
    } catch (final IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "meter '"
              + meter.name()
              + "' ("
              + meter.type()
              + ") param 'op' has unknown comparison '"
              + op
              + "'; known: "
              + List.of(RatioAggregateFunction.Comparison.values()),
          e);
    }
  }

  /** The {@code histogram} bucket bounds: required, non-empty, strictly increasing. */
  private static long[] thresholds(final Meter meter) {
    final long[] thresholds = meter.requireLongArrayParam("thresholds");
    if (thresholds.length == 0) {
      throw new IllegalArgumentException(
          "meter '"
              + meter.name()
              + "' ("
              + meter.type()
              + ") param 'thresholds' must not be"
              + " empty");
    }
    for (int i = 1; i < thresholds.length; i++) {
      if (thresholds[i] <= thresholds[i - 1]) {
        throw new IllegalArgumentException(
            "meter '"
                + meter.name()
                + "' ("
                + meter.type()
                + ") param 'thresholds' must be strictly increasing, was "
                + Arrays.toString(thresholds));
      }
    }
    return thresholds;
  }

  /**
   * The pushdown for the single-{@code long} additive meters ({@code count}/{@code sum}/{@code
   * level}): one {@code SUM} column (empty suffix, so the physical column is just the meter's own),
   * decomposing the accumulator to itself and recomposing the summed column back to a {@code long}.
   */
  private static PushdownSpec<Long, Long> sumSpec() {
    return new PushdownSpec<>(
        List.of(new PushdownColumn("", DimensionType.LONG, Agg.SUM)),
        acc -> List.of(acc),
        cols -> asLong(cols, 0));
  }

  /**
   * The pushdown for {@code ratio}: two additive {@code SUM} columns ({@code matched}, {@code
   * total}); the ratio itself is derived on read, so recompose rebuilds the accumulator and
   * finalizes it — exactly what the app-merge path does.
   */
  private static PushdownSpec<RatioAccumulator, RatioResult> ratioSpec() {
    return new PushdownSpec<>(
        List.of(
            new PushdownColumn("matched", DimensionType.LONG, Agg.SUM),
            new PushdownColumn("total", DimensionType.LONG, Agg.SUM)),
        acc -> List.of(acc.matched(), acc.total()),
        cols -> RatioResult.of(new RatioAccumulator(asLong(cols, 0), asLong(cols, 1))));
  }

  /**
   * The pushdown for {@code execution_time}: {@code count}/{@code total} sum, {@code min}/{@code
   * max} carried as their own {@code MIN}/{@code MAX} columns (beyond the ADR's count/total/max
   * sketch — {@link ExecutionTimeResult} carries {@code minMs}, so a faithful round-trip needs the
   * min column too). Recompose mirrors {@link ExecutionTimeAggregateFunction#getResult}: the
   * average is derived, and an empty roll-up (count 0) yields all-zero.
   */
  private static PushdownSpec<ExecutionTimeAccumulator, ExecutionTimeResult> executionTimeSpec() {
    return new PushdownSpec<>(
        List.of(
            new PushdownColumn("count", DimensionType.LONG, Agg.SUM),
            new PushdownColumn("total", DimensionType.LONG, Agg.SUM),
            new PushdownColumn("min", DimensionType.LONG, Agg.MIN),
            new PushdownColumn("max", DimensionType.LONG, Agg.MAX)),
        acc -> List.of(acc.count(), acc.totalMs(), acc.minMs(), acc.maxMs()),
        cols -> {
          final long count = asLong(cols, 0);
          if (count == 0L) {
            return new ExecutionTimeResult(0L, 0.0, 0L, 0L);
          }
          final long total = asLong(cols, 1);
          return new ExecutionTimeResult(
              count, (double) total / count, asLong(cols, 2), asLong(cols, 3));
        });
  }

  /** Coerces a store-aggregated column value (a {@link Number}, or null when no row matched). */
  private static long asLong(final List<Object> columns, final int index) {
    final Object value = columns.get(index);
    return value == null ? 0L : ((Number) value).longValue();
  }
}
