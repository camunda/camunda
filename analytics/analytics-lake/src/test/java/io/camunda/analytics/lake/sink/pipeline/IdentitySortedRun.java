/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.sink.pipeline;

import io.camunda.analytics.lake.sink.ColumnVector;
import io.camunda.analytics.lake.sink.Segment;
import io.camunda.analytics.lake.sink.SortedRun;
import io.camunda.analytics.lake.sink.TableSchema;
import java.util.List;

/**
 * A {@link SortedRun} that performs no reordering — a thin, direct read view over a sealed {@link
 * Segment}, tagged with a single fixed family day. Sufficient for control-path tests: they exercise
 * trigger/window/backpressure behavior, not the (separately built) sort/day-routing logic, so
 * preserving append order lets tests assert on exact row sequences.
 */
final class IdentitySortedRun implements SortedRun {

  private final Segment segment;
  private final long epochDay;

  IdentitySortedRun(final Segment segment, final long epochDay) {
    this.segment = segment;
    this.epochDay = epochDay;
  }

  @Override
  public TableSchema schema() {
    return segment.schema();
  }

  @Override
  public int size() {
    return segment.size();
  }

  @Override
  public boolean isNullAt(final int column, final int i) {
    return segment.vector(column).isNull(i);
  }

  @Override
  public long longAt(final int column, final int i) {
    return ((ColumnVector.LongColumn) segment.vector(column)).get(i);
  }

  @Override
  public int intAt(final int column, final int i) {
    return ((ColumnVector.IntColumn) segment.vector(column)).get(i);
  }

  @Override
  public String stringAt(final int column, final int i) {
    final ColumnVector.DictColumn dict = (ColumnVector.DictColumn) segment.vector(column);
    return dict.value(dict.code(i));
  }

  @Override
  public int binaryLength(final int column, final int i) {
    return ((ColumnVector.BinaryColumn) segment.vector(column)).length(i);
  }

  @Override
  public int copyBinaryTo(final int column, final int i, final byte[] dst, final int dstOffset) {
    return ((ColumnVector.BinaryColumn) segment.vector(column)).copyTo(i, dst, dstOffset);
  }

  @Override
  public List<DayRange> dayRanges() {
    return segment.size() == 0 ? List.of() : List.of(new DayRange(epochDay, 0, segment.size()));
  }
}
