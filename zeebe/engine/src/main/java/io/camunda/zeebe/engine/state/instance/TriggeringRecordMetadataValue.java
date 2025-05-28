/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.instance;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.engine.state.TriggeringRecordMetadata;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;

public class TriggeringRecordMetadataValue extends UnpackedObject implements DbValue {

  private final EnumProperty<ValueType> valueTypeProperty =
      new EnumProperty<>("valueType", ValueType.class, ValueType.NULL_VAL);
  private final IntegerProperty intentProperty = new IntegerProperty("intent", Intent.NULL_VAL);
  private final LongProperty requestIdProperty = new LongProperty("requestId", -1);
  private final IntegerProperty requestStreamIdProperty =
      new IntegerProperty("requestStreamId", -1);
  private final LongProperty operationReferenceProperty =
      new LongProperty("operationReference", -1);

  public TriggeringRecordMetadataValue() {
    super(5);
    declareProperty(valueTypeProperty)
        .declareProperty(intentProperty)
        .declareProperty(requestIdProperty)
        .declareProperty(requestStreamIdProperty)
        .declareProperty(operationReferenceProperty);
  }

  public TriggeringRecordMetadataValue from(final TriggeringRecordMetadata metadata) {
    return new TriggeringRecordMetadataValue()
        .setValueType(metadata.getValueType())
        .setIntent(metadata.getIntent())
        .setRequestId(metadata.getRequestId())
        .setRequestStreamId(metadata.getRequestStreamId())
        .setOperationReference(metadata.getOperationReference());
  }

  public void wrap(final TriggeringRecordMetadata metadata) {
    setValueType(metadata.getValueType())
        .setIntent(metadata.getIntent())
        .setRequestId(metadata.getRequestId())
        .setRequestStreamId(metadata.getRequestStreamId())
        .setOperationReference(metadata.getOperationReference());
  }

  public ValueType getValueType() {
    return valueTypeProperty.getValue();
  }

  public TriggeringRecordMetadataValue setValueType(final ValueType valueType) {
    valueTypeProperty.setValue(valueType);
    return this;
  }

  public Intent getIntent() {
    return getIntent(intentProperty.getValue());
  }

  public TriggeringRecordMetadataValue setIntent(final Intent intent) {
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

  public long getRequestId() {
    return requestIdProperty.getValue();
  }

  public TriggeringRecordMetadataValue setRequestId(final long requestId) {
    requestIdProperty.setValue(requestId);
    return this;
  }

  public int getRequestStreamId() {
    return requestStreamIdProperty.getValue();
  }

  public TriggeringRecordMetadataValue setRequestStreamId(final int requestStreamId) {
    requestStreamIdProperty.setValue(requestStreamId);
    return this;
  }

  public long getOperationReference() {
    return operationReferenceProperty.getValue();
  }

  public TriggeringRecordMetadataValue setOperationReference(final long operationReference) {
    operationReferenceProperty.setValue(operationReference);
    return this;
  }

  public boolean hasOperationReference() {
    return operationReferenceProperty.getValue() != -1;
  }
}
