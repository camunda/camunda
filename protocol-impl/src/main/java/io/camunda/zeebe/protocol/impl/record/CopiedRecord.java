/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.protocol.impl.record;

import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.util.StringUtil;
import org.agrona.concurrent.UnsafeBuffer;

public final class CopiedRecord<T extends UnifiedRecordValue> implements Record<T> {

  private final ValueType valueType;
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
  private final String brokerVersion;
  private final int recordVersion;

  public CopiedRecord(
      final T recordValue,
      final RecordMetadata metadata,
      final long key,
      final int partitionId,
      final long position,
      final long sourcePosition,
      final long timestamp) {
    this.recordValue = recordValue;
    this.key = key;
    this.position = position;
    this.sourcePosition = sourcePosition;
    this.timestamp = timestamp;

    intent = metadata.getIntent();
    recordType = metadata.getRecordType();
    this.partitionId = partitionId;
    rejectionType = metadata.getRejectionType();
    rejectionReason = metadata.getRejectionReason();
    valueType = metadata.getValueType();
    brokerVersion = metadata.getBrokerVersion().toString();
    recordVersion = metadata.getRecordVersion();
  }

  private CopiedRecord(final CopiedRecord<T> copiedRecord) {
    final UnifiedRecordValue value = copiedRecord.getValue();
    final byte[] bytes = new byte[value.getLength()];
    final UnsafeBuffer buffer = new UnsafeBuffer(bytes);
    value.write(buffer, 0);

    final Class<? extends UnifiedRecordValue> recordValueClass = value.getClass();
    try {
      final T recordValue = (T) recordValueClass.newInstance();
      recordValue.wrap(buffer);
      this.recordValue = recordValue;
    } catch (final Exception e) {
      throw new RuntimeException(
          String.format(
              "Expected to instantiate %s, but has no default ctor.", recordValueClass.getName()),
          e);
    }

    key = copiedRecord.key;
    position = copiedRecord.position;
    sourcePosition = copiedRecord.sourcePosition;
    timestamp = copiedRecord.timestamp;

    intent = copiedRecord.intent;
    recordType = copiedRecord.recordType;
    partitionId = copiedRecord.partitionId;
    rejectionType = copiedRecord.rejectionType;
    rejectionReason = copiedRecord.rejectionReason;
    valueType = copiedRecord.valueType;
    brokerVersion = copiedRecord.brokerVersion;
    recordVersion = copiedRecord.recordVersion;
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
  public String getBrokerVersion() {
    return brokerVersion;
  }

  @Override
  public int getRecordVersion() {
    return recordVersion;
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
  public Record<T> copyOf() {
    return new CopiedRecord<>(this);
  }

  /**
   * Please be aware that this method is not thread-safe. Some records contain properties that will
   * get modified when iterating over it (eg. Records containing an
   * {@link io.camunda.zeebe.msgpack.property.ArrayProperty).
   * Multiple threads serializing the same Record at a time will result in exceptions.
   *
   * @return a JSON marshaled representation
   */
  @Override
  public String toJson() {
    return MsgPackConverter.convertJsonSerializableObjectToJson(this);
  }

  @Override
  public String toString() {
    return StringUtil.limitString(toJson(), 1024);
  }
}
