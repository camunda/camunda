/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl.records;

import io.camunda.zeebe.protocol.impl.encoding.AuthInfo;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.Agent;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.StringUtil;
import java.util.Map;

public class UnwrittenRecord implements TypedRecord {
  private final long key;
  private final int partitionId;
  private final UnifiedRecordValue value;
  private final RecordMetadata metadata;

  public UnwrittenRecord(
      final long key,
      final int partitionId,
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
  public long getKey() {
    return key;
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

  @Override
  public String toString() {
    return "UnwrittenRecord{"
        + "metadata="
        + metadata
        + ", value="
        + StringUtil.limitString(value.toString(), 1024)
        + '}';
  }
}
