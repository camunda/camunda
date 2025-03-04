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
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.value.LongValue;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.BatchOperationCreationRecordValue;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import java.util.Set;
import java.util.stream.Collectors;

public final class BatchOperationCreationRecord extends UnifiedRecordValue
    implements BatchOperationCreationRecordValue {

  public static final String PROP_BATCH_OPERATION_KEY = "batchOperationKey";
  public static final String PROP_KEY_LIST = "keys";
  public static final String PROP_BATCH_OPERATION_TYPE = "batchOperationType";

  private final LongProperty batchOperationKeyProp =
      new LongProperty(PROP_BATCH_OPERATION_KEY, -1);
  private final ArrayProperty<LongValue> keysProp =
      new ArrayProperty<>(PROP_KEY_LIST, LongValue::new);
  private final EnumProperty<BatchOperationType> batchOperationTypeProp =
      new EnumProperty<>(
          PROP_BATCH_OPERATION_TYPE, BatchOperationType.class, BatchOperationType.UNSPECIFIED);

  public BatchOperationCreationRecord() {
    super(2);
    declareProperty(batchOperationKeyProp)
        .declareProperty(keysProp)
        .declareProperty(batchOperationTypeProp);
  }

  @Override
  public Long getBatchOperationKey() {
    return batchOperationKeyProp.getValue();
  }

  public void setBatchOperationKey(final Long batchOperationKey) {
    batchOperationKeyProp.reset();
    batchOperationKeyProp.setValue(batchOperationKey);
  }

  @Override
  public Set<Long> getKeys() {
    return keysProp.stream()
        .map(LongValue::getValue)
        .collect(Collectors.toSet());
  }

  public BatchOperationCreationRecord setKeys(final Set<Long> keys) {
    keysProp.reset();
    keys.forEach(key -> keysProp.add().setValue(key));
    return this;
  }

  @Override
  public BatchOperationType getBatchOperationType() {
    return batchOperationTypeProp.getValue();
  }

  public BatchOperationCreationRecord setBatchOperationType(final BatchOperationType batchOperationType) {
    batchOperationTypeProp.setValue(batchOperationType);
    return this;
  }

  public void wrap(final BatchOperationCreationRecord record) {
    setKeys(record.getKeys());
    setBatchOperationType(record.getBatchOperationType());
  }

}
