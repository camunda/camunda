/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.sink.algebra;

import java.util.Arrays;
import java.util.List;

/**
 * A base-2, log-linear ("exponential") histogram: constant <em>relative</em> error, computed with
 * pure bit operations (no {@code Math.log}), storing one denormalized {@code (bin_lo, bin_hi, cnt)}
 * row per non-empty bin rather than a fixed-width array a reader would need this class's own
 * bucketing math to interpret (see {@link Algebra}'s own javadoc for why).
 *
 * <h2>Bucketing scheme</h2>
 *
 * <p>Below {@code 2^scale}, every bin is unit-width: bin {@code v} covers exactly the integer
 * {@code v} (values in this codebase — durations, counts — are never negative, so {@code v <= 0}
 * collapses into bin {@code 0}, {@code [0, 1)}). At and above {@code 2^scale}, bins double in width
 * every {@code 2^scale} consecutive indices: within one power-of-two exponent range {@code [2^e,
 * 2^(e+1))}, the top {@code scale} bits below the leading bit select which of {@code 2^scale}
 * equal-width sub-bins a value falls into, so the relative width of any bin at or above {@code
 * 2^scale} is at most {@code 2^-scale} (verified by {@code ExpHistogramBoundaryPropertyTest}).
 *
 * <p>{@code index()}/{@code lowerBound()}/{@code upperBound()} were specified up front and are
 * implemented here exactly as specified: {@code ExpHistogramBoundaryPropertyTest} property-tests
 * them (random values, every power-of-two edge, {@code Long.MAX_VALUE / 4}) against the invariant
 * {@code lowerBound(index(v)) <= v < upperBound(index(v))} for all {@code v >= 0}, plus
 * monotonicity and the relative-width bound above — no off-by-one was found; the formulas as given
 * are correct.
 *
 * <h2>State and reset cost</h2>
 *
 * <p>Bin counts live in one {@code int[]} sized once at construction for values up to {@code 2^62}
 * at this instance's scale (a few hundred entries at scale 3; see {@link #maxBins}). {@link
 * Accumulator#reset()} does not scan or clear the whole array — it tracks a dirty high-water mark
 * (the highest bin index touched since the last reset) and only clears {@code [0, dirtyHigh)},
 * which is cheap whenever the touched bins cluster away from the top of the range, as real duration
 * distributions do.
 */
public final class ExpHistogramAlgebra implements Algebra {

  /**
   * Values are never tracked past this; sizes the bin-count array (see {@link #maxBins}). Not an
   * arbitrary round number: the bin starting at {@code 2^62} would need an upper boundary of {@code
   * 2^63}, which overflows a signed 64-bit long into {@code Long.MIN_VALUE} (silently, without this
   * cap) — {@code ExpHistogramBoundaryPropertyTest} found exactly this overflow when it first
   * sampled random values across the full unbounded {@code long} range, which is what pinned this
   * domain limit down precisely rather than leaving it as a vague "large values" caveat. {@link
   * #add} on a value at or beyond this bound throws {@link ArrayIndexOutOfBoundsException} — a loud
   * failure was preferred over silently wrapping or truncating an out-of-domain value.
   */
  private static final long MAX_TRACKED_VALUE = (1L << 62) - 1;

  private final int scale;
  private final String scheme;
  private final int maxBins;

  public ExpHistogramAlgebra(final int scale) {
    if (scale < 0) {
      throw new IllegalArgumentException("scale must be >= 0, got " + scale);
    }
    this.scale = scale;
    scheme = "exp2ll-" + scale;
    maxBins = index(MAX_TRACKED_VALUE, scale) + 1;
  }

  /**
   * Maps a value to its bin index. Pure bit ops: no {@code Math.log}, no floating point. See the
   * class javadoc for the bucketing scheme this implements.
   */
  public static int index(final long value, final int scale) {
    if (value <= 0) {
      return 0;
    }
    if (value < (1L << scale)) {
      return (int) value;
    }
    final int e = 63 - Long.numberOfLeadingZeros(value);
    final int shift = e - scale;
    return ((e - scale + 1) << scale) + (int) ((value >>> shift) - (1L << scale));
  }

  /** Inclusive lower boundary of bin {@code idx}. See the class javadoc for the derivation. */
  public static long lowerBound(final int idx, final int scale) {
    final int block = idx >>> scale;
    if (block == 0) {
      return idx;
    }
    final int mantissa = idx & ((1 << scale) - 1);
    return ((1L << scale) + mantissa) << (block - 1);
  }

  /** Exclusive upper boundary of bin {@code idx}: always exactly {@code lowerBound(idx + 1)}. */
  public static long upperBound(final int idx, final int scale) {
    return lowerBound(idx + 1, scale);
  }

  @Override
  public String scheme() {
    return scheme;
  }

  @Override
  public Accumulator create() {
    return new HistogramAccumulator(scale, maxBins);
  }

  @Override
  public PartialsShape shape() {
    return PartialsShape.TALL;
  }

  @Override
  public List<PartialColumn> partialColumns(final String measure) {
    // unprefixed: the physical `_hist` table disambiguates measures via its own `measure` column
    // (and distinct schemes via its own `scheme` column) rather than per-measure column names — see
    // this method's javadoc on Algebra for why scalar stats and histograms differ here.
    return List.of(
        new PartialColumn("bin_lo", false),
        new PartialColumn("bin_hi", false),
        new PartialColumn("cnt", false));
  }

