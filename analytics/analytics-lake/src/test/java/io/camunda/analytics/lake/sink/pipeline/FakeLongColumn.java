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
import java.util.Arrays;

/**
 * Minimal array-backed {@link ColumnVector.LongColumn}, allocated once, rewound by {@link #reset}.
 */
final class FakeLongColumn implements ColumnVector.LongColumn {

  private final long[] values;
  private final boolean[] nulls;

  FakeLongColumn(final int capacity) {
    values = new long[capacity];
    nulls = new boolean[capacity];
  }

  @Override
  public ColumnType type() {
    return ColumnType.LONG;
  }

  @Override
  public void reset() {
    Arrays.fill(nulls, false);
  }

  @Override
  public boolean isNull(final int row) {
    return nulls[row];
  }

  @Override
  public void setNull(final int row) {
    nulls[row] = true;
  }

  @Override
  public long get(final int row) {
    return values[row];
  }

  @Override
  public void set(final int row, final long value) {
    values[row] = value;
    nulls[row] = false;
  }
}
