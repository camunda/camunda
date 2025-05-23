/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state;

import io.camunda.zeebe.engine.state.instance.TriggeringRecordMetadataValue;
import io.camunda.zeebe.protocol.record.RecordMetadataEncoder;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.Objects;

/** Represents a metadata of record(command) that triggered the transition */
public final class TriggeringRecordMetadata {

  private static final TriggeringRecordMetadata EMPTY = new TriggeringRecordMetadata();

  private ValueType valueType = ValueType.NULL_VAL;
  private Intent intent = Intent.UNKNOWN;
  private long requestId = RecordMetadataEncoder.requestIdNullValue();
  private int requestStreamId = RecordMetadataEncoder.requestStreamIdNullValue();
  private long operationReference = RecordMetadataEncoder.operationReferenceNullValue();

  public ValueType getValueType() {
    return valueType;
  }

  public Intent getIntent() {
    return intent;
  }

  public long getRequestId() {
    return requestId;
  }

  public int getRequestStreamId() {
    return requestStreamId;
  }

  public long getOperationReference() {
    return operationReference;
  }

  public boolean isEmpty() {
    return this.equals(EMPTY);
  }

  public static TriggeringRecordMetadata from(final TypedRecord<?> record) {
    final var metadata = new TriggeringRecordMetadata();
    metadata.valueType = record.getValueType();
    metadata.intent = record.getIntent();
    metadata.requestId = record.getRequestId();
    metadata.requestStreamId = record.getRequestStreamId();
    metadata.operationReference = record.getOperationReference();
    return metadata;
  }

  public static TriggeringRecordMetadata from(final TriggeringRecordMetadataValue value) {
    final var metadata = new TriggeringRecordMetadata();
    metadata.valueType = value.getValueType();
    metadata.intent = value.getIntent();
    metadata.requestId = value.getRequestId();
    metadata.requestStreamId = value.getRequestStreamId();
    metadata.operationReference = value.getOperationReference();
    return metadata;
  }

  public static TriggeringRecordMetadata empty() {
    return EMPTY;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof final TriggeringRecordMetadata that)) {
      return false;
    }
    return getRequestId() == that.getRequestId()
        && getRequestStreamId() == that.getRequestStreamId()
        && getOperationReference() == that.getOperationReference()
        && getValueType() == that.getValueType()
        && Objects.equals(getIntent(), that.getIntent());
  }
}
