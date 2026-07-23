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
 * Pure cross-correlation search used by {@code POST /api/tools/screen}: turns each series into its
 * slot-over-slot deltas (first differences), then tests every integer shift in {@code [-3, 3]},
 * reporting the shift with the largest-magnitude Pearson correlation.
 *
 * <p>{@code shift = k} means candidate delta index {@code i + k} is compared against target delta
 * index {@code i} — a positive {@code k} means the candidate's own move at a given slot lines up
 * with the target's move {@code k} slots earlier (the candidate lags the target by {@code k}
 * slots); a negative {@code k} means the candidate leads. Correlation at a shift with fewer than 3
 * overlapping points, or with either side having zero variance over the overlap, is treated as 0
 * (undefined, not a real signal) rather than thrown out — {@code shift = 0} is always evaluable
 * whenever both series have at least 2 points, so a series always gets some reported shift.
 */
public final class ScreenAlgorithm {

  private static final int MIN_SHIFT = -3;
  private static final int MAX_SHIFT = 3;
  private static final int MIN_OVERLAP_POINTS = 3;

  private ScreenAlgorithm() {}

  public static ShiftCorrelation bestShift(
      final List<Double> targetValues, final List<Double> candidateValues) {
    final double[] targetDeltas = deltas(targetValues);
    final double[] candidateDeltas = deltas(candidateValues);

    int bestShift = 0;
    double bestCorrelation = 0;
    double bestAbsCorrelation = -1;
    for (int shift = MIN_SHIFT; shift <= MAX_SHIFT; shift++) {
      final double correlation = correlationAtShift(targetDeltas, candidateDeltas, shift);
      if (Math.abs(correlation) > bestAbsCorrelation) {
        bestAbsCorrelation = Math.abs(correlation);
        bestCorrelation = correlation;
        bestShift = shift;
      }
    }
    return new ShiftCorrelation(bestShift, bestCorrelation);
  }

  private static double correlationAtShift(
      final double[] target, final double[] candidate, final int shift) {
    final List<Double> xs = new ArrayList<>();
    final List<Double> ys = new ArrayList<>();
    for (int i = 0; i < target.length; i++) {
      final int j = i + shift;
      if (j >= 0 && j < candidate.length) {
        xs.add(target[i]);
        ys.add(candidate[j]);
      }
    }
    if (xs.size() < MIN_OVERLAP_POINTS) {
      return 0.0;
    }
    return pearson(xs, ys);
  }

  private static double pearson(final List<Double> xs, final List<Double> ys) {
    final int n = xs.size();
    double meanX = 0;
    double meanY = 0;
    for (int i = 0; i < n; i++) {
      meanX += xs.get(i);
      meanY += ys.get(i);
    }
    meanX /= n;
    meanY /= n;
    double covariance = 0;
    double varX = 0;
    double varY = 0;
    for (int i = 0; i < n; i++) {
      final double dx = xs.get(i) - meanX;
      final double dy = ys.get(i) - meanY;
      covariance += dx * dy;
      varX += dx * dx;
      varY += dy * dy;
    }
    if (varX <= 0 || varY <= 0) {
      return 0.0;
    }
    return covariance / Math.sqrt(varX * varY);
  }

  private static double[] deltas(final List<Double> values) {
    final double[] result = new double[Math.max(0, values.size() - 1)];
    for (int i = 1; i < values.size(); i++) {
      result[i - 1] = values.get(i) - values.get(i - 1);
    }
    return result;
  }

  public record ShiftCorrelation(int shiftSlots, double correlation) {}
}
