/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.user;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.RecordV1s;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link io.camunda.zeebe.engine.state.user.legacy.PersistedUser} is a frozen copy of the
 * property based implementation the generated codec replaced.
 */
final class PersistedUserSerdeTest {

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new io.camunda.zeebe.engine.state.user.legacy.PersistedUser();
    final var current = new PersistedUser();
    final var user = user("a-user-name-that-is-longer-than-thirty-two-bytes", 4242L);
    v1.setUser(
        RecordV1s.convert(
            user, new io.camunda.zeebe.protocol.impl.record.value.user.legacy.UserRecord()));
    current.setUser(user);

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
    // given the username, which cannot be written unset, and everything else at its default
    final var v1 = new io.camunda.zeebe.engine.state.user.legacy.PersistedUser();
    final var current = new PersistedUser();
    v1.setUser(
        RecordV1s.convert(
            new UserRecord().setUsername(""),
            new io.camunda.zeebe.protocol.impl.record.value.user.legacy.UserRecord()));
    current.setUser(new UserRecord().setUsername(""));

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
    final var v1 = new io.camunda.zeebe.engine.state.user.legacy.PersistedUser();
    v1.setUser(
        RecordV1s.convert(
            user("user-a", 42L),
            new io.camunda.zeebe.protocol.impl.record.value.user.legacy.UserRecord()));
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new PersistedUser();
    current.setUser(user("dirty", -1L));

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getUserKey()).isEqualTo(42L);
    assertThat(current.getUsername()).isEqualTo("user-a");
    assertThat(current.getName()).isEqualTo("name");
    assertThat(current.getEmail()).isEqualTo("user@example.com");
    assertThat(current.getPassword()).isEqualTo("secret");
  }

  @Test
  void shouldResetTheUserWhenReadFromV1WithDefaults() {
    // given
    final var v1 = new io.camunda.zeebe.engine.state.user.legacy.PersistedUser();
    v1.setUser(
        RecordV1s.convert(
            new UserRecord().setUsername(""),
            new io.camunda.zeebe.protocol.impl.record.value.user.legacy.UserRecord()));
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new PersistedUser();
    current.setUser(user("dirty", 7L));

    // when
    current.wrap(bytes, 0, length);

    // then the defaults are back: the same bytes as a fresh V1
    final var fresh = new io.camunda.zeebe.engine.state.user.legacy.PersistedUser();
    fresh.setUser(
        RecordV1s.convert(
            new UserRecord().setUsername(""),
            new io.camunda.zeebe.protocol.impl.record.value.user.legacy.UserRecord()));
    final var defaults = new ExpandableArrayBuffer();
    final int defaultsLength = fresh.write(defaults, 0);
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = current.write(rewritten, 0);
    assertThat(hex(rewritten, rewrittenLength)).isEqualTo(hex(defaults, defaultsLength));
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new PersistedUser();
    current.setUser(user("user-b", 42L));
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new io.camunda.zeebe.engine.state.user.legacy.PersistedUser();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getUserKey()).isEqualTo(42L);
    assertThat(v1.getUsername()).isEqualTo("user-b");
    assertThat(v1.getName()).isEqualTo("name");
    assertThat(v1.getEmail()).isEqualTo("user@example.com");
    assertThat(v1.getPassword()).isEqualTo("secret");
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new PersistedUser();
    source.setUser(user("user-c", 1L));
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
    final var instance = new PersistedUser();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setUser(user("user-d", 2L));
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  private static UserRecord user(final String username, final long userKey) {
    return new UserRecord()
        .setUserKey(userKey)
        .setUsername(username)
        .setName("name")
        .setEmail("user@example.com")
        .setPassword("secret");
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
