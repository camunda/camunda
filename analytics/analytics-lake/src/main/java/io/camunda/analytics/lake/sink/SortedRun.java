/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.sink;

import java.util.List;

/**
 * A sealed segment's rows in sort-key order — the flush side's read view, produced by the sorter
 * (permutation + gather into reused scratch) and consumed by riders and encoders.
 *
 * <p>Contract: valid only until the segment is released back to the ring; implementations back this
 * with reused scratch buffers, so consumers must never retain a reference across seals. Dictionary
 * codes are resolved to values here ({@link #stringAt}) so encoders never see interner internals.
 * Indexes are positions in sorted order, {@code 0 <= i < size()}.
 */
public interface SortedRun {

  TableSchema schema();

  int size();

  boolean isNullAt(int column, int i);

  long longAt(int column, int i);

  int intAt(int column, int i);

  /** Dictionary column resolved to its string value (cold path; may box, that's budgeted). */
  String stringAt(int column, int i);

  int binaryLength(int column, int i);

  int copyBinaryTo(int column, int i, byte[] dst, int dstOffset);

  double doubleAt(int column, int i);

  /**
   * Contiguous index ranges per family day, ascending. The sorter orders by (family day, then the
   * schema's sort key columns) — day is always the implicit leading component, mirroring the future
   * partition spec — so rows of one day are adjacent by construction and the router hands each
   * range to its file.
   */
  List<DayRange> dayRanges();

  /**
   * @param epochDay family day as epoch days (UTC)
   * @param fromIndex first sorted index of the range (inclusive)
   * @param toIndex last sorted index of the range (exclusive)
   */
  record DayRange(long epochDay, int fromIndex, int toIndex) {}
}
