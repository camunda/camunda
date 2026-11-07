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
import io.camunda.zeebe.engine.state.batchoperation.PersistedBatchOperationChunk.ChunkItem;
import io.camunda.zeebe.engine.state.mutable.MutableBatchOperationState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationChunkRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationItem;
import io.camunda.zeebe.protocol.record.intent.BatchOperationChunkIntent;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import java.util.List;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Exercises the real {@link EventAppliers#applyState} version dispatch for a chunk CREATED record
 * against real state: a record written with version 2 must still store the chunk without item
 * ordinals, a record written with version 3 stores the ordinal of every item and not the one of the
 * batch operation.
 */
@ExtendWith(ProcessingStateExtension.class)
final class BatchOperationChunkCreatedReplayVersionDispatchTest {

  private static final long BATCH_OPERATION_KEY = 42L;
  private static final long CHUNK_KEY = 100L;
  private static final int BATCH_OPERATION_ORDINAL = 1234;

  private MutableProcessingState processingState;
  private MutableBatchOperationState batchOperationState;
  private EventAppliers eventAppliers;

  @BeforeEach
  void setUp() {
    batchOperationState = processingState.getBatchOperationState();
    eventAppliers = new EventAppliers();
    eventAppliers.registerEventAppliers(processingState);
    batchOperationState.create(
        BATCH_OPERATION_KEY,
        new BatchOperationCreationRecord()
            .setBatchOperationKey(BATCH_OPERATION_KEY)
            .setBatchOperationType(BatchOperationType.CANCEL_PROCESS_INSTANCE)
            .setStorageOrdinal(BATCH_OPERATION_ORDINAL)
            .setEntityFilter(
                new UnsafeBuffer(
                    MsgPackConverter.convertToMsgPack(
                        new ProcessInstanceFilter.Builder().build()))));
  }

  @Test
  void shouldStoreChunkWithoutItemOrdinalsForVersion2() {
    // when
    eventAppliers.applyState(CHUNK_KEY, BatchOperationChunkIntent.CREATED, chunkRecord(), 2);

    // then
    assertThat(batchOperationState.getNextItems(BATCH_OPERATION_KEY, 10))
        .containsExactlyInAnyOrder(new ChunkItem(1L, 0), new ChunkItem(2L, 0));
  }

  @Test
  void shouldStoreEveryItemsOwnOrdinalForVersion3() {
    // when
    eventAppliers.applyState(CHUNK_KEY, BatchOperationChunkIntent.CREATED, chunkRecord(), 3);

    // then the items' ordinals are stored, not the batch operation's
    assertThat(batchOperationState.getNextItems(BATCH_OPERATION_KEY, 10))
        .containsExactly(new ChunkItem(1L, 7), new ChunkItem(2L, 8));
  }

  private static BatchOperationChunkRecord chunkRecord() {
    return new BatchOperationChunkRecord()
        .setBatchOperationKey(BATCH_OPERATION_KEY)
        .setStorageOrdinal(BATCH_OPERATION_ORDINAL)
        .setItems(List.of(item(1L, 7), item(2L, 8)));
  }

  private static BatchOperationItem item(final long key, final int storageOrdinal) {
    return new BatchOperationItem(key, key, -1L, storageOrdinal);
  }
}
