/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.sink.algebra;

import io.camunda.analytics.lake.sink.ColumnType;
import java.util.Arrays;
import java.util.List;

/**
 * A sign-mirrored, base-2 log-linear ("exponential") histogram over signed {@code double} values —
 * the double-valued twin of {@link ExpHistogramAlgebra}, covering the full signed range rather than
 * non-negative longs. Scheme {@code "dexp2ll-<scale>"}.
 *
 * <h2>Bucketing scheme (frozen — this is the wire contract, not an implementation detail)</h2>
 *
 * <p>Every value maps to a signed bin index {@code sign(v) * unsignedIndex(|v|)}, computed with
 * pure bit operations on the double's own IEEE 754 representation ({@link Math#getExponent(double)}
 * plus the raw mantissa bits) — no {@code Math.log}, no allocation:
 *
 * <ul>
 *   <li>{@code v == 0} (positive or negative zero): its own dedicated zero bucket, signed index
 *       {@code 0}, reported as the degenerate point bin {@code [0.0, 0.0]}.
 *   <li>{@code 0 < |v| < 1}: collapses to the sign's <b>unit bucket</b>, {@code unsignedIndex == 1}
 *       — positive bucket bounds {@code [0.0, 1.0)}, negative bucket bounds {@code (-1.0, 0.0]}.
 *       Every subnormal double has magnitude {@code < 2^-1022 < 1}, so subnormals collapse into
 *       this bucket automatically, with no special-casing needed.
 *   <li>{@code |v| >= 1}: {@code e = Math.getExponent(|v|)} (the double's own binary exponent,
 *       {@code >= 0} whenever {@code |v| >= 1}); {@code block = e + 1} (block {@code >= 1},
 *       reserved distinct from the unit bucket's index {@code 1}); the top {@code scale} bits of
 *       the 52-bit mantissa ({@code mantissaBits >>> (52 - scale)}) select which of {@code 2^scale}
 *       equal-width sub-bins within that power-of-two block the value falls into — identical in
 *       spirit to {@link ExpHistogramAlgebra}'s own long-domain bit-shift, just reading the
 *       mantissa the IEEE 754 encoding already carries instead of shifting the raw integer value.
 *       {@code unsignedIndex = 1 + (block &lt;&lt; scale) + topBits}, guaranteed {@code >= 2},
 *       never colliding with the unit bucket.
 * </ul>
 *
 * <p>Boundaries: for {@code unsignedIndex == 1} (unit bucket), {@code [0.0, 1.0)}. For {@code
 * unsignedIndex >= 2}, let {@code raw = unsignedIndex - 1}, {@code block = raw >>> scale}, {@code
 * top = raw & ((1 &lt;&lt; scale) - 1)}, {@code e = block - 1}; then {@code lowerBound =
 * Math.scalb((double) ((1L &lt;&lt; scale) + top), e - scale)} and {@code upperBound(idx) =
 * lowerBound(idx + 1)} (same recursive relation {@link ExpHistogramAlgebra} uses). {@link
 * Math#scalb} — {@code x * 2^n} computed directly on the exponent, never by shifting a magnitude —
 * is what lets this algebra cover the full double exponent range without the {@code
 * MAX_TRACKED_VALUE} domain cap {@link ExpHistogramAlgebra} needs for its 64-bit integer version:
 * the top bin's {@code upperBound} naturally saturates to {@link Double#POSITIVE_INFINITY} for a
 * value near {@link Double#MAX_VALUE} (the true next power of two, {@code 2^1024}, overflows a
 * finite double) rather than needing an explicit thrown-at-the-boundary cap.
 *
 * <p>Signed bounds mirror the unsigned ones around zero: for a positive signed index {@code idx},
 * {@code [unsignedLowerBound(idx), unsignedUpperBound(idx))} — {@code lower <= v < upper}, exactly
 * {@link ExpHistogramAlgebra}'s own convention. For a negative signed index {@code idx}, {@code
 * (-unsignedUpperBound(-idx), -unsignedLowerBound(-idx)]} — {@code lower < v <= upper}, the mirror
 * image (matches the {@code (-1, 0]} convention documented above for the negative unit bucket).
 * Monotone in {@code v} by construction (more negative values get more negative signed indices, and
 * more positive values get more positive ones), and the relative width of any bin at or above
 * {@code |v| == 1} is at most {@code 2^-scale}, exactly {@link ExpHistogramAlgebra}'s own bound
 * (property- tested by {@code SignedDoubleExpHistogramBoundaryPropertyTest}).
 *
 * <h2>Non-finite rule (part of this scheme, fingerprinted)</h2>
 *
 * <p>{@code NaN}/{@code +Infinity}/{@code -Infinity} are never folded into any bin — see {@link
 * DoubleScalarStatsAlgebra}'s own javadoc for the full rule and why the shared {@code
 * nonfinite_cnt} counter lives on that algebra's row rather than this one's: a group touched only
 * by non-finite values simply drains no rows here at all (this accumulator's {@link
 * Accumulator#isEmpty()} stays {@code true}), which is correct — there is no bin to report, and the
 * count is (when {@link DoubleScalarStatsAlgebra} is declared alongside this algebra for the same
 * measure, as every caller in this codebase does) already durable there.
 *
 * <h2>Scale domain</h2>
 *
 * <p>{@code scale} must be in {@code [0, 52]} — the mantissa is exactly 52 bits wide, so a larger
 * scale would shift by a negative amount.
 */
