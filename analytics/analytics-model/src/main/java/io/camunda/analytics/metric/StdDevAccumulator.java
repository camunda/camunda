/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.metric;

/**
 * The accumulator for the {@code stddev} meter: the classic {@code (count, sum, sumSq)} moments,
 * every component additive, so the meter pushes down as three {@code SUM} columns and merges like
 * {@code sum}. The standard deviation is derived only on read (here and in the pushdown recompose,
 * through the shared {@link #populationStdDev}).
 *
 * <p><b>Population, not sample.</b> A cube cell aggregates <em>every</em> fact of its key/window —
 * the whole population of what was observed, not a sample drawn from it — so the population form
 * {@code sqrt((n·sumSq − sum²)) / n} is the honest choice (and gives the natural {@code 0} for a
 * single observation, where the sample form is undefined).
 *
 * <p>{@code sum}/{@code sumSq} accumulate the measure as {@code long}s (the measure seam is {@code
 * long}-valued); squaring keeps exact headroom for millions of observations of typical duration
 * magnitudes (e.g. 10^6 facts of ~10^5 ms), the same additive-overflow envelope {@code sum} lives
 * with.
 */
public record StdDevAccumulator(long count, long sum, long sumSq) {

  public static StdDevAccumulator empty() {
    return new StdDevAccumulator(0L, 0L, 0L);
  }

  /**
   * The population standard deviation {@code sqrt(n·sumSq − sum²) / n} of the given moments —
   * shared by the app-merge read and the pushdown recompose so both derive identical values. Guards
   * the empty roll-up ({@code n == 0} reads {@code 0}) and clamps the tiny negative variance a
   * floating-point cancellation can produce ({@code n == 1} is exactly {@code 0} already: {@code
   * 1·v² − v²}).
   */
  public static double populationStdDev(final long count, final long sum, final long sumSq) {
    if (count == 0L) {
      return 0.0;
    }
    final double n = count;
    final double variance = Math.max(0.0, (n * sumSq - (double) sum * sum) / (n * n));
    return Math.sqrt(variance);
  }
}
