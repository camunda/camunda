/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value;

import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.value.RequestMetadataRecordValue;

/**
 * Represents metadata about an incoming request that triggered an element transition. Includes the
 * scope key (the key of the element the request was received for), stream ID, request ID,
 * triggering intent, value type, and optionally the operation reference.
 *
 * <p>This metadata is especially useful in scenarios where the normal record flow is paused, for
 * example, due to the need to handle user task listener jobs. In such cases, this metadata ensures
 * that the final terminal event (e.g., {@code PROCESS_INSTANCE.ELEMENT_TERMINATED}, {@code
 * VARIABLE_DOCUMENT.UPDATED}) can still be emitted with the originally provided {@code
 * operationReference}. This enables external tools to reliably correlate outcomes with the initial
 * triggering operation.
 */
public class RequestMetadataRecord extends UnifiedRecordValue
    implements RequestMetadataRecordValue {

  private final LongProperty metadataKeyProperty = new LongProperty("metadataKey", -1);
  private final LongProperty scopeKeyProperty = new LongProperty("scopeKey", -1);
  private final EnumProperty<ValueType> valueTypeProperty =
      new EnumProperty<>("valueType", ValueType.class, ValueType.NULL_VAL);
  private final IntegerProperty intentProperty = new IntegerProperty("intent", Intent.NULL_VAL);
  private final LongProperty requestIdProperty = new LongProperty("requestId", -1);
  private final IntegerProperty requestStreamIdProperty =
      new IntegerProperty("requestStreamId", -1);
  private final LongProperty operationReferenceProperty =
      new LongProperty("operationReference", -1);

  public RequestMetadataRecord() {
    super(7);
    declareProperty(metadataKeyProperty)
        .declareProperty(scopeKeyProperty)
        .declareProperty(valueTypeProperty)
        .declareProperty(intentProperty)
        .declareProperty(requestIdProperty)
        .declareProperty(requestStreamIdProperty)
        .declareProperty(operationReferenceProperty);
  }

  public void wrap(final RequestMetadataRecord record) {
    metadataKeyProperty.setValue(record.getMetadataKey());
    scopeKeyProperty.setValue(record.getScopeKey());
    valueTypeProperty.setValue(record.getValueType());
    intentProperty.setValue(record.getIntent().value());
    requestIdProperty.setValue(record.getRequestId());
    requestStreamIdProperty.setValue(record.getRequestStreamId());
    operationReferenceProperty.setValue(record.getOperationReference());
  }

  @Override
  public long getMetadataKey() {
    return metadataKeyProperty.getValue();
  }

  public RequestMetadataRecord setMetadataKey(final long metadataKey) {
    metadataKeyProperty.setValue(metadataKey);
    return this;
  }

  @Override
  public long getScopeKey() {
    return scopeKeyProperty.getValue();
  }

  public RequestMetadataRecord setScopeKey(final long scopeKey) {
    scopeKeyProperty.setValue(scopeKey);
    return this;
  }

  @Override
  public ValueType getValueType() {
    return valueTypeProperty.getValue();
  }

  public RequestMetadataRecord setValueType(final ValueType valueType) {
    valueTypeProperty.setValue(valueType);
    return this;
  }

  @Override
  public Intent getIntent() {
    return getIntent(intentProperty.getValue());
  }

  public RequestMetadataRecord setIntent(final Intent intent) {
    intentProperty.setValue(intent.value());
    return this;
  }

  private Intent getIntent(final int intentValue) {
    if (intentValue < 0 || intentValue > Short.MAX_VALUE) {
      throw new IllegalStateException(
          String.format(
              "Expected to read the intent, but it's persisted value '%d' is not a short integer",
              intentValue));
    }
    return Intent.fromProtocolValue(getValueType(), (short) intentValue);
  }

  @Override
  public long getRequestId() {
    return requestIdProperty.getValue();
  }

  public RequestMetadataRecord setRequestId(final long requestId) {
    requestIdProperty.setValue(requestId);
    return this;
  }

  @Override
  public int getRequestStreamId() {
    return requestStreamIdProperty.getValue();
  }

  public RequestMetadataRecord setRequestStreamId(final int requestStreamId) {
    requestStreamIdProperty.setValue(requestStreamId);
    return this;
  }

  @Override
  public long getOperationReference() {
    return operationReferenceProperty.getValue();
  }

  public RequestMetadataRecord setOperationReference(final long operationReference) {
    operationReferenceProperty.setValue(operationReference);
    return this;
  }

  public boolean hasOperationReference() {
    return operationReferenceProperty.getValue() != -1;
  }
}
