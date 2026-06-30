/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics;

import io.camunda.zeebe.protocol.ColumnFamilyScope;
import io.camunda.zeebe.protocol.EnumValue;
import io.camunda.zeebe.protocol.ScopedColumnFamily;

/**
 * Column families for the durable rollups' state, held in their own {@code ZeebeDb} (separate from
 * the base projection). Each metric gets a cell store (windowed aggregate) and an offset store (the
 * per-partition dedup watermark co-committed with the cells).
 */
public enum RollupColumnFamilies implements EnumValue, ScopedColumnFamily {
  /** Reserved default (RocksDB requires a default CF). */
  DEFAULT(0, ColumnFamilyScope.PARTITION_LOCAL),

  /** Region execution-time cells: {@code windowStart ++ codec(RegionKey) -> codec(accumulator)}. */
  REGION_CELLS(1, ColumnFamilyScope.PARTITION_LOCAL),

  /** Region rollup offsets: {@code partitionId -> position} (plus the watermark slot). */
  REGION_OFFSETS(2, ColumnFamilyScope.PARTITION_LOCAL),

  /** Element heatmap cells: {@code windowStart ++ codec(ElementKey) -> codec(accumulator)}. */
  HEATMAP_CELLS(3, ColumnFamilyScope.PARTITION_LOCAL),

  /** Heatmap rollup offsets: {@code partitionId -> position} (plus the watermark slot). */
  HEATMAP_OFFSETS(4, ColumnFamilyScope.PARTITION_LOCAL);

  private final int value;
  private final ColumnFamilyScope scope;

  RollupColumnFamilies(final int value, final ColumnFamilyScope scope) {
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
