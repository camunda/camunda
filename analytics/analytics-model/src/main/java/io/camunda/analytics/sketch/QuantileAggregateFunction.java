/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.sketch;

import io.camunda.eventbridge.streaming.aggregate.AggregateFunction;
import java.util.function.ToDoubleFunction;
import org.apache.datasketches.kll.KllDoublesSketch;
import org.apache.datasketches.quantilescommon.QuantileSearchCriteria;

/**
 * Quantiles (median, p75, p90, p99, …) of a numeric measure as a mergeable {@link
 * AggregateFunction}, backed by a KLL sketch. Parameterized by a value extractor so it works for
 * any fact carrying a number — durations, token counts, amounts. The accumulator is the sketch
 * itself; its {@code merge} is a sketch union, so the estimate is approximate but the <em>merge is
 * exact and commutative/associative</em> — which is what lets a quantile be pre-aggregated and
 * combined across partitions the same way {@code count} or {@code sum} can, with bounded state
 * instead of every observation.
 *
 * <p>This mirrors Optimize's {@code PERCENTILE} duration aggregation, which is likewise
 * t-digest/sketch-backed rather than exact. The default ranks are the ones Optimize's default
 * dashboards report on duration tiles — p50/p75/p90/p99 (see the {@code p75Duration}, {@code
 * p99Duration} and {@code controlChart} instant-preview tiles).
 *
 * @param <F> the fact type
 */
public final class QuantileAggregateFunction<F>
    implements AggregateFunction<F, KllDoublesSketch, QuantileResult> {

  /**
   * Default ranks (fractions in [0, 1]): the median plus the tail percentiles Optimize's default
   * duration dashboards report — p50, p75, p90, p99.
   */
  public static final double[] DEFAULT_RANKS = {0.5, 0.75, 0.9, 0.99};

  private final ToDoubleFunction<F> value;
  private final double[] ranks;

  public QuantileAggregateFunction(final ToDoubleFunction<F> value) {
    this(value, DEFAULT_RANKS);
  }

  public QuantileAggregateFunction(final ToDoubleFunction<F> value, final double[] ranks) {
    this.value = value;
    this.ranks = ranks.clone();
  }

  @Override
  public KllDoublesSketch createAccumulator() {
    return KllDoublesSketch.newHeapInstance();
  }

  @Override
  public KllDoublesSketch add(final F fact, final KllDoublesSketch sketch) {
    sketch.update(value.applyAsDouble(fact));
    return sketch;
  }

  @Override
  public KllDoublesSketch merge(final KllDoublesSketch a, final KllDoublesSketch b) {
    final KllDoublesSketch merged = KllDoublesSketch.newHeapInstance();
    merged.merge(a);
    merged.merge(b);
    return merged;
  }

  /** In place: KLL merges directly into the caller-owned target, no fresh sketch per fold. */
  @Override
  public KllDoublesSketch mergeInto(final KllDoublesSketch target, final KllDoublesSketch delta) {
    target.merge(delta);
    return target;
  }

  @Override
  public QuantileResult getResult(final KllDoublesSketch sketch) {
    if (sketch.isEmpty()) {
      return QuantileResult.empty(ranks);
    }
    final double[] values = new double[ranks.length];
    for (int i = 0; i < ranks.length; i++) {
      values[i] = sketch.getQuantile(ranks[i], QuantileSearchCriteria.INCLUSIVE);
    }
    return new QuantileResult(
        sketch.getN(), sketch.getMinItem(), sketch.getMaxItem(), ranks.clone(), values);
  }
}
