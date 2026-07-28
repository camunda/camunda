/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.batchoperation;

import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.BatchOperationLifecycleManagementRecordValue;
import io.camunda.zeebe.protocol.record.value.scaling.BatchOperationErrorValue;
import java.util.List;

public final class BatchOperationLifecycleManagementRecord extends UnifiedRecordValue
    implements BatchOperationLifecycleManagementRecordValue {

  public static final String PROP_BATCH_OPERATION_KEY = "batchOperationKey";
  public static final String PROP_STORAGE_ORDINAL_KEY = "storageOrdinalKey";

  private final LongProperty batchOperationKeyProp = new LongProperty(PROP_BATCH_OPERATION_KEY);
  private final IntegerProperty storageOrdinalKeyProp =
      new IntegerProperty(PROP_STORAGE_ORDINAL_KEY, 0);

  private final ArrayProperty<BatchOperationError> errorsProp =
      new ArrayProperty<>("errors", BatchOperationError::new);

  public BatchOperationLifecycleManagementRecord() {
    super(3);
    declareProperty(batchOperationKeyProp);
    declareProperty(storageOrdinalKeyProp);
    declareProperty(errorsProp);
  }

  @Override
  public long getBatchOperationKey() {
    return batchOperationKeyProp.getValue();
  }

  public BatchOperationLifecycleManagementRecord setBatchOperationKey(
      final Long batchOperationKey) {
    batchOperationKeyProp.reset();
    batchOperationKeyProp.setValue(batchOperationKey);
    return this;
  }

  @Override
  public int getStorageOrdinalKey() {
    return storageOrdinalKeyProp.getValue();
  }

  public BatchOperationLifecycleManagementRecord setStorageOrdinalKey(final int storageOrdinalKey) {
    storageOrdinalKeyProp.setValue(storageOrdinalKey);
    return this;
  }

  public BatchOperationLifecycleManagementRecord wrap(
      final BatchOperationLifecycleManagementRecord record) {
    setBatchOperationKey(record.getBatchOperationKey());
    setStorageOrdinalKey(record.getStorageOrdinalKey());
    setErrors(record.getErrors().stream().map(BatchOperationError.class::cast).toList());
    return this;
  }

  @Override
  public List<BatchOperationErrorValue> getErrors() {
    return errorsProp.stream().map(BatchOperationErrorValue.class::cast).toList();
  }

  public BatchOperationLifecycleManagementRecord setErrors(final List<BatchOperationError> errors) {
    errorsProp.reset();
    for (final var error : errors) {
      errorsProp.add().wrap(error);
    }
    return this;
  }
}
