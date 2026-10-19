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

import io.camunda.zeebe.engine.state.mutable.MutableBatchOperationState;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationChunkRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationItem;
import io.camunda.zeebe.protocol.record.value.BatchOperationChunkRecordValue.BatchOperationItemValue;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BatchOperationChunkCreatedV3ApplierTest {

  private final MutableBatchOperationState state = mock(MutableBatchOperationState.class);
  private final BatchOperationChunkCreatedV3Applier applier =
      new BatchOperationChunkCreatedV3Applier(state);

  @Test
  void shouldStoreChunkWithItemsOrdinal() {
    // given
    final var record =
        new BatchOperationChunkRecord()
            .setBatchOperationKey(42L)
            .setStorageOrdinal(1234)
            .setBatchItemsStorageOrdinal(7)
            .setItems(List.of(item(1L), item(2L)));

    // when
    applier.applyState(100L, record);

    // then the items' ordinal is stored, not the batch operation's
    verify(state).addChunk(100L, 42L, 7, Set.of(1L, 2L));
  }

  @Test
  void shouldStoreOrdinalZeroWhenItemsOrdinalNotKnown() {
    // given
    final var record = new BatchOperationChunkRecord().setBatchOperationKey(42L);

    // when
    applier.applyState(100L, record);

    // then
    verify(state).addChunk(100L, 42L, 0, Set.of());
  }

  private static BatchOperationItemValue item(final long key) {
    return new BatchOperationItem(key, key, -1L);
  }
}
