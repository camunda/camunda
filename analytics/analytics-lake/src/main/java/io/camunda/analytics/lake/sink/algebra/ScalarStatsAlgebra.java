/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.sink.algebra;

import java.util.List;

/**
 * Count/sum/min/max as mergeable partials. State is exactly 4 {@code long}s per (dims, window)
 * group, one wide row shared with every other scalar-stats measure of the same entity — see {@link
 * Algebra#partialColumns}'s javadoc for why this algebra's columns are prefixed with the measure
 * name while {@link ExpHistogramAlgebra}'s are not.
 *
 * <p>{@code min}/{@code max} are declared {@link Algebra.PartialColumn#nullable() nullable} in the
 * schema this algebra reports, even though an {@link Accumulator} is only ever {@link
 * Accumulator#drain drained} while non-empty (so a stored row's {@code cnt} is always &gt;= 1 and
 * its {@code min}/{@code max} are always populated in practice today): a future writer that chooses
 * to emit a zero-count row for a group with no observations in a window (e.g. to keep a dense time
 * grid without gaps) would have nowhere else to put "no min/max exists yet" but {@code NULL}, and
 * the merge/finalize SQL below already treats {@code cnt = 0} as the marker for that case.
 */
public final class ScalarStatsAlgebra implements Algebra {

  private static final String SCHEME = "scalar";

  @Override
  public String scheme() {
    return SCHEME;
  }

  @Override
  public Accumulator create() {
    return new ScalarAccumulator();
  }

  @Override
  public PartialsShape shape() {
    return PartialsShape.WIDE;
  }

  @Override
  public List<PartialColumn> partialColumns(final String measure) {
    return List.of(
        new PartialColumn(measure + "_cnt", false),
        new PartialColumn(measure + "_sum", false),
        new PartialColumn(measure + "_min", true),
        new PartialColumn(measure + "_max", true));
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
        + "_max";
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
        + "_cnt = 0 THEN NULL ELSE CAST("
        + measure
        + "_sum AS DOUBLE) / "
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
        + "_max";
  }

  /** Garbage-free hot-side state: 4 plain {@code long} fields, no boxing, no arrays. */
  private static final class ScalarAccumulator implements Accumulator {

    private long cnt;
    private long sum;
    private long min;
    private long max;

    @Override
    public void reset() {
      cnt = 0;
      sum = 0;
      min = 0;
      max = 0;
    }

    @Override
    public void add(final long value) {
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
      return cnt == 0;
    }

    @Override
    public void drain(final RowWriter writer) {
      writer.beginRow();
      writer.writeLong(0, cnt);
      writer.writeLong(1, sum);
      writer.writeLong(2, min);
      writer.writeLong(3, max);
      writer.endRow();
      reset();
    }
  }
}
