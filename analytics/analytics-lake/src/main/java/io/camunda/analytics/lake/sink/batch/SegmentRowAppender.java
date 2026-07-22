/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.sink.batch;

import io.camunda.analytics.lake.sink.ColumnVector;
import io.camunda.analytics.lake.sink.ColumnarSegmentRing;
import io.camunda.analytics.lake.sink.RowAppender;
import io.camunda.analytics.lake.sink.SealReason;
import io.camunda.analytics.lake.sink.Segment;

/**
 * The poll thread's {@link RowAppender}: writes directly into the ring's currently-filling {@link
 * Segment}, sealing it and rotating to the next slot when it fills. No allocation at steady state —
 * see {@code io.camunda.analytics.lake.sink} {@code package-info}.
 */
public final class SegmentRowAppender implements RowAppender {

  private final ColumnarSegmentRing ring;
  private Segment filling;

  public SegmentRowAppender(final ColumnarSegmentRing ring) {
    this.ring = ring;
  }

  @Override
  public boolean begin() {
    Segment segment = ring.filling();
    if (segment.isFull()) {
      if (!ring.seal(SealReason.SEGMENT_FULL)) {
        return false; // ring full; backpressure engaged by the ring itself
      }
      segment = ring.filling();
    }
    filling = segment;
    return true;
  }

  @Override
  public RowAppender putLong(final int column, final long value) {
    ((ColumnVector.LongColumn) filling.vector(column)).set(filling.size(), value);
    return this;
  }

  @Override
  public RowAppender putInt(final int column, final int value) {
    ((ColumnVector.IntColumn) filling.vector(column)).set(filling.size(), value);
    return this;
  }

  @Override
  public RowAppender putDict(final int column, final CharSequence value) {
    ((ColumnVector.DictColumn) filling.vector(column)).set(filling.size(), value);
    return this;
  }

  @Override
  public RowAppender putBinary(
      final int column, final byte[] src, final int offset, final int len) {
    ((ColumnVector.BinaryColumn) filling.vector(column)).set(filling.size(), src, offset, len);
    return this;
  }

  @Override
  public RowAppender putNull(final int column) {
    filling.vector(column).setNull(filling.size());
    return this;
  }

  @Override
  public void endRow() {
    filling.rowCompleted();
  }
}
