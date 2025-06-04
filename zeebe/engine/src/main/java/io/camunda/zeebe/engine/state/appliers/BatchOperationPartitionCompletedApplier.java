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

/** This applier only runs on the lead partition of a batch operation. */
public class BatchOperationPartitionCompletedApplier
    implements TypedEventApplier<BatchOperationIntent, BatchOperationPartitionLifecycleRecord> {

  private final MutableBatchOperationState batchOperationState;
  private final int partitionId;

  public BatchOperationPartitionCompletedApplier(
      final MutableBatchOperationState batchOperationState, final int partitionId) {
    this.batchOperationState = batchOperationState;
    this.partitionId = partitionId;
  }

  @Override
  public void applyState(
      final long batchOperationKey, final BatchOperationPartitionLifecycleRecord value) {
    if (isOnLeadPartition(batchOperationKey)) {
      batchOperationState.completePartition(batchOperationKey, value.getSourcePartitionId());
    } else {
      batchOperationState.complete(batchOperationKey);
    }
  }

  /**
   * Check, if this applier is running on the lead partition of the batch operation.
   *
   * @param batchOperationKey the key of the batch operation
   * @return true if this applier is running on the lead partition, false otherwise
   */
  private boolean isOnLeadPartition(final long batchOperationKey) {
    return Protocol.decodePartitionId(batchOperationKey) == partitionId;
  }
}
