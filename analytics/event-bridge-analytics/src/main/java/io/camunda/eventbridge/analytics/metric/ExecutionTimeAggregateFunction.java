/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.metric;

import io.camunda.analytics.streaming.aggregate.AggregateFunction;
import java.util.function.ToLongFunction;

/**
 * Execution-time statistics as a mergeable {@link AggregateFunction}: count, total, min and max of
 * a fact's duration, with the average derived on read. Parameterized by a duration extractor so it
 * works for any fact that carries a duration — process instances, elements, jobs — not just one
 * type. Every operation is additive or a min/max, so {@code merge} is commutative and associative,
 * which is what makes the per-partition and pre-aggregation merges correct.
 *
 * @param <F> the fact type
 */
public final class ExecutionTimeAggregateFunction<F>
    implements AggregateFunction<F, ExecutionTimeAccumulator, ExecutionTimeResult> {

  private final ToLongFunction<F> durationMs;

  public ExecutionTimeAggregateFunction(final ToLongFunction<F> durationMs) {
    this.durationMs = durationMs;
  }

  @Override
  public ExecutionTimeAccumulator createAccumulator() {
    return ExecutionTimeAccumulator.empty();
  }

  @Override
  public ExecutionTimeAccumulator add(final F fact, final ExecutionTimeAccumulator acc) {
    final long duration = durationMs.applyAsLong(fact);
    return new ExecutionTimeAccumulator(
        acc.count() + 1,
        acc.totalMs() + duration,
        Math.min(acc.minMs(), duration),
        Math.max(acc.maxMs(), duration));
  }

  @Override
  public ExecutionTimeAccumulator merge(
      final ExecutionTimeAccumulator a, final ExecutionTimeAccumulator b) {
    return new ExecutionTimeAccumulator(
        a.count() + b.count(),
        a.totalMs() + b.totalMs(),
        Math.min(a.minMs(), b.minMs()),
        Math.max(a.maxMs(), b.maxMs()));
  }

  @Override
  public ExecutionTimeResult getResult(final ExecutionTimeAccumulator acc) {
    if (acc.count() == 0) {
      return new ExecutionTimeResult(0L, 0.0, 0L, 0L);
    }
    return new ExecutionTimeResult(
        acc.count(), (double) acc.totalMs() / acc.count(), acc.minMs(), acc.maxMs());
  }
}
