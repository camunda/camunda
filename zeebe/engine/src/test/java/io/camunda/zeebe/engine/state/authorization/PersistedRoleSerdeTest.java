/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.camunda.zeebe.msgpack.MsgpackPropertyException;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link io.camunda.zeebe.engine.state.authorization.legacy.PersistedRole} is a frozen copy
 * of the property based implementation the generated codec replaced.
 */
final class PersistedRoleSerdeTest {

  private static final RoleRecord ROLE =
      new RoleRecord()
          .setRoleKey(42L)
          .setRoleId("a role id that is longer than forty bytes of utf8 text")
          .setName("name")
          .setDescription("a description with some more text in it");

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new io.camunda.zeebe.engine.state.authorization.legacy.PersistedRole();
    final var current = new PersistedRole();
    v1.from(ROLE);
    current.from(ROLE);

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
  void shouldFailToWriteWhileUnsetLikeV1() {
    // given
    final var v1 = new io.camunda.zeebe.engine.state.authorization.legacy.PersistedRole();
    final var current = new PersistedRole();

    // when / then
    assertThatThrownBy(() -> v1.write(new ExpandableArrayBuffer(), 0))
        .isInstanceOf(MsgpackPropertyException.class);
    assertThatThrownBy(() -> current.write(new ExpandableArrayBuffer(), 0))
        .isInstanceOf(MsgpackPropertyException.class);
  }

  @Test
  void shouldReadBytesWrittenByV1() {
    // given
    final var v1 = new io.camunda.zeebe.engine.state.authorization.legacy.PersistedRole();
    v1.from(ROLE);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new PersistedRole();
    current.from(new RoleRecord().setRoleKey(7L).setRoleId("dirty").setName("dirty"));
    current.setDescription("dirty");

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getRoleKey()).isEqualTo(42L);
    assertThat(current.getRoleId()).isEqualTo(ROLE.getRoleId());
    assertThat(current.getName()).isEqualTo(ROLE.getName());
    assertThat(current.getDescription()).isEqualTo(ROLE.getDescription());
  }

  @Test
  void shouldFailToReadWithoutTheKeyLikeV1() {
    // given an empty map
    final var bytes = new ExpandableArrayBuffer(1);
    bytes.putByte(0, (byte) 0x80);

    // when / then
    final var v1Failure =
        catchThrowable(
            () ->
                new io.camunda.zeebe.engine.state.authorization.legacy.PersistedRole()
                    .wrap(bytes, 0, 1));
    final var failure = catchThrowable(() -> new PersistedRole().wrap(bytes, 0, 1));

    // then the same failure as V1
    assertThat(v1Failure).isInstanceOf(RuntimeException.class);
    assertThat(failure).isInstanceOf(RuntimeException.class);
    assertThat(failure.getCause()).isExactlyInstanceOf(v1Failure.getCause().getClass());
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new PersistedRole();
    current.from(ROLE);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new io.camunda.zeebe.engine.state.authorization.legacy.PersistedRole();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getRoleKey()).isEqualTo(42L);
    assertThat(v1.getRoleId()).isEqualTo(ROLE.getRoleId());
    assertThat(v1.getName()).isEqualTo(ROLE.getName());
    assertThat(v1.getDescription()).isEqualTo(ROLE.getDescription());
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new PersistedRole();
    source.from(ROLE);
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // the map has four entries, plus one unknown: 0x84 becomes 0x85
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new PersistedRole();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setName("renamed");
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
