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
 * Heap-backed {@link ColumnVector.LongColumn}: a preallocated {@code long[]} plus, for nullable
 * columns only, a bitset null mask (see {@link NullBitset}). {@link #reset()} rewinds the mask (if
 * present) but never touches the value array — stale values in unset slots are harmless because
 * {@link ColumnVector}'s contract requires {@link #isNull(int)} to be checked before reading.
 */
public final class HeapLongColumn implements ColumnVector.LongColumn {

  private final long[] values;
  private final long[] nullMask; // null when the column is not nullable

  public HeapLongColumn(final int rowCapacity, final boolean nullable) {
    values = new long[rowCapacity];
    nullMask = nullable ? NullBitset.allocate(rowCapacity) : null;
  }

  @Override
  public ColumnType type() {
    return ColumnType.LONG;
  }

  @Override
  public void reset() {
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
  }

  @Override
  public long get(final int row) {
    return values[row];
  }

  @Override
  public void set(final int row, final long value) {
    values[row] = value;
    if (nullMask != null) {
      NullBitset.clear(nullMask, row);
    }
  }
}
