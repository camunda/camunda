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
 * break. {@link EventScopeInstanceV1} is a frozen copy of the property based implementation the
 * generated codec replaced.
 */
final class EventScopeInstanceSerdeTest {

  // longer than 31 bytes, so the msgpack string header changes
  private static final String LONG_ID = "an-interrupting-element-id-longer-than-thirty-one-bytes";

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
  void shouldWriteTheSameBytesAsV1WithDefaults() {
    // given only accepting, which has no default, and everything else stays default
    final var v1 = new EventScopeInstanceV1().setAccepting(true);
    final var current = new EventScopeInstance().setAccepting(true);

    // when
    final var v1Bytes = new ExpandableArrayBuffer();
    final int v1Length = v1.write(v1Bytes, 0);
    final var currentBytes = new ExpandableArrayBuffer();
    final int currentLength = current.write(currentBytes, 0);

    // then
    assertThat(hex(currentBytes, currentLength)).isEqualTo(hex(v1Bytes, v1Length));
  }

  @Test
  void shouldFailToWriteAnUnsetAcceptingLikeV1() {
    // given
    final var v1 = new EventScopeInstanceV1();
    final var current = new EventScopeInstance();

    // when / then
    assertThatThrownBy(() -> v1.write(new ExpandableArrayBuffer(), 0))
        .isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> current.write(new ExpandableArrayBuffer(), 0))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void shouldReadBytesWrittenByV1() {
    // given
    final var bytes = new ExpandableArrayBuffer();
    final int length = populatedV1().write(bytes, 0);
    final var current =
        new EventScopeInstance()
            .setAccepting(false)
            .setInterrupted(false)
            .addInterruptingElementId(utf8("dirty"))
            .addBoundaryElementId(utf8("dirty"));

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.isAccepting()).isTrue();
    assertThat(current.isInterrupted()).isTrue();
    assertThat(current.isInterruptingElementId(utf8(LONG_ID))).isTrue();
    assertThat(current.isInterruptingElementId(utf8("dirty"))).isFalse();
    assertThat(current.isBoundaryElementId(utf8("boundary"))).isTrue();
    assertThat(current.isBoundaryElementId(utf8("dirty"))).isFalse();
  }

  @Test
  void shouldReadTheDefaultsWhenBytesHoldTheDefaults() {
    // given
    final var v1 = new EventScopeInstanceV1().setAccepting(false);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current =
        new EventScopeInstance()
            .setInterrupted(true)
            .addInterruptingElementId(utf8("dirty"))
            .setAccepting(true);

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.isAccepting()).isFalse();
    assertThat(current.isInterrupted()).isFalse();
    assertThat(current.isInterruptingElementId(utf8("dirty"))).isFalse();
    assertThat(current.isBoundaryElementId(utf8("boundary"))).isFalse();
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = populated();
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new EventScopeInstanceV1();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.isAccepting()).isTrue();
    assertThat(v1.isInterrupted()).isTrue();
    assertThat(v1.isInterruptingElementId(utf8(LONG_ID))).isTrue();
    assertThat(v1.isBoundaryElementId(utf8("boundary"))).isTrue();
  }

  @Test
  void shouldBeEqualWhenTheStoredContentIsEqual() {
    // given
    final var first = populated();
    final var second = new EventScopeInstance(first);

    // when / then
    assertThat(second).isEqualTo(first);
    assertThat(second.hashCode()).isEqualTo(first.hashCode());
    assertThat(second.isInterruptingElementId(utf8(LONG_ID))).isTrue();
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
    final var instance = new EventScopeInstance();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setInterrupted(false);
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  private static EventScopeInstance populated() {
    return new EventScopeInstance()
        .setAccepting(true)
        .setInterrupted(true)
        .addInterruptingElementId(utf8(LONG_ID))
        .addBoundaryElementId(utf8("boundary"));
  }

  private static EventScopeInstanceV1 populatedV1() {
    return new EventScopeInstanceV1()
        .setAccepting(true)
        .setInterrupted(true)
        .addInterruptingElementId(utf8(LONG_ID))
        .addBoundaryElementId(utf8("boundary"));
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
