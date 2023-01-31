/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.logstreams.log;

import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;

record ProcessedLogAppendEntryImpl(LogAppendEntry entry)
    implements LogAppendEntry {

  @Override
  public long key() {
    return entry.key();
  }

  @Override
  public int sourceIndex() {
    return entry.sourceIndex();
  }

  @Override
  public RecordMetadata recordMetadata() {
    return entry.recordMetadata();
  }

  @Override
  public UnifiedRecordValue recordValue() {
    return entry.recordValue();
  }

  @Override
  public boolean isProcessed() {
    // this class only purpose is to mark the entry as processed
    return true;
  }
}
