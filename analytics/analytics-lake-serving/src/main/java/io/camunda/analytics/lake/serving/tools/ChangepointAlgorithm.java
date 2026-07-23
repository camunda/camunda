/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving.tools;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure, side-effect-free changepoint detection over an already-computed {@link SeriesPoint} list —
 * factored out of {@link ChangepointService} so {@code screen}'s per-candidate {@code movedAt} can
 * reuse the exact same algorithm without going back through SQL.
 *
 * <h2>Algorithm (constants are deliberately fixed, not configurable — tune here if ever needed)
 * </h2>
 *
 * <ol>
 *   <li>Null-valued points (gaps in the aggregation) are dropped; the algorithm needs a dense
 *       series, and a real gap is rare enough over a partials table not to warrant interpolation.
 *   <li>{@code window = max(3, n / 8)} points (integer division) — the symmetric before/after
 *       rolling-mean width.
 *   <li>For every candidate split {@code i} in {@code [window, n - window]}, {@code gap(i) =
 *       |mean(values[i, i+window)) - mean(values[i-window, i))|}. {@code at} = the split with the
 *       largest gap; too few points for any candidate (n &lt; 2 * window) yields {@link Shape#NONE}
 *       with zero confidence.
 *   <li>{@code shape = STEP} when the single largest consecutive-point delta anywhere in the series
 *       is at least 60% of the best split's gap (the whole gap is concentrated in one step);
 *       otherwise {@code DRIFT} (the change is spread across several steps).
 *   <li>{@code shape = NONE} when the best gap is less than 15% of the series' overall mean
 *       magnitude ({@code |mean(all values)|}) — i.e. too small relative to the series' own scale
 *       to call out. When the overall mean is (near) zero, that relative test is meaningless, so
 *       this case instead falls back to an absolute epsilon ({@code 1e-9}): any non-negligible gap
 *       counts as a real change.
 *   <li>{@code confidence = clamp(gap / (3 * populationStdDev(all values)), 0, 1)} — the best gap
 *       expressed in (saturating) standard-deviation units, so a change of 3+ standard deviations
 *       reports full confidence. When the series has zero variance, confidence is 1.0 if the gap is
 *       non-negligible (an exact step off a perfectly flat baseline) and 0.0 otherwise.
 * </ol>
 */
public final class ChangepointAlgorithm {

  private static final double STEP_CONCENTRATION_RATIO = 0.60;
  private static final double NONE_RELATIVE_THRESHOLD = 0.15;
  private static final double ZERO_SCALE_EPSILON = 1e-9;
  private static final double CONFIDENCE_STD_DEV_SPAN = 3.0;

  private ChangepointAlgorithm() {}

  public static Outcome detect(final List<SeriesPoint> points) {
    final List<SeriesPoint> dense = new ArrayList<>();
    for (final SeriesPoint point : points) {
      if (point.value() != null) {
        dense.add(point);
      }
    }
    final int n = dense.size();
    final int window = Math.max(3, n / 8);
    if (n < 2 * window || window <= 0) {
      return new Outcome(null, Shape.NONE, 0.0, null, null);
    }

    int bestIndex = -1;
    double bestGap = -1;
    double bestBefore = 0;
    double bestAfter = 0;
    for (int i = window; i <= n - window; i++) {
      final double before = mean(dense, i - window, i);
      final double after = mean(dense, i, i + window);
      final double gap = Math.abs(after - before);
      if (gap > bestGap) {
        bestGap = gap;
        bestIndex = i;
        bestBefore = before;
        bestAfter = after;
      }
    }

    double maxStepDelta = 0;
    for (int i = 1; i < n; i++) {
      maxStepDelta =
          Math.max(maxStepDelta, Math.abs(dense.get(i).value() - dense.get(i - 1).value()));
    }

    final double overallMean = mean(dense, 0, n);
    final double scale = Math.abs(overallMean);
    final boolean negligible =
        scale > ZERO_SCALE_EPSILON
            ? bestGap < NONE_RELATIVE_THRESHOLD * scale
            : bestGap < ZERO_SCALE_EPSILON;
    if (negligible) {
      return new Outcome(null, Shape.NONE, 0.0, bestBefore, bestAfter);
    }

    final Shape shape =
        maxStepDelta >= STEP_CONCENTRATION_RATIO * bestGap ? Shape.STEP : Shape.DRIFT;
    final double stdDev = populationStdDev(dense, overallMean);
    final double confidence =
        stdDev > ZERO_SCALE_EPSILON
            ? Math.min(1.0, bestGap / (CONFIDENCE_STD_DEV_SPAN * stdDev))
            : 1.0;
    return new Outcome(dense.get(bestIndex).t(), shape, confidence, bestBefore, bestAfter);
  }

  private static double mean(
      final List<SeriesPoint> points, final int fromInclusive, final int toExclusive) {
    double sum = 0;
    for (int i = fromInclusive; i < toExclusive; i++) {
      sum += points.get(i).value();
    }
    return sum / (toExclusive - fromInclusive);
  }

  private static double populationStdDev(final List<SeriesPoint> points, final double mean) {
    double sumSquares = 0;
    for (final SeriesPoint point : points) {
      final double diff = point.value() - mean;
      sumSquares += diff * diff;
    }
    return Math.sqrt(sumSquares / points.size());
  }

  public enum Shape {
    STEP,
    DRIFT,
    NONE
  }

  public record Outcome(String at, Shape shape, double confidence, Double before, Double after) {}
}
