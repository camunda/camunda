/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.sink.algebra;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Estimates p50/p90/p99 purely in Java (folding values through an {@link Algebra.Accumulator},
 * walking the drained bins the same way the generated SQL does) and compares against the exact
 * percentile of the same sample. This pins down the scheme's relative-error bound without needing
 * DuckDB — {@code EntityMetricsMergeClosureDuckDbTest} in {@code io.camunda.analytics.lake.metrics}
 * separately verifies the actual generated SQL reproduces the same estimate.
 */
class ExpHistogramPercentileAccuracyTest {

  private static final int SCALE = 3;
  private static final double RELATIVE_ERROR_BOUND = Math.pow(2, -SCALE);

  @Test
  void shouldEstimatePercentilesWithinTheSchemesErrorBound() {
    // given ~10k values from a skewed (log-normal-ish) distribution, folded through a histogram
    // accumulator and drained into (bin_lo, bin_hi, cnt) rows
    final Random random = new Random(99);
    final List<Long> sample = new ArrayList<>();
    for (int i = 0; i < 10_000; i++) {
      final double skewed = Math.exp(random.nextGaussian() * 1.2 + 5.0); // heavy right tail
      sample.add(Math.max(0L, (long) skewed));
    }

    final Algebra algebra = Algebras.expHistogram(SCALE);
    final Algebra.Accumulator accumulator = algebra.create();
    final List<long[]> bins = new ArrayList<>(); // {bin_lo, bin_hi, cnt}
    final Algebra.RowWriter writer = collectingWriter(bins);
    for (final long value : sample) {
      accumulator.add(value);
    }
    accumulator.drain(writer);

    final long total = bins.stream().mapToLong(b -> b[2]).sum();
    assertThat(total).isEqualTo(sample.size());

    final List<Long> sorted = new ArrayList<>(sample);
    Collections.sort(sorted);

    // when estimating p50/p90/p99 by the same cumulative-count walk the generated SQL performs
    for (final double quantile : List.of(0.5, 0.9, 0.99)) {
      final double estimate = estimatePercentile(bins, total, quantile);
      final double exact = exactPercentile(sorted, quantile);

      // then the estimate is within the scheme's relative error bound of the exact value
      final double relativeError = Math.abs(estimate - exact) / Math.max(exact, 1.0);
      assertThat(relativeError)
          .withFailMessage(
              "p%s estimate %s vs exact %s: relative error %s exceeds bound %s",
              (int) (quantile * 100), estimate, exact, relativeError, RELATIVE_ERROR_BOUND)
          .isLessThanOrEqualTo(RELATIVE_ERROR_BOUND + 0.02); // small slack for midpoint reporting
    }
  }

  /**
   * Mirrors the generated SQL's cumulative-count walk (see {@code
   * ExpHistogramAlgebra#finalizeQuery}).
   */
  private static double estimatePercentile(
      final List<long[]> bins, final long total, final double quantile) {
    final List<long[]> sorted = new ArrayList<>(bins);
    sorted.sort((a, b) -> Long.compare(a[0], b[0]));
    final double rank = quantile * total;
    long cumulative = 0;
    for (final long[] bin : sorted) {
      cumulative += bin[2];
      if (cumulative >= rank) {
        return (bin[0] + bin[1]) / 2.0;
      }
    }
    final long[] last = sorted.get(sorted.size() - 1);
    return (last[0] + last[1]) / 2.0;
  }

  private static double exactPercentile(final List<Long> sorted, final double quantile) {
    final int index = (int) Math.min(sorted.size() - 1, Math.floor(quantile * sorted.size()));
    return sorted.get(index);
  }

  private static Algebra.RowWriter collectingWriter(final List<long[]> out) {
    return new Algebra.RowWriter() {
      private long lo;
      private long hi;
      private long cnt;

      @Override
      public void beginRow() {}

      @Override
      public void writeLong(final int columnIndex, final long value) {
        switch (columnIndex) {
          case 0 -> lo = value;
          case 1 -> hi = value;
          case 2 -> cnt = value;
          default -> throw new IllegalArgumentException("unexpected column " + columnIndex);
        }
      }

      @Override
      public void endRow() {
        out.add(new long[] {lo, hi, cnt});
      }
    };
  }
}
