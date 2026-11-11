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
import java.util.HexFormat;
import org.agrona.ExpandableArrayBuffer;
import org.junit.jupiter.api.Test;

/**
 * The stored format must not change, otherwise migration and rollback break. {@link
 * ParentScopeKeyV1} is a frozen copy of the hand-written implementation the generated codec
 * replaced.
 */
final class ParentScopeKeySerdeTest {

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new ParentScopeKeyV1();
    final var current = new ParentScopeKey();
    v1.set(42L);
    current.set(42L);

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
    final var v1 = new ParentScopeKeyV1();
    final var current = new ParentScopeKey();

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
    final var v1 = new ParentScopeKeyV1();
    v1.set(7L);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new ParentScopeKey();
    current.set(42L); // dirty the target so that a reset that misses the field fails the test

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.get()).isEqualTo(7L);
  }

  @Test
  void shouldReadTheDefaultBytesWrittenByV1() {
    // given the default value, written by V1
    final var v1 = new ParentScopeKeyV1();
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new ParentScopeKey();
    current.set(42L);

    // when
    current.wrap(bytes, 0, length);

    // then the default is restored
    assertThat(current.get()).isEqualTo(-1L);
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new ParentScopeKey();
    current.set(42L);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new ParentScopeKeyV1();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.get()).isEqualTo(42L);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a stored value that carries a property this version does not know
    final var source = new ParentScopeKey();
    source.set(42L);
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(wrapString("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // The map has one entry, so its header is 0x81 and announces one more entry with 0x82.
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new ParentScopeKey();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.set(43L);
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(instance.get()).isEqualTo(43L);
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  private static org.agrona.DirectBuffer wrapString(final String s) {
    return new org.agrona.concurrent.UnsafeBuffer(
        s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  private static String hex(final ExpandableArrayBuffer buffer, final int length) {
    final var bytes = new byte[length];
    buffer.getBytes(0, bytes);
    return HexFormat.of().formatHex(bytes);
  }
}
