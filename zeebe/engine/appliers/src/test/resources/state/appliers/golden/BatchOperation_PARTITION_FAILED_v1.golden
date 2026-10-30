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

/**
 * This applier can do two things:
 *
 * <ul>
 *   <li>If the applier is running on the lead partition of the batch operation, it will mark the
 *       <code>sourcePartitionId</code> as failed with an error message
 *   <li>If the applier is running on a non-lead partition, it will mark the batch operation locally
 *       as finished, which means it will delete the batch operation from the RocksDB state.
 * </ul>
 */
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
    if (isOnLeadPartition(value.getBatchOperationKey())) {
      // mark the source partition as failed with an error message
      batchOperationState.failPartition(
          value.getBatchOperationKey(), value.getSourcePartitionId(), value.getError());
    } else {
      // mark the batch operation as completed locally => delete it from rocksDb
      batchOperationState.complete(value.getBatchOperationKey());
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
