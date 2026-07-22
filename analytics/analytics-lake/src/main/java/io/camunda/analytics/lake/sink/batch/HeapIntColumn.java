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
 * Heap-backed {@link ColumnVector.IntColumn}: a preallocated {@code int[]} plus, for nullable
 * columns only, a bitset null mask (see {@link NullBitset}). Same reset/null contract as {@link
 * HeapLongColumn}.
 */
public final class HeapIntColumn implements ColumnVector.IntColumn {

  private final int[] values;
  private final long[] nullMask; // null when the column is not nullable

  public HeapIntColumn(final int rowCapacity, final boolean nullable) {
    values = new int[rowCapacity];
    nullMask = nullable ? NullBitset.allocate(rowCapacity) : null;
  }

  @Override
  public ColumnType type() {
    return ColumnType.INT;
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
  public int get(final int row) {
    return values[row];
  }

  @Override
  public void set(final int row, final int value) {
    values[row] = value;
    if (nullMask != null) {
      NullBitset.clear(nullMask, row);
    }
  }
}
