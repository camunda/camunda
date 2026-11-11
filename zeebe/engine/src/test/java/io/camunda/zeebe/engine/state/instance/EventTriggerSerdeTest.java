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
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The stored format must not change, otherwise migration and rollback break. {@link EventTriggerV1}
 * is a frozen copy of the hand-written implementation the generated codec replaced. The variables
 * are a msgpack binary; the values below include bytes that are not valid UTF-8 on purpose, so that
 * a string encoding would be caught.
 */
final class EventTriggerSerdeTest {

  private static final byte[] VARIABLES = {
    (byte) 0x81, (byte) 0xa1, (byte) 0xff, 0x00, (byte) 0xc1, 0x7f
  };

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new EventTriggerV1();
    final var current = new EventTrigger();
    v1.setElementId(BufferUtil.wrapString("boundary"));
    current.setElementId(BufferUtil.wrapString("boundary"));
    v1.setVariables(new UnsafeBuffer(VARIABLES));
    current.setVariables(new UnsafeBuffer(VARIABLES));
    v1.setEventKey(5L);
    current.setEventKey(5L);
    v1.setProcessInstanceKey(6L);
    current.setProcessInstanceKey(6L);

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
    // given only the values V1 requires; the process instance key keeps its default of -1, and
    // V1 cannot write the other properties unset, so they are set to empty or zero explicitly
    final var v1 = new EventTriggerV1();
    final var current = new EventTrigger();
    v1.setElementId(BufferUtil.wrapString(""));
    current.setElementId(BufferUtil.wrapString(""));
    v1.setVariables(new UnsafeBuffer(new byte[0]));
    current.setVariables(new UnsafeBuffer(new byte[0]));
    v1.setEventKey(0L);
    current.setEventKey(0L);

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
    final var v1 = new EventTriggerV1();
    v1.setElementId(BufferUtil.wrapString("boundary"));
    v1.setVariables(new UnsafeBuffer(VARIABLES));
    v1.setEventKey(5L);
    v1.setProcessInstanceKey(6L);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new EventTrigger();
    current.setElementId(BufferUtil.wrapString("stale"));
    current.setVariables(new UnsafeBuffer(new byte[] {0x01}));
    current.setEventKey(99L);
    current.setProcessInstanceKey(99L);

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(BufferUtil.bufferAsString(current.getElementId())).isEqualTo("boundary");
    assertThat(current.getVariables()).isEqualTo(new UnsafeBuffer(VARIABLES));
    assertThat(current.getEventKey()).isEqualTo(5L);
    assertThat(current.getProcessInstanceKey()).isEqualTo(6L);
  }

  @Test
  void shouldReadTheDefaultProcessInstanceKeyWrittenByV1IntoADirtyTarget() {
    // given a V1 value that keeps the default process instance key of -1, which V1 writes out
    final var v1 = new EventTriggerV1();
    v1.setElementId(BufferUtil.wrapString("boundary"));
    v1.setVariables(new UnsafeBuffer(VARIABLES));
    v1.setEventKey(5L);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new EventTrigger();
    current.setProcessInstanceKey(99L);

    // when
    current.wrap(bytes, 0, length);

    // then the reset restores the default
    assertThat(current.getProcessInstanceKey()).isEqualTo(-1L);
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new EventTrigger();
    current.setElementId(BufferUtil.wrapString("boundary"));
    current.setVariables(new UnsafeBuffer(VARIABLES));
    current.setEventKey(5L);
    current.setProcessInstanceKey(6L);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new EventTriggerV1();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getElementId()).isEqualTo(BufferUtil.wrapString("boundary"));
    assertThat(v1.getVariables()).isEqualTo(new UnsafeBuffer(VARIABLES));
    assertThat(v1.getEventKey()).isEqualTo(5L);
    assertThat(v1.getProcessInstanceKey()).isEqualTo(6L);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a stored value that carries a property this version does not know
    final var source = new EventTrigger();
    source.setElementId(BufferUtil.wrapString("boundary"));
    source.setVariables(new UnsafeBuffer(VARIABLES));
    source.setEventKey(5L);
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(wrapString("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // four declared properties: a fixmap header (0x80 | count), announce one more entry
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new EventTrigger();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setEventKey(8L);
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    final var reread = new EventTrigger();
    reread.wrap(rewritten, 0, rewrittenLength);
    assertThat(reread.getEventKey()).isEqualTo(8L);
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  @Test
  void shouldNotReferenceTheBufferItWasReadFrom() {
    // given
    final var instance = new EventTrigger();
    instance.setElementId(BufferUtil.wrapString("boundary"));
    instance.setVariables(new UnsafeBuffer(VARIABLES));
    final var buffer = new ExpandableArrayBuffer();
    final int length = instance.write(buffer, 0);
    final var read = new EventTrigger();
    read.wrap(buffer, 0, length);

    // when the buffer is reused
    buffer.setMemory(0, buffer.capacity(), (byte) 0);

    // then
    assertThat(BufferUtil.bufferAsString(read.getElementId())).isEqualTo("boundary");
    assertThat(read.getVariables()).isEqualTo(new UnsafeBuffer(VARIABLES));
  }

  private static DirectBuffer wrapString(final String s) {
    return new UnsafeBuffer(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  private static String hex(final ExpandableArrayBuffer buffer, final int length) {
    final var bytes = new byte[length];
    buffer.getBytes(0, bytes);
    return HexFormat.of().formatHex(bytes);
  }
}
