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
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link RoleRecordV1} is a frozen copy of the property based implementation the generated
 * codec replaced.
 */
final class RoleRecordSerdeTest {

  private static final String ROLE_ID = "a role id that is longer than forty bytes of utf8 text";
  private static final String NAME = "a name that is also longer than forty bytes of utf8 text";
  private static final String DESCRIPTION = "a description with some more text in it";
  private static final String ENTITY_ID = "entity";

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new RoleRecordV1();
    final var current = new RoleRecord();
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
    final var v1 = new RoleRecordV1();
    final var current = new RoleRecord();
    v1.setRoleId("role");
    current.setRoleId("role");

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
    final var v1 = new RoleRecordV1();
    final var current = new RoleRecord();

    // when / then
    assertThatThrownBy(() -> v1.write(new ExpandableArrayBuffer(), 0))
        .isInstanceOf(MsgpackPropertyException.class);
    assertThatThrownBy(() -> current.write(new ExpandableArrayBuffer(), 0))
        .isInstanceOf(MsgpackPropertyException.class);
  }

  @Test
  void shouldReadBytesWrittenByV1() {
    // given
    final var v1 = new RoleRecordV1();
    setAllProperties(v1);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new RoleRecord();
    current
        .setRoleKey(7L)
        .setRoleId("dirty")
        .setName("dirty")
        .setDescription("dirty")
        .setEntityId("dirty")
        .setEntityType(EntityType.ROLE);

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getRoleKey()).isEqualTo(42L);
    assertThat(current.getRoleId()).isEqualTo(ROLE_ID);
    assertThat(current.getName()).isEqualTo(NAME);
    assertThat(current.getDescription()).isEqualTo(DESCRIPTION);
    assertThat(current.getEntityId()).isEqualTo(ENTITY_ID);
    assertThat(current.getEntityType()).isEqualTo(EntityType.GROUP);
  }

  @Test
  void shouldRestoreDefaultsWhenReadingBytesWrittenByV1WithDefaults() {
    // given a value with defaults everywhere except the property without a default
    final var v1 = new RoleRecordV1();
    v1.setRoleId("role");
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new RoleRecord();
    setAllProperties(current);

    // when
    current.wrap(bytes, 0, length);

    // then
    final var defaults = new RoleRecordV1();
    defaults.setRoleId("role");
    assertThat(current.getRoleKey()).isEqualTo(defaults.getRoleKey());
    assertThat(current.getName()).isEqualTo(defaults.getName());
    assertThat(current.getDescription()).isEqualTo(defaults.getDescription());
    assertThat(current.getEntityId()).isEqualTo(defaults.getEntityId());
    assertThat(current.getEntityType()).isEqualTo(defaults.getEntityType());
  }

  @Test
  void shouldFailToReadWithoutTheKeyLikeV1() {
    // given an empty map
    final var bytes = new ExpandableArrayBuffer(1);
    bytes.putByte(0, (byte) 0x80);

    // when
    final var v1Failure = catchThrowable(() -> new RoleRecordV1().wrap(bytes, 0, 1));
    final var failure = catchThrowable(() -> new RoleRecord().wrap(bytes, 0, 1));

    // then the same failure as V1
    assertThat(failure.getCause()).isExactlyInstanceOf(v1Failure.getCause().getClass());
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new RoleRecord();
    setAllProperties(current);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new RoleRecordV1();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getRoleKey()).isEqualTo(42L);
    assertThat(v1.getRoleId()).isEqualTo(ROLE_ID);
    assertThat(v1.getName()).isEqualTo(NAME);
    assertThat(v1.getDescription()).isEqualTo(DESCRIPTION);
    assertThat(v1.getEntityId()).isEqualTo(ENTITY_ID);
    assertThat(v1.getEntityType()).isEqualTo(EntityType.GROUP);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new RoleRecord();
    setAllProperties(source);
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // the map has six entries, plus one unknown: 0x86 becomes 0x87
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new RoleRecord();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setName("modified");
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  @Test
  void shouldProduceTheSameJsonAsV1() throws Exception {
    // given
    final var v1 = new RoleRecordV1();
    final var current = new RoleRecord();
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

  private static void setAllProperties(final RoleRecordV1 record) {
    record
        .setRoleKey(42L)
        .setRoleId(ROLE_ID)
        .setName(NAME)
        .setDescription(DESCRIPTION)
        .setEntityId(ENTITY_ID)
        .setEntityType(EntityType.GROUP);
  }

  private static void setAllProperties(final RoleRecord record) {
    record
        .setRoleKey(42L)
        .setRoleId(ROLE_ID)
        .setName(NAME)
        .setDescription(DESCRIPTION)
        .setEntityId(ENTITY_ID)
        .setEntityType(EntityType.GROUP);
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
