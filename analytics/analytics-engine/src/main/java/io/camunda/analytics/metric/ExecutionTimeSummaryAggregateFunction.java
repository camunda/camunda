/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.metric;

import io.camunda.eventbridge.streaming.aggregate.AggregateFunction;
import java.util.function.ToLongFunction;
import org.apache.datasketches.kll.KllDoublesSketch;
import org.apache.datasketches.quantilescommon.QuantileSearchCriteria;

/**
 * The composite execution-time meter as a mergeable {@link AggregateFunction}: folds a fact's
 * duration into an {@link ExecutionTimeSummary} that carries count/total/min/max and a KLL sketch
 * together, so one meter serves count, total, average, min, max, and any percentile. Parameterized
 * by a duration extractor and the percentile ranks to report. Every component merge is
 * commutative/associative, so it pre-aggregates across partitions.
 *
 * @param <F> the fact type
 */
public final class ExecutionTimeSummaryAggregateFunction<F>
    implements AggregateFunction<F, ExecutionTimeSummary, ExecutionTimeSummaryResult> {

  private final ToLongFunction<F> durationMs;
  private final double[] ranks;

  public ExecutionTimeSummaryAggregateFunction(
      final ToLongFunction<F> durationMs, final double[] ranks) {
    this.durationMs = durationMs;
    this.ranks = ranks.clone();
  }

  @Override
  public ExecutionTimeSummary createAccumulator() {
    return ExecutionTimeSummary.empty();
  }

  @Override
  public ExecutionTimeSummary add(final F fact, final ExecutionTimeSummary acc) {
    acc.record(durationMs.applyAsLong(fact));
    return acc;
  }

  @Override
  public ExecutionTimeSummary merge(final ExecutionTimeSummary a, final ExecutionTimeSummary b) {
    final KllDoublesSketch merged = KllDoublesSketch.newHeapInstance();
    merged.merge(a.sketch());
    merged.merge(b.sketch());
    return new ExecutionTimeSummary(
        a.count() + b.count(),
        a.totalMs() + b.totalMs(),
        Math.min(a.minMs(), b.minMs()),
        Math.max(a.maxMs(), b.maxMs()),
        merged);
  }

  @Override
  public ExecutionTimeSummaryResult getResult(final ExecutionTimeSummary acc) {
    if (acc.count() == 0) {
      final double[] empty = new double[ranks.length];
      java.util.Arrays.fill(empty, Double.NaN);
      return new ExecutionTimeSummaryResult(0L, 0.0, 0L, 0L, ranks.clone(), empty);
    }
    final double[] quantiles = new double[ranks.length];
    for (int i = 0; i < ranks.length; i++) {
      quantiles[i] = acc.sketch().getQuantile(ranks[i], QuantileSearchCriteria.INCLUSIVE);
    }
    return new ExecutionTimeSummaryResult(
        acc.count(),
        (double) acc.totalMs() / acc.count(),
        acc.minMs(),
        acc.maxMs(),
        ranks.clone(),
        quantiles);
  }
}
