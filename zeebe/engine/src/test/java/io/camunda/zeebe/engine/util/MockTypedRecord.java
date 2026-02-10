/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util;

import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.encoding.AuthInfo;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.Agent;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.Map;

public final class MockTypedRecord<T extends UnifiedRecordValue> implements TypedRecord<T> {

  private final long timestamp;
  private long key;
  private RecordMetadata metadata;
  private T value;

  public MockTypedRecord(final long key, final RecordMetadata metadata, final T value) {
    this.key = key;
    this.metadata = metadata;
    this.value = value;
    timestamp = System.currentTimeMillis();
  }

  @Override
  public long getKey() {
    return key;
  }

  public void setKey(final long key) {
    this.key = key;
  }

  @Override
  public T getValue() {
    return value;
  }

  public void setValue(final T value) {
    this.value = value;
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

  public void setMetadata(final RecordMetadata metadata) {
    this.metadata = metadata;
  }

  @Override
  public long getPosition() {
    throw new UnsupportedOperationException("not yet implemented");
  }

  @Override
  public long getSourceRecordPosition() {
    throw new UnsupportedOperationException("not yet implemented");
  }

  @Override
  public long getTimestamp() {
    return timestamp;
  }

  @Override
  public Intent getIntent() {
    return metadata.getIntent();
  }

  @Override
  public int getPartitionId() {
    return Protocol.decodePartitionId(key);
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
    return metadata.getAuthorization().getClaims();
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
  public Record<T> copyOf() {
    throw new UnsupportedOperationException("not yet implemented");
  }

  @Override
  public String toJson() {
    throw new UnsupportedOperationException("not yet implemented");
  }
}
