/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link io.camunda.zeebe.engine.state.authorization.legacy.AuthorizationKeys} is a frozen
 * copy of the property based implementation the generated codec replaced.
 */
final class AuthorizationKeysSerdeTest {

  // the msgpack integer width changes at 128 and at 65536
  private static final Set<Long> KEYS = new LinkedHashSet<>(List.of(1L, 200L, 70_000L));

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new io.camunda.zeebe.engine.state.authorization.legacy.AuthorizationKeys();
    final var current = new AuthorizationKeys();
    v1.setAuthorizationKeys(KEYS);
    current.setAuthorizationKeys(KEYS);

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
    // given
    final var v1 = new io.camunda.zeebe.engine.state.authorization.legacy.AuthorizationKeys();
    final var current = new AuthorizationKeys();

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
    final var v1 = new io.camunda.zeebe.engine.state.authorization.legacy.AuthorizationKeys();
    v1.setAuthorizationKeys(KEYS);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new AuthorizationKeys();
    current.setAuthorizationKeys(Set.of(7L, 8L, 9L));

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getAuthorizationKeys()).containsExactlyInAnyOrderElementsOf(KEYS);
  }

  @Test
  void shouldResetToDefaultsWhenReadingBytesWithDefaults() {
    // given
    final var bytes = new ExpandableArrayBuffer();
    final int length =
        new io.camunda.zeebe.engine.state.authorization.legacy.AuthorizationKeys().write(bytes, 0);
    final var current = new AuthorizationKeys();
    current.setAuthorizationKeys(Set.of(7L, 8L, 9L));

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getAuthorizationKeys()).isEmpty();
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new AuthorizationKeys();
    current.setAuthorizationKeys(KEYS);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new io.camunda.zeebe.engine.state.authorization.legacy.AuthorizationKeys();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getAuthorizationKeys()).containsExactlyInAnyOrderElementsOf(KEYS);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new AuthorizationKeys();
    source.setAuthorizationKeys(KEYS);
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // one entry (0x81) becomes two entries (0x82): the map has fewer than 16 entries
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new AuthorizationKeys();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.addAuthorizationKey(5L);
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
