/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.sink.batch;

import io.camunda.analytics.lake.sink.ColumnType;
import io.camunda.analytics.lake.sink.ColumnVector;

/**
 * Heap-backed {@link ColumnVector.BinaryColumn}: a fixed byte arena plus prefix-sum offsets.
 *
 * <p>The arena is sized once, at construction, as {@code rowCapacity * avgBytesPerRow} — a budget
 * configured by {@link SegmentFactory}. Growing the arena at steady state is forbidden by design (a
 * reallocation is exactly the hot-path allocation this whole package exists to avoid); a row whose
 * bytes would not fit in the remaining arena space is rejected with an {@link
 * IllegalStateException} instead of silently growing. Callers must budget {@code avgBytesPerRow}
 * generously enough that this is the rare exception, not the norm.
 *
 * <p>Offsets are a prefix sum ({@code offsets[row]} = start, {@code offsets[row + 1]} = end),
 * written incrementally as rows are appended in order {@code 0..size()-1} — the only append order
 * the ring ever produces (see {@link io.camunda.analytics.lake.sink.RowAppender}). A null row does
 * not consume arena space, but it must still close the prefix-sum chain for the row that follows
 * it, so {@link #setNull(int)} stamps {@code offsets[row + 1]} at the current arena position (a
 * zero-length slice) rather than leaving it stale.
 */
public final class HeapBinaryColumn implements ColumnVector.BinaryColumn {

  private final byte[] arena;
  private final int[] offsets; // length rowCapacity + 1, prefix sums
  private final long[] nullMask; // null when the column is not nullable

  private int arenaPos;

  public HeapBinaryColumn(final int rowCapacity, final boolean nullable, final int avgBytesPerRow) {
    arena = new byte[rowCapacity * avgBytesPerRow];
    offsets = new int[rowCapacity + 1];
    nullMask = nullable ? NullBitset.allocate(rowCapacity) : null;
  }

  @Override
  public ColumnType type() {
    return ColumnType.BINARY;
  }

  @Override
  public void reset() {
    arenaPos = 0;
    offsets[0] = 0;
    if (nullMask != null) {
      NullBitset.clearAll(nullMask);
    }
  }

  @Override
  public boolean isNull(final int row) {
    return nullMask != null && NullBitset.isSet(nullMask, row);
  }

  @Override
  public void setNull(final int row) {
    if (nullMask == null) {
      throw new UnsupportedOperationException(
          "column is not nullable per its schema; setNull() is a schema violation");
    }
    NullBitset.set(nullMask, row);
    offsets[row + 1] = arenaPos; // keep the prefix-sum chain valid for the row that follows
  }

  @Override
  public void set(final int row, final byte[] src, final int offset, final int len) {
    if (arenaPos + len > arena.length) {
      throw new IllegalStateException(
          ("binary arena exhausted: row %d needs %d bytes but only %d of a %d-byte budget "
                  + "(rowCapacity * avgBytesPerRow) remain — arena growth at steady state is "
                  + "forbidden by design; raise the configured avgBytesPerRow for this column")
              .formatted(row, len, arena.length - arenaPos, arena.length));
    }
    System.arraycopy(src, offset, arena, arenaPos, len);
    arenaPos += len;
    offsets[row + 1] = arenaPos;
    if (nullMask != null) {
      NullBitset.clear(nullMask, row);
    }
  }

  @Override
  public int length(final int row) {
    return offsets[row + 1] - offsets[row];
  }

  @Override
  public int copyTo(final int row, final byte[] dst, final int dstOffset) {
    final int len = length(row);
    System.arraycopy(arena, offsets[row], dst, dstOffset, len);
    return len;
  }
}
