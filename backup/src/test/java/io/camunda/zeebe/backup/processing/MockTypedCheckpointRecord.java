/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.processing;

import io.camunda.zeebe.protocol.impl.record.value.management.CheckpointRecord;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.stream.api.records.TypedRecord;

record MockTypedCheckpointRecord(
    long position,
    long sourceRecordPosition,
    Intent intent,
    RecordType recordType,
    CheckpointRecord value)
    implements TypedRecord<CheckpointRecord> {

  @Override
  public long getPosition() {
    return position;
  }

  @Override
  public long getSourceRecordPosition() {
    return sourceRecordPosition;
  }

  @Override
  public long getTimestamp() {
    return 0;
  }

  @Override
  public Intent getIntent() {
    return intent;
  }

  @Override
  public int getPartitionId() {
    return 1;
  }

  @Override
  public RecordType getRecordType() {
    return recordType;
  }

  @Override
  public RejectionType getRejectionType() {
    return null;
  }

  @Override
  public String getRejectionReason() {
    return null;
  }

  @Override
  public String getBrokerVersion() {
    return null;
  }

  @Override
  public int getRecordVersion() {
    return 1;
  }

  @Override
  public ValueType getValueType() {
    return ValueType.CHECKPOINT;
  }

  @Override
  public long getKey() {
    return 0;
  }

  @Override
  public CheckpointRecord getValue() {
    return value;
  }

  @Override
  public int getRequestStreamId() {
    return -1;
  }

  @Override
  public long getRequestId() {
    return -1L;
  }

  @Override
  public int getLength() {
    return 0;
  }
}