  @Override
  public List<String> mergeGroupColumns() {
    // bin_lo/bin_hi identify which bin a row is; merging must not collapse distinct bins together.
    return List.of("bin_lo", "bin_hi");
  }

  @Override
  public String mergeProjection(final String measure) {
    return "SUM(cnt) AS cnt";
  }

  @Override
  public String finalizeProjection(final String measure) {
    throw new UnsupportedOperationException(
        "ExpHistogramAlgebra's finalize is a percentile extraction — a cumulative scan across every"
            + " bin of one (dims, window, measure, scheme) group — which cannot be expressed as a"
            + " flat per-row projection alongside other measures' own projections; use"
            + " finalizeQuery(...)/finalizeMacroSql(...) instead.");
  }

  /**
   * Full, standalone SQL text estimating the {@code quantile}-th percentile (bound as the query's
   * one positional {@code ?} parameter, a value in {@code [0, 1]}) per {@code groupByColumns} group
   * — the identity of one histogram distribution (dims + window + measure + scheme; deliberately
   * <b>not</b> including {@code bin_lo}/{@code bin_hi}, which this query itself scans across within
   * each group).
   *
   * <p>Implementation: a cumulative-count walk over bins ordered by {@code bin_lo}, reporting the
   * midpoint of the first bin whose running count reaches {@code quantile * total_count}. The
   * midpoint (rather than {@code bin_lo} or {@code bin_hi}) is reported because a bin only bounds
   * the true value, never pins it down exactly (that is the entire point of storing partials at
   * bounded relative error instead of exact values); the midpoint minimizes the worst-case absolute
   * error within the bin relative to either edge.
   */
  public String finalizeQuery(final String sourceTable, final List<String> groupByColumns) {
    return finalizeSelect(sourceTable, groupByColumns, "?");
  }

  /**
   * Same percentile-extraction query as {@link #finalizeQuery}, packaged as a DuckDB {@code CREATE
   * MACRO ... AS TABLE} statement so callers can invoke it repeatedly (e.g. once per requested
   * percentile) as {@code SELECT * FROM <macroName>(0.5)} without re-preparing a statement each
   * time.
   */
  public String finalizeMacroSql(
      final String macroName, final String sourceTable, final List<String> groupByColumns) {
    return "CREATE OR REPLACE MACRO "
        + macroName
        + "(q) AS TABLE "
        + finalizeSelect(sourceTable, groupByColumns, "q");
  }

  private String finalizeSelect(
      final String sourceTable, final List<String> groupByColumns, final String quantileParam) {
    final String groupCols = String.join(", ", groupByColumns);
    final String partitionBy = groupByColumns.isEmpty() ? "" : "PARTITION BY " + groupCols + " ";
    return "WITH ranked AS (SELECT "
        + (groupCols.isEmpty() ? "" : groupCols + ", ")
        + "bin_lo, bin_hi, cnt, "
        + "SUM(cnt) OVER ("
        + partitionBy
        + "ORDER BY bin_lo ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS cum_cnt, "
        + "SUM(cnt) OVER ("
        + (partitionBy.isEmpty() ? "" : partitionBy.trim())
        + ") AS total_cnt FROM "
        + sourceTable
        + ") SELECT "
        + (groupCols.isEmpty() ? "" : groupCols + ", ")
        + "MIN(CASE WHEN cum_cnt >= total_cnt * "
        + quantileParam
        + " THEN (bin_lo + bin_hi) / 2.0 END) AS percentile FROM ranked"
        + (groupCols.isEmpty() ? "" : " GROUP BY " + groupCols);
  }

  /**
   * Garbage-free hot-side state: one {@code int[]} of bin counts, sized once at construction, plus
   * a dirty high-water mark so {@link #reset()} need not scan the whole array (see the enclosing
   * class's javadoc).
   */
  private static final class HistogramAccumulator implements Accumulator {

    private final int scale;
    private final int[] bins;
    private int dirtyHigh; // exclusive upper bound of the touched region; 0 == empty

    HistogramAccumulator(final int scale, final int maxBins) {
      this.scale = scale;
      bins = new int[maxBins];
    }

    @Override
    public void reset() {
      if (dirtyHigh > 0) {
        Arrays.fill(bins, 0, dirtyHigh, 0);
        dirtyHigh = 0;
      }
    }

    @Override
    public void add(final long value) {
      final int idx = index(value, scale);
      bins[idx]++;
      if (idx + 1 > dirtyHigh) {
        dirtyHigh = idx + 1;
      }
    }

    @Override
    public boolean isEmpty() {
      return dirtyHigh == 0;
    }

    @Override
    public void drain(final RowWriter writer) {
      for (int idx = 0; idx < dirtyHigh; idx++) {
        final int cnt = bins[idx];
        if (cnt != 0) {
          writer.beginRow();
          writer.writeLong(0, lowerBound(idx, scale));
          writer.writeLong(1, upperBound(idx, scale));
          writer.writeLong(2, cnt);
          writer.endRow();
        }
      }
      reset();
    }
  }
}
