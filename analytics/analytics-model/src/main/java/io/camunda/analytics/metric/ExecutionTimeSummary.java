/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.metric;

import java.util.Arrays;
import org.apache.datasketches.kll.KllDoublesSketch;
import org.apache.datasketches.quantilescommon.QuantileSearchCriteria;

/**
 * The composite execution-time accumulator: exact {@code count}/{@code total}/{@code min}/{@code
 * max} <em>and</em> a KLL sketch, fused into one cell. One fold serves the whole duration family —
 * count, total, average ({@code total/count}, derived on read), min, max, and any percentile — so a
 * dataset needs a single meter for what would otherwise be several. This is the "one composite
 * measure, not seven" win for process-instance analytics.
 *
 * <p>Every field merges commutatively/associatively (additive counts, min/max, KLL union), so the
 * summary pre-aggregates across partitions like {@code count}/{@code sum}. Empty uses sentinel
 * min/max so the first observation sets both. A single instance is mutated per cell by the
 * single-writer fold; {@code merge} produces a fresh summary.
 *
 * <p>Designed to grow: a later lifecycle-summary composite adds per-transition counts
 * (activated/completed/terminated) alongside these duration stats, folded from a transition-tagged
 * fact.
 */
public final class ExecutionTimeSummary {

  private long count;
  private long totalMs;
  private long minMs;
  private long maxMs;
  private final KllDoublesSketch sketch;

  public ExecutionTimeSummary(
      final long count,
      final long totalMs,
      final long minMs,
      final long maxMs,
      final KllDoublesSketch sketch) {
    this.count = count;
    this.totalMs = totalMs;
    this.minMs = minMs;
    this.maxMs = maxMs;
    this.sketch = sketch;
  }

  public static ExecutionTimeSummary empty() {
    return new ExecutionTimeSummary(
        0L, 0L, Long.MAX_VALUE, Long.MIN_VALUE, KllDoublesSketch.newHeapInstance());
  }

  /**
   * Folds one observation into this accumulator (mutating), keeping stats and the sketch in step.
   */
  public void record(final long durationMs) {
    count++;
    totalMs += durationMs;
    minMs = Math.min(minMs, durationMs);
    maxMs = Math.max(maxMs, durationMs);
    sketch.update(durationMs);
  }

  /**
   * Folds {@code other} into this summary in place (additive stats + KLL merge) — the mutating
   * counterpart of {@link #merge(ExecutionTimeSummary, ExecutionTimeSummary)} for a summary this
   * caller owns. {@code other} is not modified, so it may be a read-only decoded view; this
   * summary's sketch must be a writable heap sketch.
   */
  public void merge(final ExecutionTimeSummary other) {
    count += other.count;
    totalMs += other.totalMs;
    minMs = Math.min(minMs, other.minMs);
    maxMs = Math.max(maxMs, other.maxMs);
    sketch.merge(other.sketch);
  }

  /**
   * Combines two summaries into a fresh one (additive stats + KLL union), commutative/associative.
   */
  public static ExecutionTimeSummary merge(
      final ExecutionTimeSummary a, final ExecutionTimeSummary b) {
    final KllDoublesSketch merged = KllDoublesSketch.newHeapInstance();
    merged.merge(a.sketch);
    merged.merge(b.sketch);
    return new ExecutionTimeSummary(
        a.count + b.count,
        a.totalMs + b.totalMs,
        Math.min(a.minMs, b.minMs),
        Math.max(a.maxMs, b.maxMs),
        merged);
  }

  /**
   * Upper edges (ms) of the fixed completion-time bands the histogram reports: {@code [≤10s, ≤30s,
   * ≤60s, ≤120s, &gt;120s]} — four edges yield five bands.
   */
  public static final double[] DURATION_BAND_EDGES_MS = {10_000, 30_000, 60_000, 120_000};

  /** The read-facing result at the given ranks; count/min/max exact, percentiles approximate. */
  public ExecutionTimeSummaryResult result(final double[] ranks) {
    if (count == 0) {
      final double[] empty = new double[ranks.length];
      Arrays.fill(empty, Double.NaN);
      return new ExecutionTimeSummaryResult(
          0L, 0.0, 0L, 0L, ranks.clone(), empty, new long[DURATION_BAND_EDGES_MS.length + 1]);
    }
    final double[] quantiles = new double[ranks.length];
    for (int i = 0; i < ranks.length; i++) {
      quantiles[i] = sketch.getQuantile(ranks[i], QuantileSearchCriteria.INCLUSIVE);
    }
    return new ExecutionTimeSummaryResult(
        count, (double) totalMs / count, minMs, maxMs, ranks.clone(), quantiles, bandCounts());
  }

  /**
   * Splits {@code count} across the fixed duration bands using the sketch CDF at {@link
   * #DURATION_BAND_EDGES_MS}: band {@code i} is the estimated number of observations that fell
   * between edge {@code i-1} and edge {@code i} (the last band is everything above the top edge).
   */
  private long[] bandCounts() {
    final double[] cdf = sketch.getCDF(DURATION_BAND_EDGES_MS, QuantileSearchCriteria.INCLUSIVE);
    final long[] bands = new long[cdf.length]; // edges.length + 1
    double prev = 0.0;
    for (int i = 0; i < cdf.length; i++) {
      bands[i] = Math.round(count * (cdf[i] - prev));
      prev = cdf[i];
    }
    return bands;
  }

  public long count() {
    return count;
  }

  public long totalMs() {
    return totalMs;
  }

  public long minMs() {
    return minMs;
  }

  public long maxMs() {
    return maxMs;
  }

  public KllDoublesSketch sketch() {
    return sketch;
  }
}
