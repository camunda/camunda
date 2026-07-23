/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.sink;

/**
 * The translator's only touchpoint with the sink: typed, positional, allocation-free row appends
 * into the currently-filling segment.
 *
 * <p>Usage per row (poll thread only): {@code begin()}, one {@code put*} per column, {@code
 * endRow()}. {@code begin()} may report backpressure (no free segment) — the caller must then pause
 * consumption and retry later; it must never buffer rows elsewhere.
 *
 * <p>Contract: implementations perform no allocation at steady state; {@code putDict} is
 * allocation-free for already-interned values. This covers exactly the per-record path — the {@code
 * begin}/{@code put*}/{@code endRow} sequence a caller runs for every record. It does not cover
 * work upstream of that sequence: {@code io.camunda.analytics.lake.translate.LakeTranslator} builds
 * the {@code vars_json} payload (and drains {@code
 * io.camunda.analytics.lake.state.RocksDbTranslatorState#variablesOf}) once per <em>completed</em>
 * instance, which does allocate — bounded by completion rate, never by per-record rate, and the one
 * explicitly budgeted exception to this interface's own zero-allocation claim.
 */
public interface RowAppender {

  /**
   * Starts a row in the filling segment.
   *
   * @return true if the row can be written; false = ring full (backpressure engaged) — retry after
   *     the gate resumes
   */
  boolean begin();

  RowAppender putLong(int column, long value);

  RowAppender putInt(int column, int value);

  RowAppender putDict(int column, CharSequence value);

  RowAppender putBinary(int column, byte[] src, int offset, int len);

  RowAppender putDouble(int column, double value);

  RowAppender putNull(int column);

  /** Completes the row; may seal the segment (SEGMENT_FULL) as a side effect. */
  void endRow();
}
