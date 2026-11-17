/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.zeebe.engine.state.mutable.MutableBatchOperationState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationCreationRecord;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Exercises the real {@link EventAppliers#applyState} version dispatch for a batch operation
 * CREATED record against real state: a record written with version 2 predates the storage ordinal
 * and is stored with the default 0, a record written with version 3 stores its ordinal.
 */
@ExtendWith(ProcessingStateExtension.class)
final class BatchOperationCreatedReplayVersionDispatchTest {

  private static final long BATCH_OPERATION_KEY = 42L;
  private static final int STORAGE_ORDINAL = 1234;

  private MutableProcessingState processingState;
  private MutableBatchOperationState batchOperationState;
  private EventAppliers eventAppliers;

  @BeforeEach
  void setUp() {
    batchOperationState = processingState.getBatchOperationState();
    eventAppliers = new EventAppliers();
    eventAppliers.registerEventAppliers(processingState);
  }

  @Test
  void shouldStoreBatchOperationWithDefaultOrdinalForVersion2() {
    // given a version 2 record, written before the storage ordinal existed
    final var record = creationRecord();

    // when
    eventAppliers.applyState(BATCH_OPERATION_KEY, BatchOperationIntent.CREATED, record, 2);

    // then
    final var batchOperation = batchOperationState.get(BATCH_OPERATION_KEY).orElseThrow();
    assertThat(batchOperation.getStorageOrdinal()).isZero();
  }

  @Test
  void shouldStoreBatchOperationWithOrdinalForVersion3() {
    // given
    final var record = creationRecord().setStorageOrdinal(STORAGE_ORDINAL);

    // when
    eventAppliers.applyState(BATCH_OPERATION_KEY, BatchOperationIntent.CREATED, record, 3);

    // then
    final var batchOperation = batchOperationState.get(BATCH_OPERATION_KEY).orElseThrow();
    assertThat(batchOperation.getStorageOrdinal()).isEqualTo(STORAGE_ORDINAL);
  }

  private static BatchOperationCreationRecord creationRecord() {
    return new BatchOperationCreationRecord()
        .setBatchOperationKey(BATCH_OPERATION_KEY)
        .setBatchOperationType(BatchOperationType.CANCEL_PROCESS_INSTANCE)
        .setEntityFilter(
            new UnsafeBuffer(
                MsgPackConverter.convertToMsgPack(new ProcessInstanceFilter.Builder().build())));
  }
}
