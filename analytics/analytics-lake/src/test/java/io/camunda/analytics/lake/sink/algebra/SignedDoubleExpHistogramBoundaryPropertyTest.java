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
 * Property-tests {@link SignedDoubleExpHistogramAlgebra}'s frozen bin-boundary formulas: {@code
 * lower <= v < upper} for positives, the mirrored {@code lower < v <= upper} for negatives, the
 * dedicated zero bucket, monotonicity across the full signed domain, and the {@code 2^-scale}
 * relative-width bound at or above {@code |v| == 1}. Mirrors {@code
 * ExpHistogramBoundaryPropertyTest} one level up (signed doubles instead of non-negative longs).
 */
class SignedDoubleExpHistogramBoundaryPropertyTest {

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2, 3, 5, 8, 12})
  void shouldSatisfyBoundaryInvariantForRandomValues(final int scale) {
    final Random random = new Random(2026);
    for (int i = 0; i < 200_000; i++) {
      assertBoundaryInvariant(randomBiasedFinite(random), scale);
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2, 3, 5, 8, 12})
  void shouldSatisfyBoundaryInvariantAtEdgeCases(final int scale) {
    for (final double value : edgeCaseValues()) {
      assertBoundaryInvariant(value, scale);
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2, 3, 5, 8})
  void shouldGiveZeroItsOwnDegenerateBucket(final int scale) {
    assertThat(SignedDoubleExpHistogramAlgebra.signedIndex(0.0, scale)).isZero();
    assertThat(SignedDoubleExpHistogramAlgebra.signedIndex(-0.0, scale)).isZero();
    assertThat(SignedDoubleExpHistogramAlgebra.signedLowerBound(0, scale)).isEqualTo(0.0);
    assertThat(SignedDoubleExpHistogramAlgebra.signedUpperBound(0, scale)).isEqualTo(0.0);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2, 3, 5, 8})
  void shouldCollapseSubnormalsIntoTheUnitBucket(final int scale) {
    final double smallestSubnormal = Double.MIN_VALUE;
    final double largestSubnormal = Math.nextDown(Double.MIN_NORMAL);
    assertThat(SignedDoubleExpHistogramAlgebra.unsignedIndex(smallestSubnormal, scale))
        .isEqualTo(1);
    assertThat(SignedDoubleExpHistogramAlgebra.unsignedIndex(largestSubnormal, scale)).isEqualTo(1);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2, 3, 5, 8})
  void shouldBeMonotoneInValue(final int scale) {
    final List<Double> values = new ArrayList<>(edgeCaseValues());
    final Random random = new Random(11);
    for (int i = 0; i < 50_000; i++) {
      values.add(randomBiasedFinite(random));
    }
    values.sort(Double::compareTo);

    int previousIndex = Integer.MIN_VALUE;
    for (final double value : values) {
      final int index = SignedDoubleExpHistogramAlgebra.signedIndex(value, scale);
      assertThat(index)
          .withFailMessage(
              "signedIndex(%s, scale=%d) = %d is less than the previous index %d",
              value, scale, index, previousIndex)
          .isGreaterThanOrEqualTo(previousIndex);
      previousIndex = index;
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 2, 3, 5, 8})
  void shouldBoundRelativeBinWidthAboveTheScaleThreshold(final int scale) {
    // walk positive unsigned bins from |v|=1 upward, checking relative width at each step
    int idx = SignedDoubleExpHistogramAlgebra.unsignedIndex(1.0, scale);
    double value = 1.0;
    final double limit = 1e12; // plenty of blocks without running long
    while (value < limit) {
      idx = SignedDoubleExpHistogramAlgebra.unsignedIndex(value, scale);
      final double lo = SignedDoubleExpHistogramAlgebra.unsignedLowerBound(idx, scale);
      final double hi = SignedDoubleExpHistogramAlgebra.unsignedUpperBound(idx, scale);
      if (lo > 0) {
        final double relativeWidth = (hi - lo) / lo;
        assertThat(relativeWidth)
            .withFailMessage(
                "bin %d at scale %d has relative width %s (lo=%s, hi=%s), expected <= 2^-%d",
                idx, scale, relativeWidth, lo, hi, scale)
            .isLessThanOrEqualTo(Math.pow(2, -scale) + 1e-9);
      }
      value = hi;
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 2, 3})
  void shouldSaturateTheTopBinsUpperBoundToInfinity(final int scale) {
    final int idx = SignedDoubleExpHistogramAlgebra.unsignedIndex(Double.MAX_VALUE, scale);
    final double upper = SignedDoubleExpHistogramAlgebra.unsignedUpperBound(idx, scale);
    // the true next power of two above Double.MAX_VALUE's block (2^1024) overflows a finite
    // double -- saturating to +Infinity is the correct, intentional representation (see class
    // javadoc), not a bug.
    assertThat(upper).isEqualTo(Double.POSITIVE_INFINITY);
  }

  private static void assertBoundaryInvariant(final double value, final int scale) {
    final int idx = SignedDoubleExpHistogramAlgebra.signedIndex(value, scale);
    final double lo = SignedDoubleExpHistogramAlgebra.signedLowerBound(idx, scale);
    final double hi = SignedDoubleExpHistogramAlgebra.signedUpperBound(idx, scale);
    if (value == 0.0) {
      assertThat(lo).isEqualTo(0.0);
      assertThat(hi).isEqualTo(0.0);
      return;
    }
    if (value > 0) {
      assertThat(lo)
          .withFailMessage(
              "lower(%s) = %s is not <= %s (scale=%d, idx=%d)", value, lo, value, scale, idx)
          .isLessThanOrEqualTo(value);
      assertThat(hi)
          .withFailMessage(
              "upper(%s) = %s is not > %s (scale=%d, idx=%d)", value, hi, value, scale, idx)
          .isGreaterThan(value);
    } else {
      assertThat(hi)
          .withFailMessage(
              "upper(%s) = %s is not >= %s (scale=%d, idx=%d)", value, hi, value, scale, idx)
          .isGreaterThanOrEqualTo(value);
      assertThat(lo)
          .withFailMessage(
              "lower(%s) = %s is not < %s (scale=%d, idx=%d)", value, lo, value, scale, idx)
          .isLessThan(value);
    }
  }

  private static double randomBiasedFinite(final Random random) {
    final int shape = random.nextInt(4);
    final double magnitude =
        switch (shape) {
          case 0 -> random.nextDouble(); // (0,1) -- unit bucket
          case 1 -> random.nextDouble() * (1 << 20); // small-to-medium
          case 2 -> Math.abs(random.nextGaussian()) * 1e6; // wider spread
          default -> Math.abs(random.nextDouble()) * Double.MAX_VALUE / 4; // huge magnitudes
        };
    return random.nextBoolean() ? magnitude : -magnitude;
  }

  private static List<Double> edgeCaseValues() {
    final List<Double> values = new ArrayList<>();
    values.add(0.0);
    values.add(-0.0);
    values.add(1.0);
    values.add(-1.0);
    values.add(Math.nextDown(1.0));
    values.add(-Math.nextDown(1.0));
    values.add(Double.MIN_VALUE);
    values.add(-Double.MIN_VALUE);
    values.add(Double.MIN_NORMAL);
    values.add(-Double.MIN_NORMAL);
    values.add(Double.MAX_VALUE);
    values.add(-Double.MAX_VALUE);
    for (int k = 1; k <= 100; k++) {
      final double pow = Math.pow(2, k);
      values.add(pow);
      values.add(-pow);
      values.add(Math.nextUp(pow));
      values.add(Math.nextDown(pow));
    }
    return values;
  }
}
