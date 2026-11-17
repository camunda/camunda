/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.instance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.camunda.zeebe.engine.state.immutable.UserTaskState.LifecycleState;
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
 * break. {@link io.camunda.zeebe.engine.state.instance.legacy.UserTaskLifecycleStateValue} is a
 * frozen copy of the property based implementation the generated codec replaced.
 */
final class UserTaskLifecycleStateValueSerdeTest {

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new io.camunda.zeebe.engine.state.instance.legacy.UserTaskLifecycleStateValue();
    final var current = new UserTaskLifecycleStateValue();
    v1.setLifecycleState(LifecycleState.COMPLETING);
    current.setLifecycleState(LifecycleState.COMPLETING);

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
  void shouldFailToWriteWhenUnsetLikeV1() {
    // given
    final var v1 = new io.camunda.zeebe.engine.state.instance.legacy.UserTaskLifecycleStateValue();
    final var current = new UserTaskLifecycleStateValue();

    // when
    final var v1Error = catchThrowable(() -> v1.write(new ExpandableArrayBuffer(), 0));
    final var currentError = catchThrowable(() -> current.write(new ExpandableArrayBuffer(), 0));

    // then
    assertThat(v1Error).isInstanceOf(MsgpackPropertyException.class);
    assertThat(currentError).isInstanceOf(MsgpackPropertyException.class);
    assertThat(currentError.getMessage()).isEqualTo(v1Error.getMessage());
  }

  @Test
  void shouldReadBytesWrittenByV1() {
    // given
    final var v1 = new io.camunda.zeebe.engine.state.instance.legacy.UserTaskLifecycleStateValue();
    v1.setLifecycleState(LifecycleState.CREATED);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new UserTaskLifecycleStateValue();
    current.setLifecycleState(LifecycleState.ASSIGNING);

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getLifecycleState()).isEqualTo(LifecycleState.CREATED);
  }

  @Test
  void shouldFailToReadUnsetLikeV1() {
    // given a map without the jobState entry
    final var bytes = new ExpandableArrayBuffer();
    bytes.putByte(0, (byte) 0x80);

    // when
    final var v1Error =
        catchThrowable(
            () ->
                new io.camunda.zeebe.engine.state.instance.legacy.UserTaskLifecycleStateValue()
                    .wrap(bytes, 0, 1));
    final var currentError =
        catchThrowable(() -> new UserTaskLifecycleStateValue().wrap(bytes, 0, 1));

    // then the legacy read fails the same way: the entry is missing, so the map is not consumed
    assertThat(v1Error).hasMessageContaining("Deserialization stuck at offset 1 of length 1");
    assertThat(currentError).hasMessageContaining("Deserialization stuck at offset 1 of length 1");
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new UserTaskLifecycleStateValue();
    current.setLifecycleState(LifecycleState.CANCELING);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new io.camunda.zeebe.engine.state.instance.legacy.UserTaskLifecycleStateValue();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getLifecycleState()).isEqualTo(LifecycleState.CANCELING);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new UserTaskLifecycleStateValue();
    source.setLifecycleState(LifecycleState.CREATING);
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
    final var instance = new UserTaskLifecycleStateValue();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setLifecycleState(LifecycleState.CREATED);
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
