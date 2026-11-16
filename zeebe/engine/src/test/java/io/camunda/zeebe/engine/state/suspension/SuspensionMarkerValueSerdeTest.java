/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.suspension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.engine.state.immutable.SuspensionState;
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
 * break. {@link SuspensionMarkerValueV1} is a frozen copy of the property based implementation the
 * generated codec replaced.
 */
final class SuspensionMarkerValueSerdeTest {

  @Test
  void shouldWriteTheSameBytesAsV1ForEveryState() {
    for (final var state : SuspensionState.State.values()) {
      // given
      final var v1 = new SuspensionMarkerValueV1();
      final var current = new SuspensionMarkerValue();
      v1.setState(state);
      current.setState(state);

      // when
      final var v1Bytes = new ExpandableArrayBuffer();
      final int v1Length = v1.write(v1Bytes, 0);
      final var currentBytes = new ExpandableArrayBuffer();
      final int currentLength = current.write(currentBytes, 0);

      // then
      assertThat(currentLength).isEqualTo(current.getLength()).isEqualTo(v1.getLength());
      assertThat(hex(currentBytes, currentLength)).isEqualTo(hex(v1Bytes, v1Length));
    }
  }

  @Test
  void shouldFailToWriteWhileUnsetLikeV1() {
    // given
    final var v1 = new SuspensionMarkerValueV1();
    final var current = new SuspensionMarkerValue();

    // when / then
    assertThatThrownBy(() -> v1.write(new ExpandableArrayBuffer(), 0))
        .isInstanceOf(MsgpackPropertyException.class);
    assertThatThrownBy(() -> current.write(new ExpandableArrayBuffer(), 0))
        .isInstanceOf(MsgpackPropertyException.class);
  }

  @Test
  void shouldReadBytesWrittenByV1() {
    // given
    final var v1 = new SuspensionMarkerValueV1();
    v1.setState(SuspensionState.State.RESUMING);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new SuspensionMarkerValue();
    current.setState(SuspensionState.State.SUSPENDING);

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getState()).isEqualTo(SuspensionState.State.RESUMING);
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new SuspensionMarkerValue();
    current.setState(SuspensionState.State.SUSPENDED);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new SuspensionMarkerValueV1();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getState()).isEqualTo(SuspensionState.State.SUSPENDED);
  }

  @Test
  void shouldFailToReadWhenTheStateIsMissingLikeV1() {
    // given a value without the state property
    final var empty = new UnsafeBuffer(new byte[] {(byte) 0x80});
    final var v1 = new SuspensionMarkerValueV1();
    final var current = new SuspensionMarkerValue();

    // when / then
    assertThatThrownBy(() -> v1.wrap(empty, 0, 1)).isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> current.wrap(empty, 0, 1)).isInstanceOf(RuntimeException.class);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new SuspensionMarkerValue();
    source.setState(SuspensionState.State.SUSPENDED);
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // The map has one entry, so its header is 0x81; announce one more entry (0x82).
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new SuspensionMarkerValue();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setState(SuspensionState.State.RESUMING);
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
