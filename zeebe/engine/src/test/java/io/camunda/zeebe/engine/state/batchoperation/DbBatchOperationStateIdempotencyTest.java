/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.batchoperation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.db.ZeebeDbInconsistentException;
import io.camunda.zeebe.engine.util.ProcessingStateRule;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationCreationRecord;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import java.util.Set;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Rule;
import org.junit.Test;

public class DbBatchOperationStateIdempotencyTest {

  @Rule public final ProcessingStateRule stateRule = new ProcessingStateRule();

  // SHOULD reproduce ZeebeDbInconsistentException
  // when fix on DbBatchOperationState#createNewChunk lines 363-370 are commented out
//  @Test
//  public void shouldThrowZeebeDbInconsistentExceptionWhenChunkAlreadyExists() {
//    final long batchOperationKey = 12345L;
//    final var creationRecord = new BatchOperationCreationRecord();
//    creationRecord.setBatchOperationKey(batchOperationKey);
//    creationRecord.setBatchOperationType(BatchOperationType.CANCEL_PROCESS_INSTANCE);
//    creationRecord.setEntityFilter(new UnsafeBuffer(new byte[0]));
//
//    final var batchOperationState = stateRule.getProcessingState().getBatchOperationState();
//    batchOperationState.create(batchOperationKey, creationRecord);
//
//    final var itemKeys = Set.of(1L, 2L, 3L);
//    // min chunk key --> -1, current chunk key --> 0
//    batchOperationState.appendItemKeys(batchOperationKey, itemKeys);
//
//    final var batch = batchOperationState.get(batchOperationKey);
//    assertThat(batch).isPresent();
//    assertThat(batch.get().hasChunks()).isTrue();
//
//    // mimic restart, chunkKeysProp empty
//    // min chunk key --> -1, current chunk key --> 0
//    batchOperationState.removeChunkAndBatchFromState(batchOperationKey, itemKeys);
//
//    assertThatThrownBy(() -> batchOperationState.appendItemKeys(batchOperationKey, itemKeys))
//        .isInstanceOf(ZeebeDbInconsistentException.class)
//        .hasMessageContaining("already exists")
//        .hasMessageContaining("BATCH_OPERATION_CHUNKS");
//  }

  // SHOULD PASS AFTER `createNewChunk` FIX
  @Test
  public void shouldReturnChunkIfAlreadyExists() {
    final long batchOperationKey = 12345L;
    final var creationRecord = new BatchOperationCreationRecord();
    creationRecord.setBatchOperationKey(batchOperationKey);
    creationRecord.setBatchOperationType(BatchOperationType.CANCEL_PROCESS_INSTANCE);
    creationRecord.setEntityFilter(new UnsafeBuffer(new byte[0]));

    final var batchOperationState = stateRule.getProcessingState().getBatchOperationState();
    batchOperationState.create(batchOperationKey, creationRecord);

    final var itemKeys = Set.of(1L, 2L, 3L);
    // min chunk key --> -1, current chunk key --> 0
    batchOperationState.appendItemKeys(batchOperationKey, itemKeys);

    var batch = batchOperationState.get(batchOperationKey);
    assertThat(batch).isPresent();
    assertThat(batch.get().hasChunks()).isTrue();

    // mimic restart, chunkKeysProp empty
    // min chunk key --> -1, current chunk key --> 0
    batchOperationState.removeChunkAndBatchFromState(batchOperationKey, itemKeys);
    batch = batchOperationState.get(batchOperationKey);
    assertThat(batch).isPresent();
    assertThat(batch.get().hasChunks()).isFalse();

    batchOperationState.appendItemKeys(batchOperationKey, itemKeys);
    batch = batchOperationState.get(batchOperationKey);
    assertThat(batch).isPresent();
    assertThat(batch.get().hasChunks()).isTrue();
  }

}

