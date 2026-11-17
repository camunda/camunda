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
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link io.camunda.zeebe.engine.state.authorization.legacy.PersistedAuthorization} is a
 * frozen copy of the property based implementation the generated codec replaced.
 */
final class PersistedAuthorizationSerdeTest {

  // the permission types are stored in this order
  private static final LinkedHashSet<PermissionType> PERMISSION_TYPES =
      new LinkedHashSet<>(List.of(PermissionType.READ, PermissionType.CREATE));

  private static void setAll(
      final io.camunda.zeebe.engine.state.authorization.legacy.PersistedAuthorization v1) {
    v1.setAuthorizationKey(70_000L)
        .setOwnerId("an owner id that is longer than forty bytes of utf8 text")
        .setOwnerType(AuthorizationOwnerType.GROUP)
        .setResourceMatcher(AuthorizationResourceMatcher.PROPERTY)
        .setResourceId("resource-id")
        .setResourcePropertyName("a property name that is longer than forty bytes of text")
        .setResourceType(AuthorizationResourceType.DOCUMENT)
        .setPermissionTypes(PERMISSION_TYPES);
  }

  private static void setAll(final PersistedAuthorization current) {
    current
        .setAuthorizationKey(70_000L)
        .setOwnerId("an owner id that is longer than forty bytes of utf8 text")
        .setOwnerType(AuthorizationOwnerType.GROUP)
        .setResourceMatcher(AuthorizationResourceMatcher.PROPERTY)
        .setResourceId("resource-id")
        .setResourcePropertyName("a property name that is longer than forty bytes of text")
        .setResourceType(AuthorizationResourceType.DOCUMENT)
        .setPermissionTypes(PERMISSION_TYPES);
  }

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new io.camunda.zeebe.engine.state.authorization.legacy.PersistedAuthorization();
    final var current = new PersistedAuthorization();
    setAll(v1);
    setAll(current);

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
    // given only what cannot be written unset, everything else stays default
    final var v1 = new io.camunda.zeebe.engine.state.authorization.legacy.PersistedAuthorization();
    final var current = new PersistedAuthorization();
    v1.setAuthorizationKey(1L)
        .setOwnerId("owner")
        .setOwnerType(AuthorizationOwnerType.USER)
        .setResourceMatcher(AuthorizationResourceMatcher.ANY)
        .setResourceId("resource")
        .setResourceType(AuthorizationResourceType.DOCUMENT);
    current
        .setAuthorizationKey(1L)
        .setOwnerId("owner")
        .setOwnerType(AuthorizationOwnerType.USER)
        .setResourceMatcher(AuthorizationResourceMatcher.ANY)
        .setResourceId("resource")
        .setResourceType(AuthorizationResourceType.DOCUMENT);

    // when
    final var v1Bytes = new ExpandableArrayBuffer();
    final int v1Length = v1.write(v1Bytes, 0);
    final var currentBytes = new ExpandableArrayBuffer();
    final int currentLength = current.write(currentBytes, 0);

