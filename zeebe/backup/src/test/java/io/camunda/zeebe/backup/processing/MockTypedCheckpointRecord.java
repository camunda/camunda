/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.processing;

import io.camunda.zeebe.protocol.impl.encoding.AuthInfo;
import io.camunda.zeebe.protocol.impl.record.value.management.CheckpointRecord;
import io.camunda.zeebe.protocol.record.Agent;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.Map;

record MockTypedCheckpointRecord(
    long position,
    long sourceRecordPosition,
    Intent intent,
    RecordType recordType,
    CheckpointRecord value,
    int requestStreamId,
    long requestId)
    implements TypedRecord<CheckpointRecord> {

  public MockTypedCheckpointRecord(
      final long position,
      final long sourceRecordPosition,
      final Intent intent,
      final RecordType recordType,
      final CheckpointRecord value) {
    this(
        position,
        sourceRecordPosition,
        intent,
        recordType,
        value,
        -1, // requestStreamId
        -1L // requestId
        );
  }

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
  public Map<String, Object> getAuthorizations() {
    return Map.of();
  }

  @Override
  public Agent getAgent() {
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
  public long getOperationReference() {
    return -1;
  }

  @Override
  public long getBatchOperationReference() {
    return -1;
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
  public AuthInfo getAuthInfo() {
    return null;
  }

  @Override
  public int getRequestStreamId() {
    return requestStreamId;
  }

  @Override
  public long getRequestId() {
    return requestId;
  }

  @Override
  public int getLength() {
    return 0;
  }
}
