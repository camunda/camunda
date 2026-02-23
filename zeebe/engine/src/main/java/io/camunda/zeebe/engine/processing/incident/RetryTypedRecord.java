/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.incident;

import io.camunda.zeebe.protocol.impl.encoding.AuthInfo;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.Agent;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordMetadataEncoder;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.Map;

/**
 * A minimal implementation of {@link TypedRecord} used to reprocess a command, typically after an
 * incident has been resolved.
 *
 * <p>This class is designed to handle different types of records and their corresponding intents,
 * allowing seamless recovery by re-processing the failed command.
 *
 * <p>Consumers can use the type of this class to identify and handle retried commands differently,
 * if special logic is needed during reprocessing.
 *
 * @param <T> the type of the record value being retried
 */
public final class RetryTypedRecord<T extends UnifiedRecordValue> implements TypedRecord<T> {

  private final long key;
  private final Intent intent;
  private final T record;

  RetryTypedRecord(final long key, final Intent intent, final T record) {
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
    return RecordType.COMMAND;
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
    return null;
  }

  @Override
  public long getOperationReference() {
    return 0;
  }

  @Override
  public long getBatchOperationReference() {
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
  public AuthInfo getAuthInfo() {
    return null;
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
