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

/**
 * The composite execution-time meter as a mergeable {@link AggregateFunction}: folds a fact's
 * duration into an {@link ExecutionTimeSummary} that carries count/total/min/max and a KLL sketch
 * together, so one meter serves count, total, average, min, max, and any percentile. Parameterized
 * by a duration extractor and the percentile ranks to report. Every component merge is
 * commutative/associative, so it pre-aggregates across partitions.
 *
 * @deprecated the {@code execution_time_summary} bundle predates per-meter filters; declare the
 *     primitive meters (execution-time + percentile) instead. Kept for existing declarations.
 * @param <F> the fact type
 */
@Deprecated
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
    return ExecutionTimeSummary.merge(a, b);
  }

  /** In place: stats and the KLL sketch fold directly into the caller-owned target summary. */
  @Override
  public ExecutionTimeSummary mergeInto(
      final ExecutionTimeSummary target, final ExecutionTimeSummary delta) {
    target.merge(delta);
    return target;
  }

  @Override
  public ExecutionTimeSummaryResult getResult(final ExecutionTimeSummary acc) {
    return acc.result(ranks);
  }
}
