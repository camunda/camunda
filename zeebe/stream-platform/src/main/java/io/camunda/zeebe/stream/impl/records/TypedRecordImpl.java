/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl.records;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.logstreams.log.LoggedEvent;
import io.camunda.zeebe.protocol.impl.encoding.AuthInfo;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.Agent;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.StringUtil;
import java.util.Map;

public final class TypedRecordImpl implements TypedRecord {
  private final int partitionId;
  private LoggedEvent rawEvent;
  private RecordMetadata metadata;
  private UnifiedRecordValue value;

  public TypedRecordImpl(final int partitionId) {
    this.partitionId = partitionId;
  }

  public void wrap(
      final LoggedEvent rawEvent, final RecordMetadata metadata, final UnifiedRecordValue value) {
    this.rawEvent = rawEvent;
    this.metadata = metadata;
    this.value = value;
  }

  @JsonIgnore
  public RecordMetadata getMetadata() {
    return metadata;
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
  public Map<String, Object> getAuthorizations() {
    return metadata.getAuthorization().toDecodedMap();
  }

  @Override
  public Agent getAgent() {
    return metadata.getAgent();
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
  public long getOperationReference() {
    return metadata.getOperationReference();
  }

  @Override
  public long getBatchOperationReference() {
    return metadata.getBatchOperationReference();
  }

  @Override
  public Record copyOf() {
    return CopiedRecords.createCopiedRecord(getPartitionId(), rawEvent);
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
  public AuthInfo getAuthInfo() {
    return metadata.getAuthorization();
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
  public int getLength() {
    return metadata.getLength() + value.getLength();
  }

  @Override
  public String toJson() {
    return MsgPackConverter.convertJsonSerializableObjectToJson(this);
  }

  @Override
  public String toString() {
    return "TypedRecordImpl{"
        + "metadata="
        + metadata
        + ", value="
        + StringUtil.limitString(value.toString(), 1024)
        + '}';
  }
}
