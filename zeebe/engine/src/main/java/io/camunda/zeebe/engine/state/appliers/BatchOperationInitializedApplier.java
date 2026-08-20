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
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationInitializationRecord;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;

public class BatchOperationInitializedApplier
    implements TypedEventApplier<BatchOperationIntent, BatchOperationInitializationRecord> {

  private final MutableBatchOperationState batchOperationState;

  public BatchOperationInitializedApplier(final MutableBatchOperationState batchOperationState) {
    this.batchOperationState = batchOperationState;
  }

  @Override
  public void applyState(
      final long batchOperationKey, final BatchOperationInitializationRecord value) {
    batchOperationState.transitionToInitialized(batchOperationKey);
  }
}
