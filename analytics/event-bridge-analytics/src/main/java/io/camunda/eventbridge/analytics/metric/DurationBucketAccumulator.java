/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.metric;

/**
 * Accumulator for the completion-time distribution over a start cohort: {@code started} instances,
 * and how many of them landed in each duration band ({@code buckets}, one more than the number of
 * thresholds — the last is the overflow ">last threshold"). All counts are additive so the merge is
 * exact. {@code started - settled} instances are still running (not yet in any band).
 */
public record DurationBucketAccumulator(long started, long[] buckets) {

  public static DurationBucketAccumulator empty(final int bands) {
    return new DurationBucketAccumulator(0L, new long[bands]);
  }

  /** Instances that have reached a terminal state (sum across bands). */
  public long settled() {
    long sum = 0L;
    for (final long b : buckets) {
      sum += b;
    }
    return sum;
  }
}
