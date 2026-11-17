/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.msgpack.MsgpackPropertyException;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.HexFormat;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link
 * io.camunda.zeebe.protocol.impl.record.value.authorization.legacy.AuthorizationRecord} is a frozen
 * copy of the property based implementation the generated codec replaced.
 */
final class AuthorizationRecordSerdeTest {

  private static final String OWNER_ID = "an owner id that is longer than forty bytes of utf8";
  private static final String RESOURCE_ID = "a resource id that is longer than forty bytes of utf8";
  private static final String RESOURCE_PROPERTY_NAME = "resource property name";
  private static final EnumSet<PermissionType> PERMISSIONS =
      EnumSet.of(PermissionType.CREATE, PermissionType.READ, PermissionType.UPDATE);

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.authorization.legacy.AuthorizationRecord();
    final var current = new AuthorizationRecord();
    setAllProperties(v1);
    setAllProperties(current);

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
    // given only the property without a default is set, everything else stays default
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.authorization.legacy.AuthorizationRecord();
    final var current = new AuthorizationRecord();
    v1.setResourceMatcher(AuthorizationResourceMatcher.ID);
    current.setResourceMatcher(AuthorizationResourceMatcher.ID);

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
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.authorization.legacy.AuthorizationRecord();
    final var current = new AuthorizationRecord();

    // when / then
    assertThatThrownBy(() -> v1.write(new ExpandableArrayBuffer(), 0))
        .isInstanceOf(MsgpackPropertyException.class);
    assertThatThrownBy(() -> current.write(new ExpandableArrayBuffer(), 0))
        .isInstanceOf(MsgpackPropertyException.class);
  }

  @Test
  void shouldReadBytesWrittenByV1() {
    // given
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.authorization.legacy.AuthorizationRecord();
    setAllProperties(v1);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new AuthorizationRecord();
    current
        .setAuthorizationKey(7L)
        .setOwnerId("dirty")
        .setOwnerType(AuthorizationOwnerType.CLIENT)
        .setResourceMatcher(AuthorizationResourceMatcher.ANY)
        .setResourceId("dirty")
        .setResourcePropertyName("dirty")
        .setResourceType(AuthorizationResourceType.BATCH)
        .setPermissionTypes(EnumSet.of(PermissionType.DELETE));

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getAuthorizationKey()).isEqualTo(42L);
    assertThat(current.getOwnerId()).isEqualTo(OWNER_ID);
    assertThat(current.getOwnerType()).isEqualTo(AuthorizationOwnerType.USER);
    assertThat(current.getResourceMatcher()).isEqualTo(AuthorizationResourceMatcher.ID);
    assertThat(current.getResourceId()).isEqualTo(RESOURCE_ID);
    assertThat(current.getResourcePropertyName()).isEqualTo(RESOURCE_PROPERTY_NAME);
    assertThat(current.getResourceType()).isEqualTo(AuthorizationResourceType.ROLE);
    assertThat(current.getPermissionTypes()).isEqualTo(PERMISSIONS);
  }

  @Test
  void shouldRestoreDefaultsWhenReadingBytesWrittenByV1WithDefaults() {
    // given a value with defaults everywhere except the property without a default
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.authorization.legacy.AuthorizationRecord();
    v1.setResourceMatcher(AuthorizationResourceMatcher.ID);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new AuthorizationRecord();
    setAllProperties(current);

    // when
    current.wrap(bytes, 0, length);

    // then
    final var defaults =
        new io.camunda.zeebe.protocol.impl.record.value.authorization.legacy.AuthorizationRecord();
    defaults.setResourceMatcher(AuthorizationResourceMatcher.ID);
    assertThat(current.getAuthorizationKey()).isEqualTo(defaults.getAuthorizationKey());
    assertThat(current.getOwnerId()).isEmpty();
    assertThat(current.getOwnerType()).isEqualTo(defaults.getOwnerType());
    assertThat(current.getResourceId()).isEmpty();
    assertThat(current.getResourcePropertyName()).isEmpty();
    assertThat(current.getResourceType()).isEqualTo(defaults.getResourceType());
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
                new io.camunda.zeebe.protocol.impl.record.value.authorization.legacy
                        .AuthorizationRecord()
                    .wrap(bytes, 0, 1));
    final var failure = catchThrowable(() -> new AuthorizationRecord().wrap(bytes, 0, 1));

    // then the same failure as V1
    assertThat(failure.getCause()).isExactlyInstanceOf(v1Failure.getCause().getClass());
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new AuthorizationRecord();
    setAllProperties(current);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.authorization.legacy.AuthorizationRecord();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getAuthorizationKey()).isEqualTo(42L);
    assertThat(v1.getOwnerId()).isEqualTo(OWNER_ID);
    assertThat(v1.getOwnerType()).isEqualTo(AuthorizationOwnerType.USER);
    assertThat(v1.getResourceMatcher()).isEqualTo(AuthorizationResourceMatcher.ID);
    assertThat(v1.getResourceId()).isEqualTo(RESOURCE_ID);
    assertThat(v1.getResourcePropertyName()).isEqualTo(RESOURCE_PROPERTY_NAME);
    assertThat(v1.getResourceType()).isEqualTo(AuthorizationResourceType.ROLE);
    assertThat(v1.getPermissionTypes()).isEqualTo(PERMISSIONS);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new AuthorizationRecord();
    setAllProperties(source);
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
    final var instance = new AuthorizationRecord();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setOwnerId("modified");
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  @Test
  void shouldProduceTheSameJsonAsV1() throws Exception {
    // given
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.authorization.legacy.AuthorizationRecord();
    final var current = new AuthorizationRecord();
    setAllProperties(v1);
    setAllProperties(current);

    // when
    final var v1Json = v1.toJson();
    final var currentJson = current.toJson();

    // then exporters see the same document; compare the trees, Jackson does not guarantee the order
    // of the properties and it differs between the old and the new class
    final var mapper = new ObjectMapper();
    assertThat(mapper.readTree(currentJson)).isEqualTo(mapper.readTree(v1Json));
  }

  private static void setAllProperties(
      final io.camunda.zeebe.protocol.impl.record.value.authorization.legacy.AuthorizationRecord
          record) {
    record
        .setAuthorizationKey(42L)
        .setOwnerId(OWNER_ID)
        .setOwnerType(AuthorizationOwnerType.USER)
        .setResourceMatcher(AuthorizationResourceMatcher.ID)
        .setResourceId(RESOURCE_ID)
        .setResourcePropertyName(RESOURCE_PROPERTY_NAME)
        .setResourceType(AuthorizationResourceType.ROLE)
        .setPermissionTypes(PERMISSIONS);
  }

  private static void setAllProperties(final AuthorizationRecord record) {
    record
        .setAuthorizationKey(42L)
        .setOwnerId(OWNER_ID)
        .setOwnerType(AuthorizationOwnerType.USER)
        .setResourceMatcher(AuthorizationResourceMatcher.ID)
        .setResourceId(RESOURCE_ID)
        .setResourcePropertyName(RESOURCE_PROPERTY_NAME)
        .setResourceType(AuthorizationResourceType.ROLE)
        .setPermissionTypes(PERMISSIONS);
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
