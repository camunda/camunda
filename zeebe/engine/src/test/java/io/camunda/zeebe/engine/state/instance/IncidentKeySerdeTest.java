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
 * break. {@link IncidentKeyV1} is a frozen copy of the property based implementation the generated
 * codec replaced.
 */
final class IncidentKeySerdeTest {

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new IncidentKeyV1();
    final var current = new IncidentKey();
    v1.set(4_294_967_296L);
    current.set(4_294_967_296L);

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
  void shouldFailToWriteAnUnsetKeyLikeV1() {
    // given
    final var v1 = new IncidentKeyV1();
    final var current = new IncidentKey();

    // when / then
    assertThatThrownBy(() -> v1.write(new ExpandableArrayBuffer(), 0))
        .isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> current.write(new ExpandableArrayBuffer(), 0))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void shouldReadBytesWrittenByV1() {
    // given
    final var v1 = new IncidentKeyV1();
    v1.set(42L);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new IncidentKey();
    current.set(-7L);

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.get()).isEqualTo(42L);
  }

  @Test
  void shouldFailToReadBytesWithoutTheKeyLikeV1() {
    // given an empty map
    final var bytes = new UnsafeBuffer(new byte[] {(byte) 0x80});

    // when / then
    assertThatThrownBy(() -> new IncidentKeyV1().wrap(bytes, 0, bytes.capacity()))
        .isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> new IncidentKey().wrap(bytes, 0, bytes.capacity()))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new IncidentKey();
    current.set(42L);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new IncidentKeyV1();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.get()).isEqualTo(42L);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new IncidentKey();
    source.set(1L);
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
    final var instance = new IncidentKey();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.set(2L);
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
