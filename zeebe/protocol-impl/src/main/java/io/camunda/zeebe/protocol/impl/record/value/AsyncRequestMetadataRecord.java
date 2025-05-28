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
import io.camunda.zeebe.protocol.record.value.AsyncRequestMetadataRecordValue;

public final class AsyncRequestMetadataRecord extends UnifiedRecordValue
    implements AsyncRequestMetadataRecordValue {

  private final LongProperty requestKeyProperty = new LongProperty("requestKey", -1);
  private final EnumProperty<ValueType> valueTypeProperty =
      new EnumProperty<>("valueType", ValueType.class, ValueType.NULL_VAL);
  private final IntegerProperty intentProperty = new IntegerProperty("intent", Intent.NULL_VAL);
  private final LongProperty requestIdProperty = new LongProperty("requestId", -1);
  private final IntegerProperty requestStreamIdProperty =
      new IntegerProperty("requestStreamId", -1);
  private final LongProperty operationReferenceProperty =
      new LongProperty("operationReference", -1);

  public AsyncRequestMetadataRecord() {
    super(6);
    declareProperty(requestKeyProperty)
        .declareProperty(valueTypeProperty)
        .declareProperty(intentProperty)
        .declareProperty(requestIdProperty)
        .declareProperty(requestStreamIdProperty)
        .declareProperty(operationReferenceProperty);
  }

  public void wrap(final AsyncRequestMetadataRecord record) {
    requestKeyProperty.setValue(record.getRequestKey());
    valueTypeProperty.setValue(record.getValueType());
    intentProperty.setValue(record.getIntent().value());
    requestIdProperty.setValue(record.getRequestId());
    requestStreamIdProperty.setValue(record.getRequestStreamId());
    operationReferenceProperty.setValue(record.getOperationReference());
  }

  @Override
  public long getRequestKey() {
    return requestKeyProperty.getValue();
  }

  public AsyncRequestMetadataRecord setRequestKey(final long requestKey) {
    requestKeyProperty.setValue(requestKey);
    return this;
  }

  @Override
  public ValueType getValueType() {
    return valueTypeProperty.getValue();
  }

  public AsyncRequestMetadataRecord setValueType(final ValueType valueType) {
    valueTypeProperty.setValue(valueType);
    return this;
  }

  @Override
  public Intent getIntent() {
    return getIntent(intentProperty.getValue());
  }

  public AsyncRequestMetadataRecord setIntent(final Intent intent) {
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

  public AsyncRequestMetadataRecord setRequestId(final long requestId) {
    requestIdProperty.setValue(requestId);
    return this;
  }

  @Override
  public int getRequestStreamId() {
    return requestStreamIdProperty.getValue();
  }

  public AsyncRequestMetadataRecord setRequestStreamId(final int requestStreamId) {
    requestStreamIdProperty.setValue(requestStreamId);
    return this;
  }

  @Override
  public long getOperationReference() {
    return operationReferenceProperty.getValue();
  }

  public AsyncRequestMetadataRecord setOperationReference(final long operationReference) {
    operationReferenceProperty.setValue(operationReference);
    return this;
  }
}
