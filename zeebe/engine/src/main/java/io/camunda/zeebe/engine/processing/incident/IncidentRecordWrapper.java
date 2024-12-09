/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.incident;

import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordMetadataEncoder;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.Map;

/**
 * A wrapper class for records that facilitates retrying commands after an incident is resolved.
 *
 * <p>This class is designed to handle different types of records and their corresponding intents,
 * allowing seamless recovery by re-processing the failed command.
 */
final class IncidentRecordWrapper<T extends UnifiedRecordValue> implements TypedRecord<T> {

  private final long key;
  private final Intent intent;
  private final T record;

  IncidentRecordWrapper(final long key, final Intent intent, final T record) {
    this.key = key;
    this.intent = intent;
    this.record = record;
  }

  @Override
  public String toJson() {
    return null;
  }

  @Override
  public long getPosition() {
    return 0;
  }

  @Override
  public long getSourceRecordPosition() {
    return 0;
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
    return 0;
  }

  @Override
  public RecordType getRecordType() {
    return null;
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
  public int getRecordVersion() {
    return 1;
  }

  @Override
  public ValueType getValueType() {
    return null;
  }

  @Override
  public long getOperationReference() {
    return 0;
  }

  @Override
  public Record<T> copyOf() {
    return this;
  }

  @Override
  public long getKey() {
    return key;
  }

  @Override
  public T getValue() {
    return record;
  }

  @Override
  public int getRequestStreamId() {
    return RecordMetadataEncoder.requestStreamIdNullValue();
  }

  @Override
  public long getRequestId() {
    return RecordMetadataEncoder.requestIdNullValue();
  }

  @Override
  public int getLength() {
    return 0;
  }
}
