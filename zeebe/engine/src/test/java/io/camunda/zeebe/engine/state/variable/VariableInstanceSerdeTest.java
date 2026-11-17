/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.variable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.msgpack.MsgpackPropertyException;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link io.camunda.zeebe.engine.state.variable.legacy.VariableInstance} is a frozen copy of
 * the property based implementation the generated codec replaced.
 */
final class VariableInstanceSerdeTest {

  private static final byte[] VALUE =
      "a-variable-value-that-is-longer-than-thirty-two-bytes".getBytes(StandardCharsets.UTF_8);

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new io.camunda.zeebe.engine.state.variable.legacy.VariableInstance();
    final var current = new VariableInstance();
    final var value = new UnsafeBuffer(VALUE);
    v1.setKey(4242L).setValue(value, 0, value.capacity());
    current.setKey(4242L).setValue(value, 0, value.capacity());

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
  void shouldWriteTheSameBytesAsV1WithAnEmptyValue() {
    // given the key and the value, which cannot be written unset, at their default values
    final var v1 = new io.camunda.zeebe.engine.state.variable.legacy.VariableInstance();
    final var current = new VariableInstance();
    final var empty = new UnsafeBuffer(new byte[0]);
    v1.setKey(0L).setValue(empty, 0, 0);
    current.setKey(0L).setValue(empty, 0, 0);

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
    final var v1 = new io.camunda.zeebe.engine.state.variable.legacy.VariableInstance();
    final var current = new VariableInstance();

    // when / then
    assertThatThrownBy(() -> v1.write(new ExpandableArrayBuffer(), 0))
        .isInstanceOf(MsgpackPropertyException.class);
    assertThatThrownBy(() -> current.write(new ExpandableArrayBuffer(), 0))
        .isInstanceOf(MsgpackPropertyException.class);
  }

  @Test
  void shouldReadBytesWrittenByV1() {
    // given
    final var v1 = new io.camunda.zeebe.engine.state.variable.legacy.VariableInstance();
    final var value = new UnsafeBuffer(VALUE);
    v1.setKey(42L).setValue(value, 0, value.capacity());
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new VariableInstance();
    final var dirty = new UnsafeBuffer("dirty".getBytes(StandardCharsets.UTF_8));
    current.setKey(-1L).setValue(dirty, 0, dirty.capacity());

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getKey()).isEqualTo(42L);
    assertThat(bytesOf(current.getValue())).isEqualTo(VALUE);
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new VariableInstance();
    final var value = new UnsafeBuffer(VALUE);
    current.setKey(42L).setValue(value, 0, value.capacity());
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new io.camunda.zeebe.engine.state.variable.legacy.VariableInstance();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getKey()).isEqualTo(42L);
    assertThat(bytesOf(v1.getValue())).isEqualTo(VALUE);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new VariableInstance();
    final var value = new UnsafeBuffer(VALUE);
    source.setKey(1L).setValue(value, 0, value.capacity());
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // The map has two entries, so its header is 0x82; announce one more entry (0x83).
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new VariableInstance();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setKey(2L);
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  private static byte[] bytesOf(final DirectBuffer buffer) {
    final var bytes = new byte[buffer.capacity()];
    buffer.getBytes(0, bytes);
    return bytes;
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
