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
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationExecutionRecord;
import io.camunda.zeebe.protocol.record.intent.BatchOperationExecutionIntent;

public class BatchOperationExecutingApplier
    implements TypedEventApplier<BatchOperationExecutionIntent, BatchOperationExecutionRecord> {

  private final MutableBatchOperationState batchOperationState;

  public BatchOperationExecutingApplier(final MutableBatchOperationState batchOperationState) {
    this.batchOperationState = batchOperationState;
  }

  @Override
  public void applyState(final long key, final BatchOperationExecutionRecord value) {
    batchOperationState.removeItemKeys(value.getBatchOperationKey(), value.getItemKeys());
  }
}
