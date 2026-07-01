/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.projection;

import io.camunda.zeebe.protocol.ColumnFamilyScope;
import io.camunda.zeebe.protocol.EnumValue;
import io.camunda.zeebe.protocol.ScopedColumnFamily;

/** Column families for the analytics base-projection state, held in its own {@code ZeebeDb}. */
public enum AnalyticsColumnFamilies implements EnumValue, ScopedColumnFamily {
  /** Reserved default (RocksDB requires a default CF). */
  DEFAULT(0, ColumnFamilyScope.PARTITION_LOCAL),

  /** Per-process-instance variables, keyed by {@code processInstanceKey}. */
  INSTANCE_VARIABLES(1, ColumnFamilyScope.PARTITION_LOCAL),

  /** Per source partition: the position up to which the fold has consumed. */
  CONSUMED_POSITION(2, ColumnFamilyScope.PARTITION_LOCAL),

  /**
   * Per in-flight element instance: its activation time, keyed by {@code instanceKey:elementId}.
   */
  ELEMENT_START(3, ColumnFamilyScope.PARTITION_LOCAL),

  /**
   * Per process instance: a flag set when it has raised at least one incident, keyed by {@code
   * processInstanceKey}. Read (and cleared) when the instance reaches a terminal state so the
   * derived fact can record whether the instance ever had an incident.
   */
  INSTANCE_INCIDENT(4, ColumnFamilyScope.PARTITION_LOCAL);

  private final int value;
  private final ColumnFamilyScope scope;

  AnalyticsColumnFamilies(final int value, final ColumnFamilyScope scope) {
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
