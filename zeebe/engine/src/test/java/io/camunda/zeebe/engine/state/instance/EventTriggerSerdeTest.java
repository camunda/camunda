/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.instance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link EventTriggerV1} is a frozen copy of the property based implementation the generated
 * codec replaced.
 */
final class EventTriggerSerdeTest {

  // longer than 31 bytes, so the msgpack string header changes
  private static final String LONG_ID = "an-element-id-that-is-longer-than-thirty-one-bytes";

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = populatedV1();
    final var current = populated();

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
  void shouldFailToWriteAnUnsetPropertyLikeV1() {
    // given every property without a default is unset
    final var v1 = new EventTriggerV1();
    final var current = new EventTrigger();

    // when / then
    assertThatThrownBy(() -> v1.write(new ExpandableArrayBuffer(), 0))
        .isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> current.write(new ExpandableArrayBuffer(), 0))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void shouldWriteTheSameBytesAsV1WithDefaults() {
    // given only what cannot be written unset, the processInstanceKey stays default
    final var v1 = new EventTriggerV1();
    v1.setElementId(utf8("e")).setVariables(utf8("v")).setEventKey(1L);
    final var current = new EventTrigger();
    current.setElementId(utf8("e")).setVariables(utf8("v")).setEventKey(1L);

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
    final var bytes = new ExpandableArrayBuffer();
    final int length = populatedV1().write(bytes, 0);
    final var current =
        new EventTrigger()
            .setElementId(utf8("dirty"))
            .setVariables(utf8("dirty"))
            .setEventKey(99L)
            .setProcessInstanceKey(98L);

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getElementId()).isEqualTo(utf8(LONG_ID));
    assertThat(current.getVariables()).isEqualTo(binary());
    assertThat(current.getEventKey()).isEqualTo(42L);
    assertThat(current.getProcessInstanceKey()).isEqualTo(7L);
  }

  @Test
  void shouldReadTheDefaultWhenBytesHoldTheDefault() {
    // given a value without the processInstanceKey, written by V1 with the default
    final var v1 = new EventTriggerV1();
    v1.setElementId(utf8("e")).setVariables(utf8("v")).setEventKey(1L);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new EventTrigger().setProcessInstanceKey(55L);

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getProcessInstanceKey()).isEqualTo(-1L);
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = populated();
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new EventTriggerV1();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getElementId()).isEqualTo(utf8(LONG_ID));
    assertThat(v1.getVariables()).isEqualTo(binary());
    assertThat(v1.getEventKey()).isEqualTo(42L);
    assertThat(v1.getProcessInstanceKey()).isEqualTo(7L);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = populated();
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // Four entries: the map header is 0x80 | count, so announce one more entry.
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new EventTrigger();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setEventKey(43L);
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  private static EventTrigger populated() {
    return new EventTrigger()
        .setElementId(utf8(LONG_ID))
        .setVariables(binary())
        .setEventKey(42L)
        .setProcessInstanceKey(7L);
  }

  private static EventTriggerV1 populatedV1() {
    return new EventTriggerV1()
        .setElementId(utf8(LONG_ID))
        .setVariables(binary())
        .setEventKey(42L)
        .setProcessInstanceKey(7L);
  }

  private static DirectBuffer binary() {
    final byte[] bytes = new byte[40];
    for (int i = 0; i < bytes.length; i++) {
      bytes[i] = (byte) (i * 3);
    }
    return new UnsafeBuffer(bytes);
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
