/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.metric;

import io.camunda.analytics.dimension.FactRow;
import io.camunda.analytics.fact.Fact;
import io.camunda.analytics.fact.Transition;
import io.camunda.analytics.meter.MeasureRef;
import io.camunda.eventbridge.streaming.aggregate.AggregateFunction;

/**
 * The composite lifecycle-summary meter as a mergeable {@link AggregateFunction}: reads a fact's
 * {@link Fact#TRANSITION} to count activations/completions/terminations, and folds the duration of
 * ended events into an {@link ExecutionTimeSummary} — all in one accumulator, so a single meter
 * serves the lifecycle counts and the duration family. Operates on {@link FactRow} directly since
 * it reads the transition and duration by name. Facts without a recognised transition contribute
 * only their duration (if any); a duration is folded only when present, so activations are not
 * counted as zero-duration observations.
 */
public final class LifecycleSummaryAggregateFunction
    implements AggregateFunction<FactRow, LifecycleSummary, LifecycleSummaryResult> {

  private final MeasureRef duration;
  private final double[] ranks;

  public LifecycleSummaryAggregateFunction(final MeasureRef duration, final double[] ranks) {
    this.duration = duration;
    this.ranks = ranks.clone();
  }

  @Override
  public LifecycleSummary createAccumulator() {
    return LifecycleSummary.empty();
  }

  @Override
  public LifecycleSummary add(final FactRow fact, final LifecycleSummary acc) {
    if (fact.get(Fact.TRANSITION) instanceof final String transition) {
      if (Transition.ACTIVATED.name().equals(transition)) {
        acc.recordActivated();
      } else if (Transition.COMPLETED.name().equals(transition)) {
        acc.recordCompleted();
      } else if (Transition.TERMINATED.name().equals(transition)) {
        acc.recordTerminated();
      }
    }
    if (fact.get(duration.field()) instanceof final Number durationMs) {
      acc.recordDuration(durationMs.longValue());
    }
    return acc;
  }

  @Override
  public LifecycleSummary merge(final LifecycleSummary a, final LifecycleSummary b) {
    return LifecycleSummary.merge(a, b);
  }

  /** In place: counters and the duration summary fold directly into the caller-owned target. */
  @Override
  public LifecycleSummary mergeInto(final LifecycleSummary target, final LifecycleSummary delta) {
    target.merge(delta);
    return target;
  }

  @Override
  public LifecycleSummaryResult getResult(final LifecycleSummary acc) {
    return new LifecycleSummaryResult(
        acc.activated(), acc.completed(), acc.terminated(), acc.duration().result(ranks));
  }
}
