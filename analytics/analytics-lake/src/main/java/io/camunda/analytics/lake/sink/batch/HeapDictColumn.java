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
 * Heap-backed {@link ColumnVector.DictColumn}: an {@code int[]} of codes resolved against a single
 * {@link Interner} shared by every {@code STRING_DICT} column of the pipeline (see {@link
 * SegmentFactory}). {@link #set(int, CharSequence)} is allocation-free once a value has been seen
 * before anywhere in the pipeline; only the first appearance of a distinct value allocates.
 */
public final class HeapDictColumn implements ColumnVector.DictColumn {

  private final int[] codes;
  private final long[] nullMask; // null when the column is not nullable
  private final Interner interner;

  public HeapDictColumn(final int rowCapacity, final boolean nullable, final Interner interner) {
    codes = new int[rowCapacity];
    nullMask = nullable ? NullBitset.allocate(rowCapacity) : null;
    this.interner = interner;
  }

  @Override
  public ColumnType type() {
    return ColumnType.STRING_DICT;
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
  public int code(final int row) {
    return codes[row];
  }

  @Override
  public void set(final int row, final CharSequence value) {
    codes[row] = interner.intern(value);
    if (nullMask != null) {
      NullBitset.clear(nullMask, row);
    }
  }

  @Override
  public String value(final int code) {
    return interner.valueOf(code);
  }
}
