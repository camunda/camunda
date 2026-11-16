/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.globallistener;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.impl.record.value.globallistener.GlobalListenerBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.globallistener.GlobalListenerRecord;
import io.camunda.zeebe.protocol.record.value.GlobalListenerSource;
import io.camunda.zeebe.protocol.record.value.GlobalListenerType;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link PersistedGlobalListenersConfigV1} is a frozen copy of the property based
 * implementation the generated codec replaced.
 */
final class PersistedGlobalListenersConfigSerdeTest {

  // longer than 31 bytes, so the msgpack string header changes
  private static final String LONG_ID = "a-listener-id-longer-than-thirty-one-bytes";

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new PersistedGlobalListenersConfigV1();
    v1.setGlobalListeners(populatedBatch());
    final var current = new PersistedGlobalListenersConfig();
    current.setGlobalListeners(populatedBatch());

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
    // given
    final var v1 = new PersistedGlobalListenersConfigV1();
    final var current = new PersistedGlobalListenersConfig();

    // when
    final var v1Bytes = new ExpandableArrayBuffer();
    final int v1Length = v1.write(v1Bytes, 0);
    final var currentBytes = new ExpandableArrayBuffer();
    final int currentLength = current.write(currentBytes, 0);

    // then
    assertThat(hex(currentBytes, currentLength)).isEqualTo(hex(v1Bytes, v1Length));
  }

  @Test
  void shouldReadBytesWrittenByV1() {
    // given
    final var v1 = new PersistedGlobalListenersConfigV1();
    v1.setGlobalListeners(populatedBatch());
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new PersistedGlobalListenersConfig();
    current.setGlobalListeners(new GlobalListenerBatchRecord().setGlobalListenerBatchKey(99L));

    // when
    current.wrap(bytes, 0, length);

    // then
    final var batch = current.getGlobalListeners();
    assertThat(batch.getGlobalListenerBatchKey()).isEqualTo(7L);
    assertThat(batch.getListeners()).hasSize(1);
    assertThat(batch.getListeners().getFirst().getId()).isEqualTo(LONG_ID);
    assertThat(batch.getCreatedListenerKeys()).containsExactly(300L);
    assertThat(batch.getUpdatedListenerKeys()).containsExactly(70_000L);
    assertThat(batch.getDeletedListenerKeys()).containsExactly(5L);
  }

  @Test
  void shouldReadTheDefaultsWhenBytesHoldTheDefaults() {
    // given
    final var bytes = new ExpandableArrayBuffer();
    final int length = new PersistedGlobalListenersConfigV1().write(bytes, 0);
    final var current = new PersistedGlobalListenersConfig();
    current.setGlobalListeners(populatedBatch());

    // when
    current.wrap(bytes, 0, length);

    // then
    final var batch = current.getGlobalListeners();
    assertThat(batch.getGlobalListenerBatchKey()).isEqualTo(-1L);
    assertThat(batch.getListeners()).isEmpty();
    assertThat(batch.getCreatedListenerKeys()).isEmpty();
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new PersistedGlobalListenersConfig();
    current.setGlobalListeners(populatedBatch());
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new PersistedGlobalListenersConfigV1();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getGlobalListeners().getGlobalListenerBatchKey()).isEqualTo(7L);
    assertThat(v1.getGlobalListeners().getListeners()).hasSize(1);
    assertThat(v1.getGlobalListeners().getDeletedListenerKeys()).containsExactly(5L);
  }

  @Test
  void shouldNotReferenceTheBufferItWasReadFrom() {
    // given
    final var instance = new PersistedGlobalListenersConfig();
    instance.setGlobalListeners(populatedBatch());
    final var buffer = new ExpandableArrayBuffer();
    final int length = instance.write(buffer, 0);
    final var read = new PersistedGlobalListenersConfig();
    read.wrap(buffer, 0, length);

    // when the buffer is reused
    buffer.setMemory(0, buffer.capacity(), (byte) 0);

    // then
    assertThat(read.getGlobalListeners().getListeners().getFirst().getId()).isEqualTo(LONG_ID);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new PersistedGlobalListenersConfig();
    source.setGlobalListeners(populatedBatch());
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // One entry: the map header is 0x80 | count, so announce one more entry.
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new PersistedGlobalListenersConfig();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setGlobalListeners(new GlobalListenerBatchRecord().setGlobalListenerBatchKey(8L));
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  private static GlobalListenerBatchRecord populatedBatch() {
    final var listener =
        new GlobalListenerRecord()
            .setGlobalListenerKey(12L)
            .setId(LONG_ID)
            .setType("custom-type")
            .setRetries(5)
            .setEventTypes(List.of("all", "custom"))
            .setAfterNonGlobal(true)
            .setPriority(70)
            .setSource(GlobalListenerSource.API)
            .setListenerType(GlobalListenerType.USER_TASK)
            .setConfigKey(34L);
    final var created = new GlobalListenerRecord().setGlobalListenerKey(300L);
    final var updated = new GlobalListenerRecord().setGlobalListenerKey(70_000L);
    final var deleted = new GlobalListenerRecord().setGlobalListenerKey(5L);
    return new GlobalListenerBatchRecord()
        .setGlobalListenerBatchKey(7L)
        .addListener(listener)
        .addCreatedListener(created)
        .addUpdatedListener(updated)
        .addDeletedListener(deleted);
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
