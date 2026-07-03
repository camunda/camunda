/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.metric;

import io.camunda.eventbridge.analytics.fact.SlaCohortFact;
import io.camunda.eventbridge.streaming.aggregate.AggregateFunction;

/**
 * The completion-time distribution over a start cohort: of the instances that started in a window,
 * how many finished within each duration band. Reuses the SLA cohort's signals — activation counts
 * a started instance, and an outcome buckets it by its {@code durationMs} into the first band whose
 * threshold it does not exceed (or the overflow band). The SLA-met metric is the one-threshold
 * special case of this; here we keep several bands to show the shape (e.g. "80% ≤10s, 10% ≤30s, 10%
 * >120s"). All counts are additive, and instances not yet settled show up as {@code started −
 * settled} (still running).
 */
public final class DurationBucketAggregateFunction
    implements AggregateFunction<
        SlaCohortFact, DurationBucketAccumulator, DurationBucketAccumulator> {

  private final long[] thresholdsMs;
  private final int bands;

  public DurationBucketAggregateFunction(final long[] thresholdsMs) {
    this.thresholdsMs = thresholdsMs.clone();
    this.bands = thresholdsMs.length + 1; // +1 for the ">last threshold" overflow band
  }

  @Override
  public DurationBucketAccumulator createAccumulator() {
    return DurationBucketAccumulator.empty(bands);
  }

  @Override
  public DurationBucketAccumulator add(
      final SlaCohortFact fact, final DurationBucketAccumulator acc) {
    final long[] buckets = acc.buckets().clone();
    if (fact.activation()) {
      return new DurationBucketAccumulator(acc.started() + 1L, buckets);
    }
    buckets[bandOf(fact.durationMs())] += 1L;
    return new DurationBucketAccumulator(acc.started(), buckets);
  }

  @Override
  public DurationBucketAccumulator merge(
      final DurationBucketAccumulator a, final DurationBucketAccumulator b) {
    final long[] buckets = a.buckets().clone();
    for (int i = 0; i < buckets.length; i++) {
      buckets[i] += b.buckets()[i];
    }
    return new DurationBucketAccumulator(a.started() + b.started(), buckets);
  }

  @Override
  public DurationBucketAccumulator getResult(final DurationBucketAccumulator acc) {
    return acc;
  }

  private int bandOf(final long durationMs) {
    for (int i = 0; i < thresholdsMs.length; i++) {
      if (durationMs <= thresholdsMs[i]) {
        return i;
      }
    }
    return bands - 1;
  }
}
