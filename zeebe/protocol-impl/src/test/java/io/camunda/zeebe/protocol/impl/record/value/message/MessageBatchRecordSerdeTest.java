/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.message;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link io.camunda.zeebe.protocol.impl.record.value.message.legacy.MessageBatchRecord} is a
 * frozen copy of the property based implementation the generated codec replaced.
 */
final class MessageBatchRecordSerdeTest {

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given the message keys have no setter, a record only has them when it is read
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.message.legacy.MessageBatchRecord();
    final var current = new MessageBatchRecord();

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
  void shouldReadBytesWrittenByV1AndWriteThemAgain() {
    // given
    final var bytes = new ExpandableArrayBuffer();
    final int length = messageKeysBytes(bytes);
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.message.legacy.MessageBatchRecord();
    v1.wrap(bytes, 0, length);
    final var v1Bytes = new ExpandableArrayBuffer();
    final int v1Length = v1.write(v1Bytes, 0);
    final var current = new MessageBatchRecord();

    // when
    current.wrap(bytes, 0, length);
    final var currentBytes = new ExpandableArrayBuffer();
    final int currentLength = current.write(currentBytes, 0);

    // then
    assertThat(current.getMessageKeys()).containsExactly(1L, 300L, 70000L);
    assertThat(v1.getMessageKeys()).containsExactly(1L, 300L, 70000L);
    assertThat(hex(currentBytes, currentLength)).isEqualTo(hex(v1Bytes, v1Length));
    assertThat(hex(currentBytes, currentLength)).isEqualTo(hex(bytes, length));
  }

  @Test
  void shouldRestoreDefaultsWhenReadingBytesWithoutMessageKeys() {
    // given
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.message.legacy.MessageBatchRecord();
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new MessageBatchRecord();
    final var keys = new ExpandableArrayBuffer();
    current.wrap(keys, 0, messageKeysBytes(keys));

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getMessageKeys()).isEmpty();
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = messageKeysBytes(stored);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // the map has one entry, plus one unknown: 0x81 becomes 0x82
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read and written again
    final var instance = new MessageBatchRecord();
    instance.wrap(stored, 0, storedLength + futureLength);
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  @Test
  void shouldProduceTheSameJsonAsV1() throws Exception {
    // given
    final var bytes = new ExpandableArrayBuffer();
    final int length = messageKeysBytes(bytes);
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.message.legacy.MessageBatchRecord();
    v1.wrap(bytes, 0, length);
    final var current = new MessageBatchRecord();
    current.wrap(bytes, 0, length);

    // when
    final var v1Json = v1.toJson();
    final var currentJson = current.toJson();

    // then exporters see the same document; compare the trees, Jackson does not guarantee the order
    // of the properties and it differs between the old and the new class
    final var mapper = new ObjectMapper();
    assertThat(mapper.readTree(currentJson)).isEqualTo(mapper.readTree(v1Json));
    assertThat(mapper.readTree(currentJson).get("messageKeys")).hasSize(3);
  }

  /** Writes the map {"messageKeys": [1, 300, 70000]}, as an old version did. */
  private static int messageKeysBytes(final ExpandableArrayBuffer buffer) {
    final var writer = new MsgPackWriter().wrap(buffer, 0);
    writer.writeMapHeader(1);
    writer.writeString(utf8("messageKeys"));
    writer.writeArrayHeader(3);
    writer.writeInteger(1L);
    writer.writeInteger(300L);
    writer.writeInteger(70000L);
    return writer.getOffset();
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
