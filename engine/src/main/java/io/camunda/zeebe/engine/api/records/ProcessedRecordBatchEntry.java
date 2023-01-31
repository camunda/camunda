/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.api.records;

import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;

public record ProcessedRecordBatchEntry(
    long key, int sourceIndex, RecordMetadata recordMetadata, UnifiedRecordValue recordValue)
    implements ImmutableRecordBatchEntry {

  @Override
  public int getLength() {
    return Long.BYTES
        + // key
        Integer.BYTES
        + // source Index
        recordMetadata.getLength()
        + recordValue.getLength();
  }

  @Override
  public boolean shouldSkipProcessing() {
    return true;
  }
}
