/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.streaming.state;

import io.camunda.zeebe.protocol.ColumnFamilyScope;
import io.camunda.zeebe.protocol.EnumValue;
import io.camunda.zeebe.protocol.ScopedColumnFamily;

/** Column families used by the state-store tests. */
public enum TestColumnFamilies implements EnumValue, ScopedColumnFamily {
  /** Reserved default (RocksDB requires a default CF). */
  DEFAULT(0, ColumnFamilyScope.PARTITION_LOCAL),
  /** A {@code DbLong -> DbString} store. */
  KV(1, ColumnFamilyScope.PARTITION_LOCAL),
  /** A {@code (DbLong, DbString) -> DbString} store, for prefix scans. */
  COMPOSITE(2, ColumnFamilyScope.PARTITION_LOCAL),
  /** Durable rollup cells: {@code windowStart ++ codec(key) -> codec(acc)}. */
  CELLS(3, ColumnFamilyScope.PARTITION_LOCAL),
  /** Durable rollup offsets: {@code partitionId -> position} (plus the watermark slot). */
  OFFSETS(4, ColumnFamilyScope.PARTITION_LOCAL);

  private final int value;
  private final ColumnFamilyScope scope;

  TestColumnFamilies(final int value, final ColumnFamilyScope scope) {
    this.value = value;
    this.scope = scope;
  }

  @Override
  public int getValue() {
    return value;
  }

  @Override
  public ColumnFamilyScope partitionScope() {
    return scope;
  }
}
