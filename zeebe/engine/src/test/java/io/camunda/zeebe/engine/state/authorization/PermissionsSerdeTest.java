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
import io.camunda.zeebe.protocol.record.value.AuthorizationScope;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link io.camunda.zeebe.engine.state.authorization.legacy.Permissions} is a frozen copy of
 * the property based implementation the generated codec replaced.
 */
final class PermissionsSerdeTest {

  private static final Map<PermissionType, Set<AuthorizationScope>> PERMISSIONS =
      Map.of(
          PermissionType.READ,
          Set.of(AuthorizationScope.of("a resource id that is longer than forty bytes of text")),
          PermissionType.CREATE,
          Set.of(AuthorizationScope.WILDCARD));

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new io.camunda.zeebe.engine.state.authorization.legacy.Permissions();
    final var current = new Permissions();
    v1.setPermissions(PERMISSIONS);
    current.setPermissions(PERMISSIONS);

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
    // given the default is an empty document, nothing is set
    final var v1 = new io.camunda.zeebe.engine.state.authorization.legacy.Permissions();
    final var current = new Permissions();

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
    final var v1 = new io.camunda.zeebe.engine.state.authorization.legacy.Permissions();
    v1.setPermissions(PERMISSIONS);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new Permissions();
    current.setPermissions(Map.of(PermissionType.UPDATE, Set.of(AuthorizationScope.WILDCARD)));

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getPermissions()).isEqualTo(PERMISSIONS);
  }

  @Test
  void shouldReadAMissingKeyAsTheDefaultLikeV1() {
    // given an empty map
    final var bytes = new ExpandableArrayBuffer(1);
    bytes.putByte(0, (byte) 0x80);
    final var current = new Permissions();
    current.setPermissions(PERMISSIONS);

    // when
    new io.camunda.zeebe.engine.state.authorization.legacy.Permissions().wrap(bytes, 0, 1);
    current.wrap(bytes, 0, 1);

    // then
    assertThat(current.getPermissions()).isEmpty();
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new Permissions();
    current.setPermissions(PERMISSIONS);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new io.camunda.zeebe.engine.state.authorization.legacy.Permissions();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getPermissions()).isEqualTo(PERMISSIONS);
  }

  @Test
  void shouldReportEmptyLikeV1() {
    // given
    final var v1 = new io.camunda.zeebe.engine.state.authorization.legacy.Permissions();
    final var current = new Permissions();
    v1.setPermissions(Map.of());
    current.setPermissions(Map.of());

    // then
    assertThat(current.isEmpty()).isTrue().isEqualTo(v1.isEmpty());

    // when
    v1.setPermissions(PERMISSIONS);
    current.setPermissions(PERMISSIONS);
    assertThat(current.isEmpty()).isFalse().isEqualTo(v1.isEmpty());
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new Permissions();
    source.setPermissions(PERMISSIONS);
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
    final var instance = new Permissions();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.addAuthorizationScope(PermissionType.DELETE, AuthorizationScope.WILDCARD);
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
