/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.batchoperation;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.BinaryProperty;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.value.LongValue;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import java.util.Comparator;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class PersistedBatchOperation extends UnpackedObject implements DbValue {

  private final LongProperty keyProp = new LongProperty("key");
  private final EnumProperty<BatchOperationType> batchOperationTypeProp =
      new EnumProperty<>(
          "batchOperationType", BatchOperationType.class, BatchOperationType.UNSPECIFIED);
  private final EnumProperty<BatchOperationStatus> statusProp =
      new EnumProperty<>("status", BatchOperationStatus.class);
  private final BinaryProperty entityFilterProp = new BinaryProperty("entityFilter");
  private final ArrayProperty<LongValue> chunkKeysProp =
      new ArrayProperty<>("chunkKeys", LongValue::new);

  public PersistedBatchOperation() {
    super(5);
    declareProperty(keyProp)
        .declareProperty(batchOperationTypeProp)
        .declareProperty(statusProp)
        .declareProperty(entityFilterProp)
        .declareProperty(chunkKeysProp);
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

  public BatchOperationStatus getStatus() {
    return statusProp.getValue();
  }

  public PersistedBatchOperation setStatus(final BatchOperationStatus status) {
    statusProp.setValue(status);
    return this;
  }

  public String getEntityFilter() {
    return MsgPackConverter.convertToJson(entityFilterProp.getValue());
  }

  public PersistedBatchOperation setEntityFilter(final DirectBuffer filter) {
    entityFilterProp.setValue(new UnsafeBuffer(filter));
    return this;
  }

  public <T> T getEntityFilter(final Class<T> clazz) {
    return MsgPackConverter.convertToObject(entityFilterProp.getValue(), clazz);
  }

  public long nextBlockKey() {
    final var nextKey = getMaxBlockKey() + 1;
    chunkKeysProp.add().setValue(nextKey);

    return nextKey;
  }

  public PersistedBatchOperation removeBlockKey(final Long blockKey) {
    final var newKeys =
        chunkKeysProp.stream().map(LongValue::getValue).filter(k -> !k.equals(blockKey)).toList();

    chunkKeysProp.reset();

    for (final var key : newKeys) {
      chunkKeysProp.add().setValue(key);
    }

    return this;
  }

  public long getMinBlockKey() {
    return chunkKeysProp.stream()
        .min(Comparator.comparing(LongValue::getValue))
        .map(LongValue::getValue)
        .orElse(-1L);
  }

  public long getMaxBlockKey() {
    return chunkKeysProp.stream()
        .max(Comparator.comparing(LongValue::getValue))
        .map(LongValue::getValue)
        .orElse(-1L);
  }

  public enum BatchOperationStatus {
    CREATED,
    ACTIVATED,
    PAUSED,
    CANCELED,
    COMPLETED
  }
}
