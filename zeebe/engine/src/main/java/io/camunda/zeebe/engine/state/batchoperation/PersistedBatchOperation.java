/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.batchoperation;

import io.camunda.search.filter.FilterBase;
import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.DocumentProperty;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.value.LongValue;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import java.util.List;
import org.agrona.DirectBuffer;

public class PersistedBatchOperation extends UnpackedObject implements DbValue {

  private final LongProperty keyProp = new LongProperty("key");
  private final EnumProperty<BatchOperationType> batchOperationTypeProp =
      new EnumProperty<>(
          "batchOperationType", BatchOperationType.class, BatchOperationType.UNSPECIFIED);
  private final EnumProperty<BatchOperationIntent> intentProp =
      new EnumProperty<>("intent", BatchOperationIntent.class);
  private final EnumProperty<BatchOperationState> statusProp =
      new EnumProperty<>("status", BatchOperationState.class);
  private final IntegerProperty offsetProp = new IntegerProperty("offset", 0);
  private final DocumentProperty filterProp = new DocumentProperty("filter");
  private final ArrayProperty<LongValue> keysProp = new ArrayProperty<LongValue>("keys",
      LongValue::new);

  public PersistedBatchOperation() {
    super(7);
    declareProperty(keyProp)
        .declareProperty(batchOperationTypeProp)
        .declareProperty(intentProp)
        .declareProperty(statusProp)
        .declareProperty(offsetProp)
        .declareProperty(filterProp)
        .declareProperty(keysProp);

  }

  public boolean canCancel() {
    return getStatus() == BatchOperationState.CREATED
        || getStatus() == BatchOperationState.ACTIVATED
        || getStatus() == BatchOperationState.PAUSED;
  }

  public boolean canPause() {
    return getStatus() == BatchOperationState.CREATED
        || getStatus() == BatchOperationState.ACTIVATED;
  }

  public boolean canResume() {
    return getStatus() == BatchOperationState.PAUSED;
  }

  public long getKey() {
    return keyProp.getValue();
  }

  public PersistedBatchOperation setKey(final long key) {
    keyProp.setValue(key);
    return this;
  }

  public BatchOperationType getBatchOperationType() {
    return batchOperationTypeProp.getValue();
  }

  public PersistedBatchOperation setBatchOperationType(
      final BatchOperationType batchOperationType) {
    batchOperationTypeProp.setValue(batchOperationType);
    return this;
  }

  public BatchOperationIntent getIntent() {
    return intentProp.getValue();
  }

  public PersistedBatchOperation setIntent(final BatchOperationIntent intent) {
    intentProp.setValue(intent);
    return this;
  }

  public BatchOperationState getStatus() {
    return statusProp.getValue();
  }

  public PersistedBatchOperation setStatus(final BatchOperationState status) {
    statusProp.setValue(status);
    return this;
  }

  public int getOffset() {
    return offsetProp.getValue();
  }

  public PersistedBatchOperation setOffset(final int offset) {
    offsetProp.setValue(offset);
    return this;
  }

  public <T extends FilterBase> T getFilter(final Class<T> clazz) {
    return MsgPackConverter.convertToObject(filterProp.getValue(), clazz);
  }

  public PersistedBatchOperation setFilter(final DirectBuffer filter) {
    filterProp.setValue(filter);
    return this;
  }

  public List<Long> getKeys() {
    return keysProp.stream().map(LongValue::getValue).toList();
  }

  public PersistedBatchOperation appendKeys(final List<Long> keys) {
    keysProp.reset();
    keys.forEach(key -> keysProp.add().setValue(key));
    return this;
  }

  public List<Long> getNextKeys(final int offset, final int maxBatchSize) {
    return keysProp.stream()
        .skip(offset)
        .limit(maxBatchSize)
        .map(LongValue::getValue)
        .toList();
  }

  public DirectBuffer getFilterBuffer() {
    return filterProp.getValue();
  }

  public enum BatchOperationState {
    CREATED,
    ACTIVATED,
    PAUSED,
    CANCELED,
    COMPLETED
  }
}
