/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.sink.algebra;

import io.camunda.analytics.lake.sink.ColumnType;
import java.util.List;

/**
 * Count/sum/min/max over {@code DOUBLE} values as mergeable partials — the double-valued twin of
 * {@link ScalarStatsAlgebra}. State is {@code cnt}/{@code nonfinite_cnt} ({@code LONG}) plus {@code
 * sum}/{@code min}/{@code max} ({@code DOUBLE}) per (dims, window) group, one wide row shared with
 * every other {@link Algebra.PartialsShape#WIDE} measure of the same entity (see {@link
 * Algebra#partialColumns}'s own javadoc for the prefixing convention this mirrors).
 *
 * <h2>Non-finite rule (part of this scheme, fingerprinted)</h2>
 *
 * <p>{@code NaN}/{@code +Infinity}/{@code -Infinity} are never folded into {@code cnt}/{@code
 * sum}/{@code min}/{@code max} — folding a non-finite value into a sum would poison every future
 * merge of that partial row with {@code NaN} forever, and {@code MIN}/{@code MAX} over an infinity
 * would silently pin the bound at an unreachable extreme. Instead each non-finite value increments
 * this algebra's own {@code nonfinite_cnt} column and nothing else. A JSON numeric token can
 * legitimately parse to a non-finite double (e.g. {@code "1e999"} parses to {@code
 * Double.POSITIVE_INFINITY}), so this is a real path, not a defensive no-op. {@link
 * SignedDoubleExpHistogramAlgebra} applies the identical rule to its own bins but reports no column
 * of its own for it — see that class's own javadoc for why the two algebras share this one {@code
 * nonfinite_cnt} column instead of each carrying a copy: a measure that declares only {@link
 * SignedDoubleExpHistogramAlgebra} without this algebra alongside it silently drops the non-finite
 * count with no column to persist it in.
 *
 * <p>Because a group can be touched only by non-finite values (so {@link Accumulator#isEmpty()} is
 * {@code false} while {@code cnt} is still {@code 0}), {@code min}/{@code max} — nullable in the
 * schema this algebra reports — must be written as an explicit {@code NULL} from inside {@link
 * Accumulator#drain}: unlike {@link ScalarStatsAlgebra}, whose {@code min}/{@code max} are declared
 * nullable purely for forward compatibility (never actually observed {@code NULL} today, per its
 * own javadoc), this algebra's {@code min}/{@code max} are genuinely {@code NULL} in that case,
 * which is exactly what {@link Algebra.RowWriter#writeNull} exists to express.
 */
public final class DoubleScalarStatsAlgebra implements Algebra {

  private static final String SCHEME = "dscalar";

  @Override
  public String scheme() {
    return SCHEME;
  }

  @Override
  public Accumulator create() {
    return new DoubleScalarAccumulator();
  }

  @Override
  public PartialsShape shape() {
    return PartialsShape.WIDE;
  }

  @Override
  public ColumnType valueType() {
    return ColumnType.DOUBLE;
  }

  @Override
  public List<PartialColumn> partialColumns(final String measure) {
    return List.of(
        new PartialColumn(measure + "_cnt", false, ColumnType.LONG),
        new PartialColumn(measure + "_sum", false, ColumnType.DOUBLE),
        new PartialColumn(measure + "_min", true, ColumnType.DOUBLE),
        new PartialColumn(measure + "_max", true, ColumnType.DOUBLE),
        new PartialColumn(measure + "_nonfinite_cnt", false, ColumnType.LONG));
  }

  @Override
  public List<String> mergeGroupColumns() {
    return List.of();
  }

  @Override
  public String mergeProjection(final String measure) {
    return "SUM("
        + measure
        + "_cnt) AS "
        + measure
        + "_cnt, SUM("
        + measure
        + "_sum) AS "
        + measure
        + "_sum, MIN("
        + measure
        + "_min) AS "
        + measure
        + "_min, MAX("
        + measure
        + "_max) AS "
        + measure
        + "_max, SUM("
        + measure
        + "_nonfinite_cnt) AS "
        + measure
        + "_nonfinite_cnt";
  }

  @Override
  public String finalizeProjection(final String measure) {
    return measure
        + "_cnt AS "
        + measure
        + "_cnt, "
        + measure
        + "_sum AS "
        + measure
        + "_sum, CASE WHEN "
        + measure
        + "_cnt = 0 THEN NULL ELSE "
        + measure
        + "_sum / "
        + measure
        + "_cnt END AS "
        + measure
        + "_avg, "
        + measure
        + "_min AS "
        + measure
        + "_min, "
        + measure
        + "_max AS "
        + measure
        + "_max, "
        + measure
        + "_nonfinite_cnt AS "
        + measure
        + "_nonfinite_cnt";
  }

  /** Garbage-free hot-side state: 3 {@code double} fields plus 2 {@code long} counters. */
  private static final class DoubleScalarAccumulator implements Accumulator {

    private long cnt;
    private double sum;
    private double min;
    private double max;
    private long nonfiniteCnt;

    @Override
    public void reset() {
      cnt = 0;
      sum = 0;
      min = 0;
      max = 0;
      nonfiniteCnt = 0;
    }

    @Override
    public void addDouble(final double value) {
      if (!Double.isFinite(value)) {
        // Never folded into cnt/sum/min/max -- see class javadoc's "Non-finite rule".
        nonfiniteCnt++;
        return;
      }
      if (cnt == 0) {
        min = value;
        max = value;
      } else {
        if (value < min) {
          min = value;
        }
        if (value > max) {
          max = value;
        }
      }
      cnt++;
      sum += value;
    }

    @Override
    public boolean isEmpty() {
      return cnt == 0 && nonfiniteCnt == 0;
    }

    @Override
    public void drain(final RowWriter writer) {
      writer.beginRow();
      writer.writeLong(0, cnt);
      writer.writeDouble(1, sum);
      if (cnt == 0) {
        // Every folded value (if any) was non-finite -- no finite min/max ever observed. See class
        // javadoc for why this differs from ScalarStatsAlgebra's own min/max nullability note.
        writer.writeNull(2);
        writer.writeNull(3);
      } else {
        writer.writeDouble(2, min);
        writer.writeDouble(3, max);
      }
      writer.writeLong(4, nonfiniteCnt);
      writer.endRow();
      reset();
    }
  }
}
