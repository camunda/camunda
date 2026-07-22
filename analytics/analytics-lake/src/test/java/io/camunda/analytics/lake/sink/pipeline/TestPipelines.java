/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.sink.pipeline;

import io.camunda.analytics.lake.sink.ColumnType;
import io.camunda.analytics.lake.sink.ColumnVector;
import io.camunda.analytics.lake.sink.ColumnarSegmentRing;
import io.camunda.analytics.lake.sink.SealReason;
import io.camunda.analytics.lake.sink.Segment;
import io.camunda.analytics.lake.sink.SortedRun;
import io.camunda.analytics.lake.sink.TableSchema;
import java.time.Duration;
import java.util.List;
import java.util.function.Function;

/**
 * Shared fixtures for the pipeline tests: a minimal one-column {@link TableSchema} (that column is
 * both the sort key and the family-day source, and holds the row's "offset" test value), plus a
 * test-only row feeder that plays the part of the not-yet-built {@code RowAppender} (begin/put/
 * endRow) against a real {@link ColumnarSegmentRing}, exactly as the data path will.
 */
final class TestPipelines {

  /** The schema's only column: holds each row's test value, doubles as the family-day source. */
  static final int VALUE_COLUMN = 0;

  /** Fixed family day every {@link IdentitySortedRun} in these tests reports, unless overridden. */
  static final long EPOCH_DAY = 19000L;

  private TestPipelines() {}

  static TableSchema schema(final String table) {
    return new TableSchema(
        table, List.of(new TableSchema.Column("value", ColumnType.LONG, 1, false, 0, true)));
  }

  static Segment[] newSegments(
      final TableSchema schema, final int ringSegments, final int segmentRows) {
    final Segment[] segments = new Segment[ringSegments];
    for (int i = 0; i < ringSegments; i++) {
      segments[i] =
          new Segment(schema, new ColumnVector[] {new FakeLongColumn(segmentRows)}, segmentRows);
    }
    return segments;
  }

  /** A single row with the given value, useful for the {@link FileWindow}-level tests. */
  static Segment segmentWithValues(final TableSchema schema, final long... values) {
    final int capacity = Math.max(values.length, 1);
    final Segment segment =
        new Segment(schema, new ColumnVector[] {new FakeLongColumn(capacity)}, capacity);
    for (final long value : values) {
      final int row = segment.size();
      ((ColumnVector.LongColumn) segment.vector(VALUE_COLUMN)).set(row, value);
      segment.rowCompleted();
    }
    return segment;
  }

  /** A sorter that performs no reordering, tagging every run with {@link #EPOCH_DAY}. */
  static Function<Segment, SortedRun> identitySorter() {
    return segment -> new IdentitySortedRun(segment, EPOCH_DAY);
  }

  /**
   * Mimics one {@code RowAppender} begin()/put()/endRow() cycle, poll thread only: refuses the row
   * (mirroring {@code begin()} returning false) if the filling segment is already at capacity —
   * that only happens when a previous {@code endRow()}'s SEGMENT_FULL seal attempt failed because
   * the ring was full, per {@link ColumnarSegmentRing#seal}'s contract. Otherwise appends the value
   * and, if the row fills the segment, seals it as {@code endRow()} would.
   *
   * @return true if the row was accepted; false = backpressure engaged, retry later
   */
  static boolean tryAppendRow(final ColumnarSegmentRing ring, final long value) {
    final Segment filling = ring.filling();
    if (filling.isFull()) {
      return false;
    }
    ((ColumnVector.LongColumn) filling.vector(VALUE_COLUMN)).set(filling.size(), value);
    filling.rowCompleted();
    if (filling.isFull()) {
      ring.seal(SealReason.SEGMENT_FULL);
    }
    return true;
  }

  /**
   * Like {@link #tryAppendRow}, but retries under real backpressure instead of dropping the row —
   * exactly what a real {@code RowAppender} caller must do on a {@code false} return. Needed
   * whenever a test's feeder can legitimately outrun the (separately scheduled) flush thread.
   */
  static void appendRowRetrying(
      final ColumnarSegmentRing ring, final long value, final Duration timeout) {
    final long deadlineNanos = System.nanoTime() + timeout.toNanos();
    while (!tryAppendRow(ring, value)) {
      if (System.nanoTime() - deadlineNanos > 0) {
        throw new AssertionError(
            "row " + value + " still refused by backpressure after " + timeout);
      }
      Thread.onSpinWait();
    }
  }
}
