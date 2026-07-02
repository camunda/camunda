/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.streaming.aggregate;

/**
 * The read-facing result of {@link QuantileAggregateFunction}: the observation count, the exact min
 * and max, and the estimated value at each requested rank (parallel arrays: {@code values[i]} is
 * the estimate for {@code ranks[i]}, a fraction in [0, 1]). Values are approximate; count/min/max
 * are exact.
 *
 * @param count the number of observations folded in
 * @param min the smallest observed value ({@code NaN} when empty)
 * @param max the largest observed value ({@code NaN} when empty)
 * @param ranks the requested quantile ranks, fractions in [0, 1]
 * @param values the estimated value at each rank, parallel to {@code ranks}
 */
public record QuantileResult(long count, double min, double max, double[] ranks, double[] values) {

  static QuantileResult empty(final double[] ranks) {
    final double[] values = new double[ranks.length];
    java.util.Arrays.fill(values, Double.NaN);
    return new QuantileResult(0L, Double.NaN, Double.NaN, ranks.clone(), values);
  }

  /** The estimated value at {@code rank}, or {@code NaN} if that rank was not requested. */
  public double valueAt(final double rank) {
    for (int i = 0; i < ranks.length; i++) {
      if (ranks[i] == rank) {
        return values[i];
      }
    }
    return Double.NaN;
  }
}
