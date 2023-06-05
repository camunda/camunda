/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.stream.impl.records;

import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.stream.api.records.TypedRecord;

public class UnwrittenRecord implements TypedRecord {
  private final long key;
  private final int partitionId;
  private final UnifiedRecordValue value;
  private final RecordMetadata metadata;

  public UnwrittenRecord(
      final long key,
      int partitionId,
      final UnifiedRecordValue value,
      final RecordMetadata metadata) {
    this.key = key;
    this.partitionId = partitionId;
    this.value = value;
    this.metadata = metadata;
  }

  @Override
  public long getPosition() {
    throw new UnsupportedOperationException();
  }

  @Override
  public long getSourceRecordPosition() {
    throw new UnsupportedOperationException();
  }

  @Override
  public long getTimestamp() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Intent getIntent() {
    return metadata.getIntent();
  }

  @Override
  public int getPartitionId() {
    return partitionId;
  }

  @Override
  public RecordType getRecordType() {
    return metadata.getRecordType();
  }

  @Override
  public RejectionType getRejectionType() {
    return metadata.getRejectionType();
  }

  @Override
  public String getRejectionReason() {
    return metadata.getRejectionReason();
  }

  @Override
  public String getBrokerVersion() {
    return metadata.getBrokerVersion().toString();
  }

  @Override
  public int getRecordVersion() {
    return metadata.getRecordVersion();
  }

  @Override
  public ValueType getValueType() {
    return metadata.getValueType();
  }

  @Override
  public long getKey() {
    return key;
  }

  @Override
  public UnifiedRecordValue getValue() {
    return value;
  }

  @Override
  public int getRequestStreamId() {
    return metadata.getRequestStreamId();
  }

  @Override
  public long getRequestId() {
    return metadata.getRequestId();
  }

  @Override
  public int getLength() {
    return metadata.getLength() + value.getLength();
  }
}
