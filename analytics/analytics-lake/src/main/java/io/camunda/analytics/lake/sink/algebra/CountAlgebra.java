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
 * A plain row count as mergeable partial state: one {@code long} per (dims, window) group, one
 * unprefixed {@code cnt} column shared with every other measure's own {@code WIDE} columns on the
 * {@code _metrics} row (see {@code io.camunda.analytics.lake.metrics.EntityMetrics.Builder#count()}
 * for the declaration entry point).
 *
 * <h2>Why unprefixed, unlike {@link ScalarStatsAlgebra}</h2>
 *
 * <p>{@link ScalarStatsAlgebra}'s columns are prefixed with the measure name because several
 * scalar-stats measures can coexist on the same {@code _metrics} row. A declaration's {@code
 * count()} is not a measure — it has no raw column, and a declaration may only ever count once (see
 * {@code EntityMetrics.Builder#count()}) — so its one column needs no disambiguating prefix.
 *
 * <h2>Why {@link Accumulator#add(long)} ignores its argument</h2>
 *
 * <p>Every other algebra folds a measure's raw value; a count has none — it increments once per row
 * that reaches a group, regardless of any measure's own nullness (see {@code MetricsRider}/{@code
 * io.camunda.analytics.lake.metrics.PollFedRider}'s own "no null-skip semantics" javadoc for where
 * the caller enforces the unconditional-per-row contract this algebra's own {@link
 * Accumulator#add(long)} relies on).
 */
public final class CountAlgebra implements Algebra {

  private static final String SCHEME = "count";
  private static final String COLUMN = "cnt";

  @Override
  public String scheme() {
    return SCHEME;
  }

  @Override
  public Accumulator create() {
    return new CountAccumulator();
  }

  @Override
  public PartialsShape shape() {
    return PartialsShape.WIDE;
  }

  @Override
  public List<PartialColumn> partialColumns(final String measure) {
    return List.of(new PartialColumn(COLUMN, false));
  }

  @Override
  public List<String> mergeGroupColumns() {
    return List.of();
  }

  @Override
  public String mergeProjection(final String measure) {
    return "SUM(" + COLUMN + ") AS " + COLUMN;
  }

  @Override
  public String finalizeProjection(final String measure) {
    return COLUMN + " AS " + COLUMN;
  }

  /** Garbage-free hot-side state: one plain {@code long} field, no boxing, no arrays. */
  private static final class CountAccumulator implements Accumulator {

    private long cnt;

    @Override
    public void reset() {
      cnt = 0;
    }

    @Override
    public void add(final long value) {
      // value is deliberately ignored -- see class javadoc.
      cnt++;
    }

    @Override
    public boolean isEmpty() {
      return cnt == 0;
    }

    @Override
    public void drain(final RowWriter writer) {
      writer.beginRow();
      writer.writeLong(0, cnt);
      writer.endRow();
      reset();
    }
  }
}
