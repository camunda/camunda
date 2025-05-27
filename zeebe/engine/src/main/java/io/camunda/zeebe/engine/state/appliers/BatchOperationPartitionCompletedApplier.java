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
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationPartitionLifecycleRecord;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;

/** This applier only runs on the lead partition of a batch operation. */
public class BatchOperationPartitionCompletedApplier
    implements TypedEventApplier<BatchOperationIntent, BatchOperationPartitionLifecycleRecord> {

  private final MutableBatchOperationState batchOperationState;

  public BatchOperationPartitionCompletedApplier(
      final MutableBatchOperationState batchOperationState) {
    this.batchOperationState = batchOperationState;
  }

  @Override
  public void applyState(
      final long batchOperationKey, final BatchOperationPartitionLifecycleRecord value) {
    batchOperationState.completePartition(batchOperationKey, value.getSourcePartitionId());
  }
}
