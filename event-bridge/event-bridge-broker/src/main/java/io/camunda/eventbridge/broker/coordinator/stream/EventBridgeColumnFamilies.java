/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.coordinator.stream;

import io.camunda.zeebe.protocol.ColumnFamilyScope;
import io.camunda.zeebe.protocol.EnumValue;
import io.camunda.zeebe.protocol.ScopedColumnFamily;

/**
 * Column families for the event bridge coordinator's replicated state, stored in the coordinator
 * partition's {@code ZeebeDb}. Kept entirely separate from the engine's {@code ZbColumnFamilies} —
 * the coordinator runs its own {@code StreamProcessor} with its own RocksDB instance.
 */
public enum EventBridgeColumnFamilies implements EnumValue, ScopedColumnFamily {
  /** Reserved default (RocksDB requires a default CF). */
  DEFAULT(0, ColumnFamilyScope.PARTITION_LOCAL),

  /** Committed consumer offsets keyed by {@code (groupId, partitionId)} → next position. */
  CONSUMER_OFFSETS(1, ColumnFamilyScope.PARTITION_LOCAL),

  /** Replicated consumer-group metadata keyed by {@code groupId} → encoded members/assignment. */
  GROUP_METADATA(2, ColumnFamilyScope.PARTITION_LOCAL);

  private final int value;
  private final ColumnFamilyScope scope;

  EventBridgeColumnFamilies(final int value, final ColumnFamilyScope scope) {
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
