/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.batchoperation;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.value.LongValue;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

final class PersistedBatchOperationChunkTest {

  @Test
  void shouldReadChunkPersistedWithoutOrdinal() {
    // given a chunk written with the layout that predates the ordinal
    final var legacy = new LegacyChunk();
    legacy.keyProp.setValue(10L);
    legacy.batchOperationKeyProp.setValue(42L);
    legacy.itemKeysProp.add().setValue(1L);
    legacy.itemKeysProp.add().setValue(2L);
    final var buffer = new UnsafeBuffer(new byte[legacy.getLength()]);
    legacy.write(buffer, 0);

    // when
    final var chunk = new PersistedBatchOperationChunk();
    chunk.wrap(buffer, 0, legacy.getLength());

    // then
    assertThat(chunk.getKey()).isEqualTo(10L);
    assertThat(chunk.getBatchOperationKey()).isEqualTo(42L);
    assertThat(chunk.getItemKeys()).containsExactly(1L, 2L);
    assertThat(chunk.getBatchItemsStorageOrdinal()).isZero();
  }

  /** The persisted chunk as it was before the items' storage ordinal was added. */
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
