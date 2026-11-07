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
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.value.LongValue;
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

  @Test
  void shouldReadChunkPersistedBeforeOrdinalsExisted() {
    // given a chunk written with the layout that predates the ordinal array
    final var legacy = new LegacyChunk();
    legacy.keyProp.setValue(1L);
    legacy.batchOperationKeyProp.setValue(2L);
    legacy.itemKeysProp.add().setValue(10L);
    legacy.itemKeysProp.add().setValue(11L);
    final var buffer = new UnsafeBuffer(new byte[legacy.getLength()]);
    legacy.write(buffer, 0);

    // when
    final var read = new PersistedBatchOperationChunk();
    read.wrap(buffer, 0, buffer.capacity());

    // then
    assertThat(read.getKey()).isEqualTo(1L);
    assertThat(read.getBatchOperationKey()).isEqualTo(2L);
    assertThat(read.getItems()).containsExactly(new ChunkItem(10L, 0), new ChunkItem(11L, 0));
  }

  private static PersistedBatchOperationChunk roundTrip(final PersistedBatchOperationChunk chunk) {
    final var buffer = new UnsafeBuffer(new byte[chunk.getLength()]);
    chunk.write(buffer, 0);
    final var read = new PersistedBatchOperationChunk();
    read.wrap(buffer, 0, buffer.capacity());
    return read;
  }

  /** The persisted chunk as it was before the items' storage ordinals were added. */
  private static final class LegacyChunk extends UnpackedObject {
    private final LongProperty keyProp = new LongProperty("key");
    private final LongProperty batchOperationKeyProp = new LongProperty("batchOperationKey");
    private final ArrayProperty<LongValue> itemKeysProp =
        new ArrayProperty<>("itemKeys", LongValue::new);

    private LegacyChunk() {
      super(3);
      declareProperty(keyProp).declareProperty(batchOperationKeyProp).declareProperty(itemKeysProp);
    }
  }
}
