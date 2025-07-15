/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.batchoperation;

import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.BatchOperationPartitionLifecycleRecordValue;

public final class BatchOperationPartitionLifecycleRecord extends UnifiedRecordValue
    implements BatchOperationPartitionLifecycleRecordValue {

  public static final String PROP_BATCH_OPERATION_KEY = "batchOperationKey";
  public static final String PROP_SOURCE_PARTITION_ID = "sourcePartitionId";

  private final LongProperty batchOperationKeyProp = new LongProperty(PROP_BATCH_OPERATION_KEY);
  private final IntegerProperty sourcePartitionIdProp =
      new IntegerProperty(PROP_SOURCE_PARTITION_ID, -1);
  private final ObjectProperty<BatchOperationError> errorProp =
      new ObjectProperty<>("error", new BatchOperationError());

  public BatchOperationPartitionLifecycleRecord() {
    super(3);
    declareProperty(batchOperationKeyProp);
    declareProperty(sourcePartitionIdProp);
    declareProperty(errorProp);
  }

  @Override
  public long getBatchOperationKey() {
    return batchOperationKeyProp.getValue();
  }

  public BatchOperationPartitionLifecycleRecord setBatchOperationKey(final Long batchOperationKey) {
    batchOperationKeyProp.reset();
    batchOperationKeyProp.setValue(batchOperationKey);
    return this;
  }

  @Override
  public Integer getSourcePartitionId() {
    return sourcePartitionIdProp.getValue();
  }

  public BatchOperationPartitionLifecycleRecord setSourcePartitionId(final int sourcePartitionId) {
    sourcePartitionIdProp.reset();
    sourcePartitionIdProp.setValue(sourcePartitionId);
    return this;
  }

  public BatchOperationError getError() {
    return errorProp.getValue();
  }

  public BatchOperationPartitionLifecycleRecord setError(final BatchOperationError error) {
    errorProp.getValue().wrap(error);
    return this;
  }

  public BatchOperationPartitionLifecycleRecord wrap(
      final BatchOperationPartitionLifecycleRecord record) {
    setBatchOperationKey(record.getBatchOperationKey());
    setSourcePartitionId(record.getSourcePartitionId());
    setError(record.getError());
    return this;
  }
}
