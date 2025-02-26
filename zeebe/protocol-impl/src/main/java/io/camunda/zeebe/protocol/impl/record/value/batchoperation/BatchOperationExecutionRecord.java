/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.batchoperation;

import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.value.LongValue;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.BatchOperationCreationRecordValue;
import io.camunda.zeebe.protocol.record.value.BatchOperationExecutionRecordValue;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import java.util.Set;
import java.util.stream.Collectors;

public final class BatchOperationExecutionRecord extends UnifiedRecordValue
    implements BatchOperationExecutionRecordValue {

  public static final String PROP_BATCH_OPERATION_KEY = "batchOperationKey";
  public static final String PROP_OFFSET = "offset";
  public static final String PROP_KEY_LIST = "keys";
  public static final String PROP_BATCH_OPERATION_TYPE = "batchOperationType";

  private final LongProperty batchOperationKeyProp =
      new LongProperty(PROP_BATCH_OPERATION_KEY);
  private final IntegerProperty offsetProp =
      new IntegerProperty(PROP_OFFSET, 0);
  private final ArrayProperty<LongValue> keysProp =
      new ArrayProperty<>(PROP_KEY_LIST, LongValue::new);
  private final EnumProperty<BatchOperationType> batchOperationTypeProp =
      new EnumProperty<>(
          PROP_BATCH_OPERATION_TYPE, BatchOperationType.class, BatchOperationType.UNSPECIFIED);

  public BatchOperationExecutionRecord() {
    super(4);
    declareProperty(batchOperationKeyProp)
        .declareProperty(offsetProp)
        .declareProperty(keysProp)
        .declareProperty(batchOperationTypeProp);
  }

  @Override
  public Long getBatchOperationKey() {
    return batchOperationKeyProp.getValue();
  }

  public void setBatchOperationKey(Long batchOperationKey) {
    batchOperationKeyProp.reset();
    batchOperationKeyProp.setValue(batchOperationKey);
  }

  @Override
  public Integer getOffset() {
    return offsetProp.getValue();
  }

  public void setOffset(Integer offset) {
    offsetProp.reset();
    offsetProp.setValue(offset);
  }

  @Override
  public Set<Long> getKeys() {
    return keysProp.stream()
        .map(LongValue::getValue)
        .collect(Collectors.toSet());
  }

  public BatchOperationExecutionRecord setKeys(final Set<Long> keys) {
    keysProp.reset();
    keys.forEach(key -> keysProp.add().setValue(key));
    return this;
  }

  @Override
  public BatchOperationType getBatchOperationType() {
    return batchOperationTypeProp.getValue();
  }

  public BatchOperationExecutionRecord setBatchOperationType(final BatchOperationType batchOperationType) {
    batchOperationTypeProp.setValue(batchOperationType);
    return this;
  }

  public void wrap(final BatchOperationExecutionRecord record) {
    setKeys(record.getKeys());
    setBatchOperationType(record.getBatchOperationType());
  }

}
