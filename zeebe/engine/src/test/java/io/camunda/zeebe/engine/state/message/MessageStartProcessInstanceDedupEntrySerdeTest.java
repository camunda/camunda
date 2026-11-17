/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.message;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link io.camunda.zeebe.engine.state.message.legacy.MessageStartProcessInstanceDedupEntry}
 * is a frozen copy of the property based implementation the generated codec replaced.
 */
final class MessageStartProcessInstanceDedupEntrySerdeTest {

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 =
        new io.camunda.zeebe.engine.state.message.legacy.MessageStartProcessInstanceDedupEntry();
    final var current = new MessageStartProcessInstanceDedupEntry();
    v1.setProcessInstanceKey(1L << 40).setDeletionDeadline(1_700_000_000_000L).setStorageOrdinal(3);
    current
        .setProcessInstanceKey(1L << 40)
        .setDeletionDeadline(1_700_000_000_000L)
        .setStorageOrdinal(3);

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
    // given only defaults
    final var v1 =
        new io.camunda.zeebe.engine.state.message.legacy.MessageStartProcessInstanceDedupEntry();
    final var current = new MessageStartProcessInstanceDedupEntry();

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
    final var v1 =
        new io.camunda.zeebe.engine.state.message.legacy.MessageStartProcessInstanceDedupEntry();
    v1.setProcessInstanceKey(42L).setDeletionDeadline(7L).setStorageOrdinal(2);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new MessageStartProcessInstanceDedupEntry();
    current.setProcessInstanceKey(-5L).setDeletionDeadline(-6L).setStorageOrdinal(9);

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getProcessInstanceKey()).isEqualTo(42L);
    assertThat(current.getDeletionDeadline()).isEqualTo(7L);
    assertThat(current.getStorageOrdinal()).isEqualTo(2);
  }

  @Test
  void shouldReadDefaultsAfterReusingTheInstance() {
    // given bytes that only carry the key, the other properties have their defaults
    final var v1 =
        new io.camunda.zeebe.engine.state.message.legacy.MessageStartProcessInstanceDedupEntry();
    v1.setProcessInstanceKey(42L);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new MessageStartProcessInstanceDedupEntry();
    current.setDeletionDeadline(6L).setStorageOrdinal(9);

    // when
    current.wrap(bytes, 0, length);

    // then the defaults are restored by reset()
    assertThat(current.getProcessInstanceKey()).isEqualTo(42L);
    assertThat(current.getDeletionDeadline()).isEqualTo(-1L);
    assertThat(current.getStorageOrdinal()).isZero();
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new MessageStartProcessInstanceDedupEntry();
    current.setProcessInstanceKey(42L).setDeletionDeadline(7L).setStorageOrdinal(2);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 =
        new io.camunda.zeebe.engine.state.message.legacy.MessageStartProcessInstanceDedupEntry();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getProcessInstanceKey()).isEqualTo(42L);
    assertThat(v1.getDeletionDeadline()).isEqualTo(7L);
    assertThat(v1.getStorageOrdinal()).isEqualTo(2);
  }

  @Test
  void shouldReadAnEmptyMapAsDefaultsLikeV1() {
    // given an empty map, every property keeps its default
    final var bytes = new ExpandableArrayBuffer();
    bytes.putByte(0, (byte) 0x80);

    // then
    final var v1 =
        new io.camunda.zeebe.engine.state.message.legacy.MessageStartProcessInstanceDedupEntry();
    final var current = new MessageStartProcessInstanceDedupEntry();
    v1.wrap(bytes, 0, 1);
    current.wrap(bytes, 0, 1);
    assertThat(current.getProcessInstanceKey()).isEqualTo(v1.getProcessInstanceKey());
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new MessageStartProcessInstanceDedupEntry();
    source.setProcessInstanceKey(1L);
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // The map has three entries, a header of 0x80 | count, so announce one more.
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new MessageStartProcessInstanceDedupEntry();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setDeletionDeadline(42L);
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
