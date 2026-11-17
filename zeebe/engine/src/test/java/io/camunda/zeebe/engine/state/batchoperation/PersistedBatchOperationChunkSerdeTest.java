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
import static org.assertj.core.api.Assertions.catchThrowable;

import io.camunda.zeebe.engine.state.batchoperation.PersistedBatchOperationChunk.ChunkItem;
import io.camunda.zeebe.msgpack.MsgpackPropertyException;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link io.camunda.zeebe.engine.state.batchoperation.legacy.PersistedBatchOperationChunk}
 * is a frozen copy of the property based implementation the generated codec replaced.
 */
final class PersistedBatchOperationChunkSerdeTest {

  // the msgpack integer width changes at 128 and at 65536, for keys and for ordinals
  private static final List<ChunkItem> ITEMS =
      List.of(new ChunkItem(1L, 0), new ChunkItem(200L, 200), new ChunkItem(1L << 40, 70_000));

  private static List<Long> keys() {
    return ITEMS.stream().map(ChunkItem::itemKey).toList();
  }

  private static List<
          io.camunda.zeebe.engine.state.batchoperation.legacy.PersistedBatchOperationChunk
              .ChunkItem>
      v1Items() {
    return ITEMS.stream()
        .map(
            item ->
                new io.camunda.zeebe.engine.state.batchoperation.legacy.PersistedBatchOperationChunk
                    .ChunkItem(item.itemKey(), item.storageOrdinal()))
        .toList();
  }

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 =
        new io.camunda.zeebe.engine.state.batchoperation.legacy.PersistedBatchOperationChunk();
    final var current = new PersistedBatchOperationChunk();
    v1.setKey(42L).setBatchOperationKey(7L);
    v1.setItems(v1Items());
    current.setKey(42L).setBatchOperationKey(7L);
    current.setItems(ITEMS);

    // when
    final var v1Bytes = new ExpandableArrayBuffer();
    final int v1Length = v1.write(v1Bytes, 0);
    final var currentBytes = new ExpandableArrayBuffer();
    final int currentLength = current.write(currentBytes, 0);

    // then
    assertThat(currentLength).isEqualTo(current.getLength()).isEqualTo(v1.getLength());
    assertThat(hex(currentBytes, currentLength)).isEqualTo(hex(v1Bytes, v1Length));
  }

  @Test
  void shouldWriteTheSameBytesAsV1WithDefaults() {
    // given only the properties without a default are set, the lists stay empty
    final var v1 =
        new io.camunda.zeebe.engine.state.batchoperation.legacy.PersistedBatchOperationChunk();
    final var current = new PersistedBatchOperationChunk();
    v1.setKey(1L).setBatchOperationKey(2L);
    current.setKey(1L).setBatchOperationKey(2L);

    // when
    final var v1Bytes = new ExpandableArrayBuffer();
    final int v1Length = v1.write(v1Bytes, 0);
    final var currentBytes = new ExpandableArrayBuffer();
    final int currentLength = current.write(currentBytes, 0);

    // then
    assertThat(hex(currentBytes, currentLength)).isEqualTo(hex(v1Bytes, v1Length));
  }

  @Test
  void shouldFailToWriteWhileUnsetLikeV1() {
    // given
    final var v1 =
        new io.camunda.zeebe.engine.state.batchoperation.legacy.PersistedBatchOperationChunk();
    final var current = new PersistedBatchOperationChunk();

    // when / then
    assertThatThrownBy(() -> v1.write(new ExpandableArrayBuffer(), 0))
        .isInstanceOf(MsgpackPropertyException.class);
    assertThatThrownBy(() -> current.write(new ExpandableArrayBuffer(), 0))
        .isInstanceOf(MsgpackPropertyException.class);
  }

  @Test
  void shouldReadBytesWrittenByV1() {
    // given
    final var v1 =
        new io.camunda.zeebe.engine.state.batchoperation.legacy.PersistedBatchOperationChunk();
    v1.setKey(42L).setBatchOperationKey(7L);
    v1.setItems(v1Items());
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new PersistedBatchOperationChunk();
    current.setKey(9L).setBatchOperationKey(9L);
    current.setItems(List.of(new ChunkItem(5L, 3), new ChunkItem(6L, 4), new ChunkItem(7L, 5)));

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getKey()).isEqualTo(42L);
    assertThat(current.getBatchOperationKey()).isEqualTo(7L);
    assertThat(current.getItemKeys()).isEqualTo(keys());
    assertThat(current.getItems()).isEqualTo(ITEMS);
  }

  @Test
  void shouldFailToReadWithoutTheKeyLikeV1() {
    // given an empty map
    final var bytes = new ExpandableArrayBuffer(1);
    bytes.putByte(0, (byte) 0x80);

    // when
    final var v1Failure =
        catchThrowable(
            () ->
                new io.camunda.zeebe.engine.state.batchoperation.legacy
                        .PersistedBatchOperationChunk()
                    .wrap(bytes, 0, 1));
    final var failure = catchThrowable(() -> new PersistedBatchOperationChunk().wrap(bytes, 0, 1));

    // then the same failure as V1
    assertThat(v1Failure).isInstanceOf(RuntimeException.class);
    assertThat(failure).isInstanceOf(RuntimeException.class);
    assertThat(failure.getCause()).isExactlyInstanceOf(v1Failure.getCause().getClass());
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new PersistedBatchOperationChunk();
    current.setKey(42L).setBatchOperationKey(7L);
    current.setItems(ITEMS);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 =
        new io.camunda.zeebe.engine.state.batchoperation.legacy.PersistedBatchOperationChunk();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getKey()).isEqualTo(42L);
    assertThat(v1.getBatchOperationKey()).isEqualTo(7L);
    assertThat(v1.getItemKeys()).isEqualTo(keys());
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new PersistedBatchOperationChunk();
    source.setKey(42L).setBatchOperationKey(7L);
    source.setItems(ITEMS);
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // one entry (0x84 for four declared entries) becomes five entries (0x85)
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new PersistedBatchOperationChunk();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.appendItemKey(9L);
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  private static DirectBuffer utf8(final String s) {
    return new UnsafeBuffer(s.getBytes(StandardCharsets.UTF_8));
  }

  private static String hex(final ExpandableArrayBuffer buffer, final int length) {
    final var bytes = new byte[length];
    buffer.getBytes(0, bytes);
    return HexFormat.of().formatHex(bytes);
  }
}
