/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableBatchOperationState;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationPartitionLifecycleRecord;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;

public class BatchOperationPartitionFailedApplier
    implements TypedEventApplier<BatchOperationIntent, BatchOperationPartitionLifecycleRecord> {

  private final MutableBatchOperationState batchOperationState;
  private final int partitionId;

  public BatchOperationPartitionFailedApplier(
      final MutableBatchOperationState batchOperationState, final int partitionId) {
    this.batchOperationState = batchOperationState;
    this.partitionId = partitionId;
  }

  @Override
  public void applyState(final long recordKey, final BatchOperationPartitionLifecycleRecord value) {
    if (Protocol.decodePartitionId(value.getBatchOperationKey()) == partitionId) {
      batchOperationState.finishPartition(
          value.getBatchOperationKey(), value.getSourcePartitionId());
    } else {
      batchOperationState.complete(value.getBatchOperationKey());
    }
  }
}
