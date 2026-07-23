/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.sink.algebra;

/**
 * Entry point for the built-in {@link Algebra} implementations. Kept as static factories (rather
 * than public constructors on the concrete classes) so a declaration such as {@code
 * EntityMetrics.declare(...).measure("work_time_ms", Algebras.scalarStats(),
 * Algebras.expHistogram(3))} reads as choosing an algebra, not wiring up an implementation class.
 */
public final class Algebras {

  private Algebras() {}

  /** Count/sum/min/max — see {@link ScalarStatsAlgebra}. */
  public static Algebra scalarStats() {
    return new ScalarStatsAlgebra();
  }

  /**
   * A plain row count, unprefixed and measure-less — see {@link CountAlgebra}. Backs {@code
   * io.camunda.analytics.lake.metrics.EntityMetrics.Builder#count()}; not intended to be declared
   * directly as a {@code .measure(...)} algebra (a count has no raw column to fold).
   */
  public static Algebra count() {
    return new CountAlgebra();
  }

  /**
   * A base-2 log-linear histogram at the given scale (higher scale = finer bins = smaller relative
   * error, at the cost of more bins) — see {@link ExpHistogramAlgebra}.
   *
   * @param scale number of bits used to sub-divide each power-of-two range; relative bin width at
   *     or above {@code 2^scale} is at most {@code 2^-scale}
   */
  public static Algebra expHistogram(final int scale) {
    return new ExpHistogramAlgebra(scale);
  }
}
