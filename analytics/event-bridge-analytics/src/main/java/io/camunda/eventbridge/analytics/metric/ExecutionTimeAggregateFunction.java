/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.metric;

import io.camunda.analytics.streaming.aggregate.AggregateFunction;
import io.camunda.eventbridge.analytics.fact.ProcessInstanceExecutionTimeFact;

/**
 * The execution-time metric as a mergeable {@link AggregateFunction}: count, total, min and max of
 * process-instance durations, with the average derived on read. Every operation is additive or a
 * min/max, so {@code merge} is commutative and associative — which is what makes the per-partition
 * and pre-aggregation merges correct.
 */
public final class ExecutionTimeAggregateFunction
    implements AggregateFunction<
        ProcessInstanceExecutionTimeFact, ExecutionTimeAccumulator, ExecutionTimeResult> {

  @Override
  public ExecutionTimeAccumulator createAccumulator() {
    return ExecutionTimeAccumulator.empty();
  }

  @Override
  public ExecutionTimeAccumulator add(
      final ProcessInstanceExecutionTimeFact fact, final ExecutionTimeAccumulator acc) {
    final long duration = fact.durationMs();
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
