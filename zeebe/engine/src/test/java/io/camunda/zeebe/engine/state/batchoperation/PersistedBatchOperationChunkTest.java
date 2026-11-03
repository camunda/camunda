/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.batchoperation;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.batchoperation.PersistedBatchOperationChunk.ChunkItem;
import java.util.List;
import java.util.Set;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

class PersistedBatchOperationChunkTest {

  @Test
  void shouldKeepStorageOrdinalPerItemAcrossSerialization() {
    // given
    final var chunk =
        new PersistedBatchOperationChunk()
            .setKey(1L)
            .setBatchOperationKey(2L)
            .setItems(List.of(new ChunkItem(10L, 1001), new ChunkItem(11L, 1002)));

    // when
    final var read = roundTrip(chunk);

    // then
    assertThat(read.getItems()).containsExactly(new ChunkItem(10L, 1001), new ChunkItem(11L, 1002));
    assertThat(read.getItemKeys()).containsExactly(10L, 11L);
  }

  @Test
  void shouldReadOrdinalZeroForItemsStoredWithoutOrdinals() {
    // given a chunk written the way applier versions 1 and 2 do, bare keys only
    final var chunk =
        new PersistedBatchOperationChunk()
            .setKey(1L)
            .setBatchOperationKey(2L)
            .setItemKeys(List.of(10L, 11L))
            .appendItemKey(12L);

    // when
    final var read = roundTrip(chunk);

    // then
    assertThat(read.getItems())
        .containsExactly(new ChunkItem(10L, 0), new ChunkItem(11L, 0), new ChunkItem(12L, 0));
  }

  @Test
  void shouldKeepOrdinalsAlignedWithKeysAfterRemoval() {
    // given
    final var chunk =
        new PersistedBatchOperationChunk()
            .setKey(1L)
            .setBatchOperationKey(2L)
            .setItems(
                List.of(
                    new ChunkItem(10L, 1001), new ChunkItem(11L, 1002), new ChunkItem(12L, 1003)));

    // when
    chunk.removeItemKeys(Set.of(11L));

    // then
    assertThat(roundTrip(chunk).getItems())
        .containsExactly(new ChunkItem(10L, 1001), new ChunkItem(12L, 1003));
  }

  private static PersistedBatchOperationChunk roundTrip(final PersistedBatchOperationChunk chunk) {
    final var buffer = new UnsafeBuffer(new byte[chunk.getLength()]);
    chunk.write(buffer, 0);
    final var read = new PersistedBatchOperationChunk();
    read.wrap(buffer, 0, buffer.capacity());
    return read;
  }
}