    // then
    assertThat(hex(currentBytes, currentLength)).isEqualTo(hex(v1Bytes, v1Length));
  }

  @Test
  void shouldFailToWriteWhileUnsetLikeV1() {
    // given
    final var v1 = new io.camunda.zeebe.engine.state.authorization.legacy.PersistedAuthorization();
    final var current = new PersistedAuthorization();

    // when / then
    assertThatThrownBy(() -> v1.write(new ExpandableArrayBuffer(), 0))
        .isInstanceOf(MsgpackPropertyException.class);
    assertThatThrownBy(() -> current.write(new ExpandableArrayBuffer(), 0))
        .isInstanceOf(MsgpackPropertyException.class);
  }

  @Test
  void shouldReadBytesWrittenByV1() {
    // given
    final var v1 = new io.camunda.zeebe.engine.state.authorization.legacy.PersistedAuthorization();
    setAll(v1);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new PersistedAuthorization();
    current.setAuthorizationKey(7L).setOwnerId("dirty").setOwnerType(AuthorizationOwnerType.USER);
    current.setResourceMatcher(AuthorizationResourceMatcher.ID).setResourceId("dirty");
    current.setResourceType(AuthorizationResourceType.BATCH).addPermission(PermissionType.DELETE);
    current.setResourcePropertyName("dirty");

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getAuthorizationKey()).isEqualTo(70_000L);
    assertThat(current.getOwnerId())
        .isEqualTo("an owner id that is longer than forty bytes of utf8 text");
    assertThat(current.getOwnerType()).isEqualTo(AuthorizationOwnerType.GROUP);
    assertThat(current.getResourceMatcher()).isEqualTo(AuthorizationResourceMatcher.PROPERTY);
    assertThat(current.getResourceId()).isEqualTo("resource-id");
    assertThat(current.getResourcePropertyName())
        .isEqualTo("a property name that is longer than forty bytes of text");
    assertThat(current.getResourceType()).isEqualTo(AuthorizationResourceType.DOCUMENT);
    assertThat(current.getPermissionTypes()).isEqualTo(PERMISSION_TYPES);
  }

  @Test
  void shouldResetToDefaultsWhenReadingBytesWithDefaults() {
    // given
    final var bytes = new ExpandableArrayBuffer();
    final var source =
        new io.camunda.zeebe.engine.state.authorization.legacy.PersistedAuthorization();
    source
        .setAuthorizationKey(1L)
        .setOwnerId("owner")
        .setOwnerType(AuthorizationOwnerType.USER)
        .setResourceMatcher(AuthorizationResourceMatcher.ANY)
        .setResourceId("resource")
        .setResourceType(AuthorizationResourceType.DOCUMENT);
    final int length = source.write(bytes, 0);
    final var current = new PersistedAuthorization();
    setAll(current);

    // when
    current.wrap(bytes, 0, length);

    // then
    final var defaults =
        new io.camunda.zeebe.engine.state.authorization.legacy.PersistedAuthorization();
    defaults
        .setAuthorizationKey(1L)
        .setOwnerId("owner")
        .setOwnerType(AuthorizationOwnerType.USER)
        .setResourceMatcher(AuthorizationResourceMatcher.ANY)
        .setResourceId("resource")
        .setResourceType(AuthorizationResourceType.DOCUMENT);
    assertThat(current.getResourcePropertyName())
        .isEqualTo(defaults.getResourcePropertyName())
        .isEmpty();
    assertThat(current.getPermissionTypes()).isEmpty();
  }

  @Test
  void shouldFailToReadWithoutTheKeyLikeV1() {
    // given an empty map
    final var bytes = new ExpandableArrayBuffer(1);
    bytes.putByte(0, (byte) 0x80);

    // when
    final var v1Failure =
        catchThrowable(
            () ->
                new io.camunda.zeebe.engine.state.authorization.legacy.PersistedAuthorization()
                    .wrap(bytes, 0, 1));
    final var failure = catchThrowable(() -> new PersistedAuthorization().wrap(bytes, 0, 1));

    // then the same failure as V1
    assertThat(v1Failure).isInstanceOf(RuntimeException.class);
    assertThat(failure).isInstanceOf(RuntimeException.class);
    assertThat(failure.getCause()).isExactlyInstanceOf(v1Failure.getCause().getClass());
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new PersistedAuthorization();
    setAll(current);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new io.camunda.zeebe.engine.state.authorization.legacy.PersistedAuthorization();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getAuthorizationKey()).isEqualTo(70_000L);
    assertThat(v1.getResourceId()).isEqualTo("resource-id");
    assertThat(v1.getPermissionTypes()).isEqualTo(PERMISSION_TYPES);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new PersistedAuthorization();
    setAll(source);
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // the map has eight entries, plus one unknown: 0x88 becomes 0x89
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new PersistedAuthorization();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setResourceId("renamed");
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