public final class SignedDoubleExpHistogramAlgebra implements Algebra, PercentileHistogramAlgebra {

  private static final long MANTISSA_BITS = 52L;
  private static final long MANTISSA_MASK = (1L << MANTISSA_BITS) - 1;

  /** Signed index of the unit bucket for a magnitude in {@code (0, 1)}; never {@code 0}. */
  private static final int UNIT_UNSIGNED_INDEX = 1;

  private final int scale;
  private final String scheme;
  private final int maxUnsignedIndex; // inclusive upper bound of the unsigned index domain

  public SignedDoubleExpHistogramAlgebra(final int scale) {
    if (scale < 0 || scale > MANTISSA_BITS) {
      throw new IllegalArgumentException(
          "scale must be in [0, "
              + MANTISSA_BITS
              + "] (the double mantissa's own bit width), got "
              + scale);
    }
    this.scale = scale;
    scheme = "dexp2ll-" + scale;
    maxUnsignedIndex = unsignedIndex(Double.MAX_VALUE, scale);
  }

  /**
   * Maps a non-negative, finite magnitude to its unsigned bin index. Pure bit ops on the double's
   * own IEEE 754 representation — no {@code Math.log}. See the class javadoc for the scheme.
   */
  static int unsignedIndex(final double magnitude, final int scale) {
    if (magnitude < 1.0) {
      return UNIT_UNSIGNED_INDEX; // covers 0 too; v == 0 itself is handled by signedIndex() below
    }
    final int e = Math.getExponent(magnitude);
    final int block = e + 1;
    final long mantissaBits = Double.doubleToRawLongBits(magnitude) & MANTISSA_MASK;
    final int top = (int) (mantissaBits >>> (MANTISSA_BITS - scale));
    return 1 + (block << scale) + top;
  }

  /** Inclusive lower boundary of unsigned bin {@code idx}. See the class javadoc. */
  static double unsignedLowerBound(final int idx, final int scale) {
    if (idx <= UNIT_UNSIGNED_INDEX) {
      return 0.0;
    }
    final int raw = idx - 1;
    final int block = raw >>> scale;
    final int top = raw & ((1 << scale) - 1);
    final int e = block - 1;
    return Math.scalb((double) ((1L << scale) + top), e - scale);
  }

  /** Exclusive upper boundary of unsigned bin {@code idx}: always {@code lowerBound(idx + 1)}. */
  static double unsignedUpperBound(final int idx, final int scale) {
    if (idx < UNIT_UNSIGNED_INDEX) {
      return 0.0;
    }
    if (idx == UNIT_UNSIGNED_INDEX) {
      return 1.0;
    }
    return unsignedLowerBound(idx + 1, scale);
  }

  /**
   * Maps a signed, finite {@code double} to its signed bin index: {@code sign(v) *
   * unsignedIndex(|v|)}, with {@code v == 0} (either signed zero) mapping to {@code 0} directly —
   * see the class javadoc.
   */
  static int signedIndex(final double value, final int scale) {
    if (value == 0.0) {
      return 0;
    }
    final int unsigned = unsignedIndex(Math.abs(value), scale);
    return value > 0 ? unsigned : -unsigned;
  }

  /** Inclusive-or-exclusive (sign-dependent) lower boundary of signed bin {@code idx}. */
  static double signedLowerBound(final int idx, final int scale) {
    if (idx == 0) {
      return 0.0;
    }
    return idx > 0 ? unsignedLowerBound(idx, scale) : -unsignedUpperBound(-idx, scale);
  }

