/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.streamprocessor;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.RecordType;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.Intent;
import io.zeebe.util.StringUtil;

public final class TypedEventImpl implements TypedRecord {
  private final int partitionId;
  private LoggedEvent rawEvent;
  private RecordMetadata metadata;
  private UnifiedRecordValue value;

  public TypedEventImpl(final int partitionId) {
    this.partitionId = partitionId;
  }

  public void wrap(
      final LoggedEvent rawEvent, final RecordMetadata metadata, final UnifiedRecordValue value) {
    this.rawEvent = rawEvent;
    this.metadata = metadata;
    this.value = value;
  }

  @Override
  public long getPosition() {
    return rawEvent.getPosition();
  }

  @Override
  public long getSourceRecordPosition() {
    return rawEvent.getSourceEventPosition();
  }

  @Override
  public long getTimestamp() {
    return rawEvent.getTimestamp();
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
  public ValueType getValueType() {
    return metadata.getValueType();
  }

  @Override
  public long getKey() {
    return rawEvent.getKey();
  }

  @Override
  public UnifiedRecordValue getValue() {
    return value;
  }

  @Override
  @JsonIgnore
  public int getRequestStreamId() {
    return metadata.getRequestStreamId();
  }

  @Override
  @JsonIgnore
  public long getRequestId() {
    return metadata.getRequestId();
  }

  @Override
  @JsonIgnore
  public long getLength() {
    return (long) metadata.getLength() + value.getLength();
  }

  @Override
  public String toJson() {
    return MsgPackConverter.convertJsonSerializableObjectToJson(this);
  }

  @Override
  public Record clone() {
    return CopiedRecords.createCopiedRecord(getPartitionId(), rawEvent);
  }

  @Override
  public String toString() {
    return "TypedEventImpl{"
        + "metadata="
        + metadata
        + ", value="
        + StringUtil.limitString(value.toString(), 1024)
        + '}';
  }
}
