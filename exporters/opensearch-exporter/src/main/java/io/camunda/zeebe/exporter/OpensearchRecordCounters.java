/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import java.util.EnumMap;
import java.util.Map;

public final class OpensearchRecordCounters {

  private static final long INITIAL_RECORD_COUNTER = 0L;

  /**
   * Stores a counter per value type. The counter is used to create a sequence for a given record.
   */
  private final Map<ValueType, Long> recordCountersByValueType;

  public OpensearchRecordCounters() {
    recordCountersByValueType = new EnumMap<>(ValueType.class);
  }

  public OpensearchRecordCounters(final Map<ValueType, Long> recordCounters) {
    recordCountersByValueType = new EnumMap<>(recordCounters);
  }

  public RecordSequence getNextRecordSequence(final Record<?> record) {
    final var valueType = record.getValueType();
    final long recordCounter =
        recordCountersByValueType.getOrDefault(valueType, INITIAL_RECORD_COUNTER);

    final long nextCounter = recordCounter + 1;
    return new RecordSequence(record.getPartitionId(), nextCounter);
  }

  public void updateRecordCounters(final Record<?> record, final RecordSequence recordSequence) {
    final var valueType = record.getValueType();
    final var counter = recordSequence.counter();
    recordCountersByValueType.put(valueType, counter);
  }

  public Map<ValueType, Long> getRecordCounters() {
    return recordCountersByValueType;
  }
}
