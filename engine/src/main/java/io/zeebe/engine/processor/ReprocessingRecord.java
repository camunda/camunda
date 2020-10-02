/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor;

import io.zeebe.protocol.record.RecordType;
import io.zeebe.protocol.record.intent.Intent;

public final class ReprocessingRecord {

  private final long key;
  private final long sourceRecordPosition;
  private final Intent intent;
  private final RecordType recordType;

  ReprocessingRecord(
      final long key,
      final long sourceRecordPosition,
      final Intent intent,
      final RecordType recordType) {
    this.key = key;
    this.sourceRecordPosition = sourceRecordPosition;
    this.intent = intent;
    this.recordType = recordType;
  }

  public long getKey() {
    return key;
  }

  public long getSourceRecordPosition() {
    return sourceRecordPosition;
  }

  public Intent getIntent() {
    return intent;
  }

  @Override
  public String toString() {
    return "{"
        + "key="
        + key
        + ", sourceRecordPosition="
        + sourceRecordPosition
        + ", intent="
        + intent.getClass().getSimpleName()
        + ":"
        + intent.name()
        + ", recordType="
        + recordType
        + "}";
  }
}
