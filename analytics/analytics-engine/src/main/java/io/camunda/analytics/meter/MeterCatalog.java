/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.meter;

import io.camunda.analytics.metric.ExecutionTimeAccumulatorValue;
import io.camunda.analytics.metric.ExecutionTimeAggregateFunction;
import io.camunda.analytics.metric.HistogramAggregateFunction;
import io.camunda.analytics.metric.HistogramValue;
import io.camunda.analytics.sketch.DistinctCountAggregateFunction;
import io.camunda.analytics.sketch.HllSketchValue;
import io.camunda.analytics.sketch.ItemsSketchValue;
import io.camunda.analytics.sketch.KllDoublesSketchValue;
import io.camunda.analytics.sketch.QuantileAggregateFunction;
import io.camunda.analytics.sketch.TopKAggregateFunction;
import io.camunda.eventbridge.streaming.aggregate.LongRecordValue;
import io.camunda.eventbridge.streaming.aggregate.SumAggregateFunction;
import java.util.HashMap;
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
 *       #TOP_K} (frequent-items).
 * </ul>
 *
 * Ratio/cohort meters, and a non-additive last-value gauge, are deferred to the declaration-driven
 * metric port.
 */
public final class MeterCatalog {

  public static final String COUNT = "count";
  public static final String SUM = "sum";
  public static final String LEVEL = "level";
  public static final String EXECUTION_TIME = "execution_time";
  public static final String HISTOGRAM = "histogram";
  public static final String PERCENTILE = "percentile";
  public static final String DISTINCT = "distinct";
  public static final String TOP_K = "top_k";

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
                COUNT, m -> new SumAggregateFunction<>(fact -> 1L), m -> new LongRecordValue()))
        .register(
            new MeterType<>(
                SUM,
                m -> new SumAggregateFunction<>(m.requireMeasure()::asLong),
                m -> new LongRecordValue()))
        .register(
            new MeterType<>(
                LEVEL,
                m -> new SumAggregateFunction<>(m.requireMeasure()::asLong),
                m -> new LongRecordValue()))
        .register(
            new MeterType<>(
                EXECUTION_TIME,
                m -> new ExecutionTimeAggregateFunction<>(m.requireMeasure()::asLong),
                m -> new ExecutionTimeAccumulatorValue()))
        .register(
            new MeterType<>(
                HISTOGRAM,
                m ->
                    new HistogramAggregateFunction<>(
                        m.requireMeasure()::asLong, m.requireLongArrayParam("thresholds")),
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
                m -> new ItemsSketchValue()));
  }
}
