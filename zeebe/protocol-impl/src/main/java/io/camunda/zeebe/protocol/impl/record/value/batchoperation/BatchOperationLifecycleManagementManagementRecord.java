/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.batchoperation;

import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.BatchOperationLifecycleManagementRecordValue;

public final class BatchOperationLifecycleManagementManagementRecord extends UnifiedRecordValue
    implements BatchOperationLifecycleManagementRecordValue {

  public static final String PROP_BATCH_OPERATION_KEY = "batchOperationKey";

  private final LongProperty batchOperationKeyProp = new LongProperty(PROP_BATCH_OPERATION_KEY);

  public BatchOperationLifecycleManagementManagementRecord() {
    super(1);
    declareProperty(batchOperationKeyProp);
  }

  @Override
  public long getBatchOperationKey() {
    return batchOperationKeyProp.getValue();
  }

  public BatchOperationLifecycleManagementManagementRecord setBatchOperationKey(
      final Long batchOperationKey) {
    batchOperationKeyProp.reset();
    batchOperationKeyProp.setValue(batchOperationKey);
    return this;
  }

  public BatchOperationLifecycleManagementManagementRecord wrap(
      final BatchOperationLifecycleManagementManagementRecord record) {
    setBatchOperationKey(record.getBatchOperationKey());
    return this;
  }
}
