/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.instance;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.HexFormat;
import org.agrona.ExpandableArrayBuffer;
import org.junit.jupiter.api.Test;

/**
 * The stored format must not change, otherwise migration and rollback break. {@link
 * EventScopeInstanceV1} is a frozen copy of the hand-written implementation the generated codec
 * replaced.
 */
final class EventScopeInstanceSerdeTest {

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new EventScopeInstanceV1();
    final var current = new EventScopeInstance();
    v1.setAccepting(true);
    current.setAccepting(true);
    v1.setInterrupted(true);
    current.setInterrupted(true);
    v1.addInterruptingElementId(BufferUtil.wrapString("interrupting-a"));
    current.addInterruptingElementId(BufferUtil.wrapString("interrupting-a"));
    v1.addInterruptingElementId(BufferUtil.wrapString("interrupting-b"));
    current.addInterruptingElementId(BufferUtil.wrapString("interrupting-b"));
    v1.addBoundaryElementId(BufferUtil.wrapString("boundary-a"));
    current.addBoundaryElementId(BufferUtil.wrapString("boundary-a"));
    v1.addBoundaryElementId(BufferUtil.wrapString("boundary-b"));
    current.addBoundaryElementId(BufferUtil.wrapString("boundary-b"));

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
    // given only what the V1 class requires to be written; the frozen class throws on an unset
    // accepting flag, every other property keeps its default
    final var v1 = new EventScopeInstanceV1();
    v1.setAccepting(false);
    final var current = new EventScopeInstance();
    current.setAccepting(false);

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
    final var v1 = new EventScopeInstanceV1();
    v1.setAccepting(true);
    v1.addInterruptingElementId(BufferUtil.wrapString("interrupting"));
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new EventScopeInstance();
    current.setInterrupted(true);
    current.addBoundaryElementId(BufferUtil.wrapString("stale-boundary"));
    current.addInterruptingElementId(BufferUtil.wrapString("stale-interrupting"));

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.isAccepting()).isTrue();
    assertThat(current.isInterrupted()).isFalse();
    assertThat(current.isInterruptingElementId(BufferUtil.wrapString("interrupting"))).isTrue();
    assertThat(current.isInterruptingElementId(BufferUtil.wrapString("stale-interrupting")))
        .isFalse();
    assertThat(current.isBoundaryElementId(BufferUtil.wrapString("stale-boundary"))).isFalse();
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new EventScopeInstance();
    current.setAccepting(true);
    current.setInterrupted(true);
    current.addInterruptingElementId(BufferUtil.wrapString("interrupting"));
    current.addBoundaryElementId(BufferUtil.wrapString("boundary"));
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new EventScopeInstanceV1();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.isAccepting()).isTrue();
    assertThat(v1.isInterrupted()).isTrue();
    assertThat(v1.isInterruptingElementId(BufferUtil.wrapString("interrupting"))).isTrue();
    assertThat(v1.isBoundaryElementId(BufferUtil.wrapString("boundary"))).isTrue();
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a stored value that carries a property this version does not know
    final var source = new EventScopeInstance();
    source.setAccepting(true);
    source.addBoundaryElementId(BufferUtil.wrapString("boundary"));
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(BufferUtil.wrapString("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // 4 declared properties use a fixmap header (0x80 | count): announce one more entry
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new EventScopeInstance();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setInterrupted(true);
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  @Test
  void shouldNotReferenceTheBufferItWasReadFrom() {
    // given
    final var instance = new EventScopeInstance();
    instance.addInterruptingElementId(BufferUtil.wrapString("interrupting"));
    instance.addBoundaryElementId(BufferUtil.wrapString("boundary"));
    final var buffer = new ExpandableArrayBuffer();
    final int length = instance.write(buffer, 0);
    final var read = new EventScopeInstance();
    read.wrap(buffer, 0, length);

    // when the buffer is reused
    buffer.setMemory(0, buffer.capacity(), (byte) 0);

    // then
    assertThat(read.isInterruptingElementId(BufferUtil.wrapString("interrupting"))).isTrue();
    assertThat(read.isBoundaryElementId(BufferUtil.wrapString("boundary"))).isTrue();
  }

  private static String hex(final ExpandableArrayBuffer buffer, final int length) {
    final var bytes = new byte[length];
    buffer.getBytes(0, bytes);
    return HexFormat.of().formatHex(bytes);
  }
}
