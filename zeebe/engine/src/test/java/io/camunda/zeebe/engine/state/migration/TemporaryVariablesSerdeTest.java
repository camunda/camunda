/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.migration;

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
 * break. {@link io.camunda.zeebe.engine.state.migration.legacy.TemporaryVariables} is a frozen copy
 * of the property based implementation the generated codec replaced.
 */
final class TemporaryVariablesSerdeTest {

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new io.camunda.zeebe.engine.state.migration.legacy.TemporaryVariables();
    final var current = new TemporaryVariables();
    // a binary longer than 255 bytes, so the msgpack bin header changes width
    final DirectBuffer value = new UnsafeBuffer(new byte[300]);
    v1.set(value);
    current.set(value);

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
  void shouldRejectWritingWhileUnsetLikeV1() {
    // given
    final var v1 = new io.camunda.zeebe.engine.state.migration.legacy.TemporaryVariables();
    final var current = new TemporaryVariables();

    // then
    assertThatThrownBy(() -> v1.write(new ExpandableArrayBuffer(), 0))
        .isInstanceOf(MsgpackPropertyException.class);
    assertThatThrownBy(() -> current.write(new ExpandableArrayBuffer(), 0))
        .isInstanceOf(MsgpackPropertyException.class);
  }

  @Test
  void shouldReadBytesWrittenByV1() {
    // given
    final var v1 = new io.camunda.zeebe.engine.state.migration.legacy.TemporaryVariables();
    final DirectBuffer value = new UnsafeBuffer(new byte[] {1, 2, 3});
    v1.set(value);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new TemporaryVariables();
    current.set(new UnsafeBuffer(new byte[] {9}));

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(hex(current.get())).isEqualTo("010203");
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new TemporaryVariables();
    current.set(new UnsafeBuffer(new byte[] {1, 2, 3}));
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new io.camunda.zeebe.engine.state.migration.legacy.TemporaryVariables();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(hex(v1.get())).isEqualTo("010203");
  }

  @Test
  void shouldRejectReadingWithoutTheKeyLikeV1() {
    // given an empty map
    final var bytes = new ExpandableArrayBuffer();
    bytes.putByte(0, (byte) 0x80);

    // then
    assertThatThrownBy(
            () ->
                new io.camunda.zeebe.engine.state.migration.legacy.TemporaryVariables()
                    .wrap(bytes, 0, 1))
        .isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> new TemporaryVariables().wrap(bytes, 0, 1))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new TemporaryVariables();
    source.set(new UnsafeBuffer(new byte[] {1}));
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // The map has one entry, a header of 0x80 | count, so announce one more.
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new TemporaryVariables();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.set(new UnsafeBuffer(new byte[] {2}));
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  private static DirectBuffer utf8(final String s) {
    return new UnsafeBuffer(s.getBytes(StandardCharsets.UTF_8));
  }

  private static String hex(final DirectBuffer buffer) {
    final var bytes = new byte[buffer.capacity()];
    buffer.getBytes(0, bytes);
    return HexFormat.of().formatHex(bytes);
  }

  private static String hex(final ExpandableArrayBuffer buffer, final int length) {
    final var bytes = new byte[length];
    buffer.getBytes(0, bytes);
    return HexFormat.of().formatHex(bytes);
  }
}
