/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.camunda.zeebe.engine.state.batchoperation.PersistedBatchOperationChunk.ChunkItem;
import io.camunda.zeebe.engine.state.mutable.MutableBatchOperationState;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationChunkRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationItem;
import java.util.List;
import org.junit.jupiter.api.Test;

class BatchOperationChunkCreatedV3ApplierTest {

  private final MutableBatchOperationState state = mock(MutableBatchOperationState.class);
  private final BatchOperationChunkCreatedV3Applier applier =
      new BatchOperationChunkCreatedV3Applier(state);

  @Test
  void shouldStoreEveryItemWithItsOwnStorageOrdinal() {
    // given
    final var record =
        new BatchOperationChunkRecord()
            .setBatchOperationKey(42L)
            .setStorageOrdinal(1234)
            .setItems(List.of(item(1L, 7), item(2L, 8)));

    // when
    applier.applyState(100L, record);

    // then the items' ordinals are stored, not the batch operation's
    verify(state).addChunk(100L, 42L, List.of(new ChunkItem(1L, 7), new ChunkItem(2L, 8)));
  }

  @Test
  void shouldStoreEmptyChunk() {
    // given
    final var record = new BatchOperationChunkRecord().setBatchOperationKey(42L);

    // when
    applier.applyState(100L, record);

    // then
    verify(state).addChunk(100L, 42L, List.of());
  }

  private static BatchOperationItem item(final long key, final int storageOrdinal) {
    return new BatchOperationItem(key, key, -1L, storageOrdinal);
  }
}