  /** Exclusive-or-inclusive (sign-dependent) upper boundary of signed bin {@code idx}. */
  static double signedUpperBound(final int idx, final int scale) {
    if (idx == 0) {
      return 0.0;
    }
    return idx > 0 ? unsignedUpperBound(idx, scale) : -unsignedLowerBound(-idx, scale);
  }

  @Override
  public String scheme() {
    return scheme;
  }

  @Override
  public Accumulator create() {
    return new SignedHistogramAccumulator(scale, maxUnsignedIndex);
  }

  @Override
  public PartialsShape shape() {
    return PartialsShape.TALL;
  }

  @Override
  public ColumnType valueType() {
    return ColumnType.DOUBLE;
  }

  @Override
  public List<PartialColumn> partialColumns(final String measure) {
    // unprefixed: the physical `_hist` table disambiguates measures via its own `measure` column,
    // exactly like ExpHistogramAlgebra -- see that class's own javadoc for why.
    return List.of(
        new PartialColumn("bin_lo", false, ColumnType.DOUBLE),
        new PartialColumn("bin_hi", false, ColumnType.DOUBLE),
        new PartialColumn("cnt", false, ColumnType.LONG));
  }

  @Override
  public List<String> mergeGroupColumns() {
    return List.of("bin_lo", "bin_hi");
  }

  @Override
  public String mergeProjection(final String measure) {
    return "SUM(cnt) AS cnt";
  }

  @Override
  public String finalizeProjection(final String measure) {
    throw new UnsupportedOperationException(
        "SignedDoubleExpHistogramAlgebra's finalize is a percentile extraction -- a cumulative scan"
            + " across every bin of one (dims, window, measure, scheme) group -- which cannot be"
            + " expressed as a flat per-row projection alongside other measures' own projections;"
            + " use finalizeQuery(...)/finalizeMacroSql(...) instead.");
  }

  /** Same idea as {@link ExpHistogramAlgebra#finalizeQuery}; ordering by {@code bin_lo} still. */
  public String finalizeQuery(final String sourceTable, final List<String> groupByColumns) {
    return finalizeSelect(sourceTable, groupByColumns, "?");
  }

  /** Same idea as {@link ExpHistogramAlgebra#finalizeMacroSql}. */
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
   * Garbage-free hot-side state: one {@code int[]} of bin counts covering the full signed domain
   * ({@code storageIndex = signedIndex + zeroOffset}), sized once at construction, plus a two-sided
   * dirty range so {@link #reset()} need not scan the whole array — the signed twin of {@link
   * ExpHistogramAlgebra}'s own single-sided high-water mark.
   */
  private static final class SignedHistogramAccumulator implements Accumulator {

    private final int scale;
    private final int zeroOffset;
    private final int[] bins;
    private int dirtyLow = -1; // -1 == empty; inclusive lowest touched storage index otherwise
    private int dirtyHigh = -1; // inclusive highest touched storage index

    SignedHistogramAccumulator(final int scale, final int maxUnsignedIndex) {
      this.scale = scale;
      zeroOffset = maxUnsignedIndex;
      bins = new int[2 * maxUnsignedIndex + 1];
    }

    @Override
    public void reset() {
      if (dirtyLow >= 0) {
        Arrays.fill(bins, dirtyLow, dirtyHigh + 1, 0);
        dirtyLow = -1;
        dirtyHigh = -1;
      }
    }

    @Override
    public void addDouble(final double value) {
      if (!Double.isFinite(value)) {
        // Never folded into a bin -- see class javadoc's "Non-finite rule".
        return;
      }
      final int storageIndex = signedIndex(value, scale) + zeroOffset;
      bins[storageIndex]++;
      if (dirtyLow < 0) {
        dirtyLow = storageIndex;
        dirtyHigh = storageIndex;
      } else if (storageIndex < dirtyLow) {
        dirtyLow = storageIndex;
      } else if (storageIndex > dirtyHigh) {
        dirtyHigh = storageIndex;
      }
    }

    @Override
    public boolean isEmpty() {
      return dirtyLow < 0;
    }

    @Override
    public void drain(final RowWriter writer) {
      for (int storageIndex = dirtyLow; storageIndex <= dirtyHigh; storageIndex++) {
        final int cnt = bins[storageIndex];
        if (cnt != 0) {
          final int signedIdx = storageIndex - zeroOffset;
          writer.beginRow();
          writer.writeDouble(0, signedLowerBound(signedIdx, scale));
          writer.writeDouble(1, signedUpperBound(signedIdx, scale));
          writer.writeLong(2, cnt);
          writer.endRow();
        }
      }
      reset();
    }
  }
}
