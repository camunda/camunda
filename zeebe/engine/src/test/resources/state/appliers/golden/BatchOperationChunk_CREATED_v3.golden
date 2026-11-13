/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.batchoperation.PersistedBatchOperationChunk.ChunkItem;
import io.camunda.zeebe.engine.state.mutable.MutableBatchOperationState;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationChunkRecord;
import io.camunda.zeebe.protocol.record.intent.BatchOperationChunkIntent;

public class BatchOperationChunkCreatedV3Applier
    implements TypedEventApplier<BatchOperationChunkIntent, BatchOperationChunkRecord> {

  private final MutableBatchOperationState batchOperationState;

  public BatchOperationChunkCreatedV3Applier(final MutableBatchOperationState batchOperationState) {
    this.batchOperationState = batchOperationState;
  }

  @Override
  public void applyState(final long chunkKey, final BatchOperationChunkRecord value) {
    batchOperationState.addChunk(
        chunkKey,
        value.getBatchOperationKey(),
        value.getItems().stream()
            .map(item -> new ChunkItem(item.getItemKey(), item.getStorageOrdinal()))
            .distinct()
            .toList());
  }
}
