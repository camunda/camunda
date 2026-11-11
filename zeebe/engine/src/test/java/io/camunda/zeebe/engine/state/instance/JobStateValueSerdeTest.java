/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.instance;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import java.util.HexFormat;
import org.agrona.ExpandableArrayBuffer;
import org.junit.jupiter.api.Test;

/**
 * The stored format must not change, otherwise migration and rollback break. {@link
 * JobStateValueV1} is a frozen copy of the hand-written implementation the generated codec
 * replaced.
 */
final class JobStateValueSerdeTest {

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new JobStateValueV1();
    final var current = new JobStateValue();
    v1.setState(JobState.State.ACTIVATED);
    current.setState(JobState.State.ACTIVATED);

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
  void shouldWriteTheSameBytesAsV1ForEveryState() {
    // given every state the enum can hold, one pair of objects per state
    for (final var state : JobState.State.values()) {
      final var v1 = new JobStateValueV1();
      final var current = new JobStateValue();
      v1.setState(state);
      current.setState(state);

      // when
      final var v1Bytes = new ExpandableArrayBuffer();
      final int v1Length = v1.write(v1Bytes, 0);
      final var currentBytes = new ExpandableArrayBuffer();
      final int currentLength = current.write(currentBytes, 0);

      // then
      assertThat(hex(currentBytes, currentLength))
          .as("bytes for state %s", state)
          .isEqualTo(hex(v1Bytes, v1Length));
    }
  }

  @Test
  void shouldReadBytesWrittenByV1() {
    // given
    final var v1 = new JobStateValueV1();
    v1.setState(JobState.State.FAILED);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new JobStateValue();
    current.setState(JobState.State.ACTIVATABLE); // dirty the target before reading

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getState()).isEqualTo(JobState.State.FAILED);
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new JobStateValue();
    current.setState(JobState.State.ERROR_THROWN);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new JobStateValueV1();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getState()).isEqualTo(JobState.State.ERROR_THROWN);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a stored value that carries a property this version does not know
    final var source = new JobStateValue();
    source.setState(JobState.State.ACTIVATED);
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
    final var instance = new JobStateValue();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setState(JobState.State.FAILED);
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(instance.getState()).isEqualTo(JobState.State.FAILED);
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
