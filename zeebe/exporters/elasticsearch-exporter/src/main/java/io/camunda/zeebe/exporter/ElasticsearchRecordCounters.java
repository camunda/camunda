/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter;

import io.camunda.zeebe.protocol.record.Record;

/**
 * Maintains a single global counter for all records regardless of value type. In the combined Zeebe
 * index all value types are stored together, so the sequence counter must reflect the global
 * ordering across all record types rather than per-type ordering.
 *
 * @see <a href="https://github.com/camunda/camunda/issues/10568">Related issue</a>
 */
public final class ElasticsearchRecordCounters {

  private static final long INITIAL_RECORD_COUNTER = 0L;

  /** A single counter incremented for every record, regardless of its value type. */
  private long recordCounter;

  public ElasticsearchRecordCounters() {
    recordCounter = INITIAL_RECORD_COUNTER;
  }

  public ElasticsearchRecordCounters(final long recordCounter) {
    this.recordCounter = recordCounter;
  }

  public RecordSequence getNextRecordSequence(final Record<?> record) {
    return new RecordSequence(record.getPartitionId(), recordCounter + 1);
  }

  public void updateRecordCounters(final Record<?> record, final RecordSequence recordSequence) {
    recordCounter = recordSequence.counter();
  }

  public long getRecordCounter() {
    return recordCounter;
  }
}
