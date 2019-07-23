/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.protocol.impl.record;

import io.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.RecordType;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.Intent;

public class CopiedRecord<T extends UnifiedRecordValue> implements Record<T> {

  private final T recordValue;

  private final long key;
  private final long position;
  private final long sourcePosition;
  private final long timestamp;

  private final RecordType recordType;
  private final Intent intent;
  private final int partitionId;
  private final RejectionType rejectionType;
  private final String rejectionReason;
  protected ValueType valueType;

  public CopiedRecord(
      T recordValue,
      RecordMetadata metadata,
      long key,
      int partitionId,
      long position,
      long sourcePosition,
      long timestamp) {
    this.recordValue = recordValue;
    this.key = key;
    this.position = position;
    this.sourcePosition = sourcePosition;
    this.timestamp = timestamp;

    this.intent = metadata.getIntent();
    this.recordType = metadata.getRecordType();
    this.partitionId = partitionId;
    this.rejectionType = metadata.getRejectionType();
    this.rejectionReason = metadata.getRejectionReason();
    this.valueType = metadata.getValueType();
  }

  @Override
  public long getPosition() {
    return position;
  }

  @Override
  public long getSourceRecordPosition() {
    return sourcePosition;
  }

  @Override
  public long getKey() {
    return key;
  }

  @Override
  public long getTimestamp() {
    return timestamp;
  }

  @Override
  public Intent getIntent() {
    return intent;
  }

  @Override
  public int getPartitionId() {
    return partitionId;
  }

  @Override
  public RecordType getRecordType() {
    return recordType;
  }

  @Override
  public RejectionType getRejectionType() {
    return rejectionType;
  }

  @Override
  public String getRejectionReason() {
    return rejectionReason;
  }

  @Override
  public ValueType getValueType() {
    return valueType;
  }

  @Override
  public T getValue() {
    return recordValue;
  }

  @Override
  public String toJson() {
    return MsgPackConverter.convertJsonSerializableObjectToJson(this);
  }

  @Override
  public String toString() {
    return toJson();
  }
}
