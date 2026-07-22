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
import java.util.List;
import java.util.Random;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Property-tests {@link ExpHistogramAlgebra#index}/{@link ExpHistogramAlgebra#lowerBound}/{@link
 * ExpHistogramAlgebra#upperBound} against the invariant every bin must satisfy, across random
 * values and every power-of-two edge case. As documented on {@link ExpHistogramAlgebra}'s class
 * javadoc, this test found no off-by-one in the given formulas — they are implemented here exactly
 * as specified.
 */
class ExpHistogramBoundaryPropertyTest {

  /**
   * The documented supported domain (see {@code ExpHistogramAlgebra}'s class javadoc and its {@code
   * maxBins} sizing): values at or beyond {@code 2^62} are out of scope, and deliberately so —
   * {@code upperBound} of the bin starting at {@code 2^62} would need {@code lowerBound} of the
   * *next* bin, which is {@code 1L << 63}, silently overflowing a signed 64-bit long into {@code
   * Long.MIN_VALUE}. This test caps its random sample to the supported domain for exactly this
   * reason; the overflow is a real boundary of a 64-bit signed representation, not an off-by- one
   * in the given formulas (which this test otherwise confirms are correct — see the class javadoc
   * on {@code ExpHistogramAlgebra}).
   */
  private static final long MAX_SUPPORTED_VALUE = (1L << 62) - 1;

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2, 3, 5, 8})
  void shouldSatisfyBoundaryInvariantForRandomValues(final int scale) {
    // given a large random sample of non-negative longs, biased toward smaller magnitudes so both
    // the unit-width and log-linear regions are well exercised
    final Random random = new Random(7);
    for (int i = 0; i < 200_000; i++) {
      final long value = randomBiasedNonNegative(random);
      assertBoundaryInvariant(value, scale);
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2, 3, 5, 8})
  void shouldSatisfyBoundaryInvariantAtEdgeCases(final int scale) {
    // given every edge value the spec explicitly calls out: 0, 1, and 2^k-1/2^k/2^k+1 for several k
    for (final long value : edgeCaseValues()) {
      assertBoundaryInvariant(value, scale);
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2, 3, 5, 8})
  void shouldBeMonotoneInValue(final int scale) {
    // given a sorted sample of values (edge cases plus a random sample)
    final List<Long> values = new ArrayList<>(edgeCaseValues());
    final Random random = new Random(11);
    for (int i = 0; i < 50_000; i++) {
      values.add(randomBiasedNonNegative(random));
    }
    values.sort(Long::compareTo);

    // then index() never decreases as the value increases
    int previousIndex = -1;
    for (final long value : values) {
      final int index = ExpHistogramAlgebra.index(value, scale);
      assertThat(index)
          .withFailMessage(
              "index(%d, scale=%d) = %d is less than the previous index %d",
              value, scale, index, previousIndex)
          .isGreaterThanOrEqualTo(previousIndex);
      previousIndex = index;
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 2, 3, 5, 8})
  void shouldBoundRelativeBinWidthAboveTheScaleThreshold(final int scale) {
    // given every bin index reachable from values at or above 2^scale, up to a generous ceiling
    final long threshold = 1L << scale;
    long value = threshold;
    final long limit = 1L << 40; // far beyond the unit-width region, plenty of blocks to check
    while (value < limit) {
      final int idx = ExpHistogramAlgebra.index(value, scale);
      final long lo = ExpHistogramAlgebra.lowerBound(idx, scale);
      final long hi = ExpHistogramAlgebra.upperBound(idx, scale);

      // then the bin's relative width never exceeds 2^-scale
      final double relativeWidth = (hi - lo) / (double) lo;
      assertThat(relativeWidth)
          .withFailMessage(
              "bin %d at scale %d has relative width %s (lo=%d, hi=%d), expected <= 2^-%d",
              idx, scale, relativeWidth, lo, hi, scale)
          .isLessThanOrEqualTo(Math.pow(2, -scale) + 1e-9);

      value = hi; // jump to the next bin's first value
    }
  }

  private static void assertBoundaryInvariant(final long value, final int scale) {
    final int idx = ExpHistogramAlgebra.index(value, scale);
    final long lo = ExpHistogramAlgebra.lowerBound(idx, scale);
    final long hi = ExpHistogramAlgebra.upperBound(idx, scale);
    assertThat(lo)
        .withFailMessage(
            "lowerBound(index(%d)) = %d is not <= %d (scale=%d, idx=%d)",
            value, lo, value, scale, idx)
        .isLessThanOrEqualTo(Math.max(value, 0));
    assertThat(hi)
        .withFailMessage(
            "upperBound(index(%d)) = %d is not > %d (scale=%d, idx=%d)",
            value, hi, value, scale, idx)
        .isGreaterThan(Math.max(value, 0));
  }

  private static long randomBiasedNonNegative(final Random random) {
    // mix of small values (exercise the unit-width region) and large ones (exercise deep blocks),
    // capped at MAX_SUPPORTED_VALUE (see its javadoc)
    final int shape = random.nextInt(3);
    return switch (shape) {
      case 0 -> (long) random.nextInt(1 << 16);
      case 1 -> Math.abs(random.nextLong() % (1L << 40));
      default -> Math.abs(random.nextLong() % MAX_SUPPORTED_VALUE);
    };
  }

  private static List<Long> edgeCaseValues() {
    final List<Long> values = new ArrayList<>();
    values.add(0L);
    values.add(1L);
    for (int k = 1; k <= 61; k++) {
      final long pow = 1L << k;
      values.add(pow - 1);
      values.add(pow);
      values.add(pow + 1);
    }
    values.add(Long.MAX_VALUE / 4);
    return values;
  }
}
