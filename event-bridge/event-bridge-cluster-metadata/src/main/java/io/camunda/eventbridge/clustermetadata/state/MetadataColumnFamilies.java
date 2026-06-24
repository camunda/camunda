/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.clustermetadata.state;

import io.camunda.zeebe.protocol.ColumnFamilyScope;
import io.camunda.zeebe.protocol.EnumValue;
import io.camunda.zeebe.protocol.ScopedColumnFamily;

/**
 * Column families for the metadata group's replicated state, stored in the metadata partition's
 * {@code ZeebeDb}. Kept entirely separate from the engine's {@code ZbColumnFamilies} and from the
 * consumer-group coordinator's families — this is a dedicated {@code StreamProcessor}/RocksDB
 * instance holding only the topic registry.
 */
public enum MetadataColumnFamilies implements EnumValue, ScopedColumnFamily {
  /** Reserved default (RocksDB requires a default CF). */
  DEFAULT(0, ColumnFamilyScope.PARTITION_LOCAL),

  /**
   * Topic registry (desired state) keyed by {@code topicName} → encoded partition/replica config.
   */
  TOPIC_REGISTRY(1, ColumnFamilyScope.PARTITION_LOCAL);

  private final int value;
  private final ColumnFamilyScope scope;

  MetadataColumnFamilies(final int value, final ColumnFamilyScope scope) {
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
