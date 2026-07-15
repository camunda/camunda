/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.sketch;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Arrays;
import org.apache.datasketches.kll.KllDoublesSketch;
import org.apache.datasketches.quantilescommon.QuantileSearchCriteria;

/**
 * The read-facing result of {@link QuantileAggregateFunction}: the observation count, the exact min
 * and max, the estimated value at each requested rank (parallel arrays: {@code values[i]} is the
 * estimate for {@code ranks[i]}, a fraction in [0, 1]), and the merged sketch itself. Values are
 * approximate; count/min/max are exact.
 *
 * <p>The sketch rides along so a server-side caller can answer an arbitrary rank or share query
 * (e.g. the boxplot outlier fence) without re-declaring every rank it might ever need — see {@link
 * #quantile}, {@link #shareAbove} and {@link #outlierStats}. It never reaches the client: {@link
 * JsonIgnore} keeps it out of the JSON this record rides inside (report-row measures), and it is
 * {@code null} for an empty result ({@link #empty}) or after any hypothetical deserialization —
 * every sketch-consuming method degrades gracefully (a declared-rank fallback or {@code NaN}/{@code
 * null}) rather than throwing. Because of that, {@code quantile}/{@code shareAbove}/{@code
 * outlierStats} are meant for server-side callers only (the dashboard read layer) — a client never
 * has the sketch to ask them with.
 *
 * @param count the number of observations folded in
 * @param min the smallest observed value ({@code NaN} when empty)
 * @param max the largest observed value ({@code NaN} when empty)
 * @param ranks the requested quantile ranks, fractions in [0, 1]
 * @param values the estimated value at each rank, parallel to {@code ranks}
 * @param sketch the merged KLL sketch backing this result, or {@code null} when empty/absent
 */
public record QuantileResult(
    long count,
    double min,
    double max,
    double[] ranks,
    double[] values,
    @JsonIgnore KllDoublesSketch sketch) {

  static QuantileResult empty(final double[] ranks) {
    final double[] values = new double[ranks.length];
    Arrays.fill(values, Double.NaN);
    return new QuantileResult(0L, Double.NaN, Double.NaN, ranks.clone(), values, null);
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

  /**
   * The estimated value at an arbitrary {@code rank} (a fraction in [0, 1]), server-side only:
   * reads the merged sketch directly rather than being limited to the declared {@link #ranks}.
   * Falls back to {@link #valueAt} (declared ranks only) when the sketch is absent; {@code NaN} for
   * an undeclared rank without a sketch.
   */
  public double quantile(final double rank) {
    return sketch != null
        ? sketch.getQuantile(rank, QuantileSearchCriteria.INCLUSIVE)
        : valueAt(rank);
  }

  /**
   * The estimated share of observations strictly above {@code value} ({@code 1 - rank(value)}),
   * server-side only. {@code NaN} without a sketch.
   */
  public double shareAbove(final double value) {
    return sketch != null
        ? 1.0 - sketch.getRank(value, QuantileSearchCriteria.INCLUSIVE)
        : Double.NaN;
  }

  /**
   * The boxplot outlier statistics derived from this distribution (Q1/Q3/fence/share/count),
   * server-side only, or {@code null} when the sketch is absent (an empty result, or after a
   * hypothetical JSON round-trip). See {@link OutlierStats} for the fence formula.
   */
  public OutlierStats outlierStats() {
    if (sketch == null) {
      return null;
    }
    final double q1 = quantile(0.25);
    final double q3 = quantile(0.75);
    final double median = quantile(0.5);
    final double fence = q3 + 1.5 * (q3 - q1);
    final double share = shareAbove(fence);
    return new OutlierStats(count, median, q1, q3, fence, share, Math.round(share * count));
  }
}
