/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.metric;

import io.camunda.eventbridge.streaming.aggregate.AggregateFunction;
import java.util.Arrays;
import java.util.function.ToLongFunction;

/**
 * A value histogram as a mergeable {@link AggregateFunction}: counts how many facts fall in each
 * bucket of a numeric measure, bucketed by a fixed set of strictly-ascending {@code thresholds}.
 * There are {@code thresholds.length + 1} buckets — bucket {@code i} counts values in {@code
 * [thresholds[i-1], thresholds[i])} and the last, overflow, bucket counts values {@code >=} the
 * final threshold. The accumulator is the bucket-count array; {@code merge} is element-wise
 * addition, so it is commutative and associative and pre-aggregatable across partitions the same
 * way {@code count}/{@code sum} are.
 *
 * <p>Unlike {@code DurationBucketAggregateFunction}, which is cohort-shaped (tied to {@code
 * SlaCohortFact}), this buckets any numeric measure — the generic histogram meter kind.
 *
 * @param <F> the fact type
 */
public final class HistogramAggregateFunction<F> implements AggregateFunction<F, long[], long[]> {

  private final ToLongFunction<F> value;
  private final long[] thresholds;

  public HistogramAggregateFunction(final ToLongFunction<F> value, final long[] thresholds) {
    if (thresholds.length == 0) {
      throw new IllegalArgumentException("a histogram needs at least one threshold");
    }
    for (int i = 1; i < thresholds.length; i++) {
      if (thresholds[i] <= thresholds[i - 1]) {
        throw new IllegalArgumentException(
            "histogram thresholds must be strictly ascending: " + Arrays.toString(thresholds));
      }
    }
    this.value = value;
    this.thresholds = thresholds.clone();
  }

  private int buckets() {
    return thresholds.length + 1;
  }

  @Override
  public long[] createAccumulator() {
    return new long[buckets()];
  }

  @Override
  public long[] add(final F fact, final long[] acc) {
    acc[indexFor(value.applyAsLong(fact))]++;
    return acc;
  }

  @Override
  public long[] merge(final long[] a, final long[] b) {
    final long[] out = new long[a.length];
    for (int i = 0; i < a.length; i++) {
      out[i] = a[i] + b[i];
    }
    return out;
  }

  @Override
  public long[] getResult(final long[] acc) {
    return acc.clone();
  }

  private int indexFor(final long observed) {
    for (int i = 0; i < thresholds.length; i++) {
      if (observed < thresholds[i]) {
        return i;
      }
    }
    return thresholds.length;
  }
}
