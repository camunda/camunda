/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.fasterxml.jackson.databind.ObjectMapper;
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
 * break. {@link io.camunda.zeebe.protocol.impl.record.value.user.legacy.UserRecord} is a frozen
 * copy of the property based implementation the generated codec replaced.
 */
final class UserRecordSerdeTest {

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new io.camunda.zeebe.protocol.impl.record.value.user.legacy.UserRecord();
    final var current = new UserRecord();
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
    // given only the properties without a default are set, everything else stays default
    final var v1 = new io.camunda.zeebe.protocol.impl.record.value.user.legacy.UserRecord();
    final var current = new UserRecord();
    setRequiredProperties(v1);
    setRequiredProperties(current);

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
    final var v1 = new io.camunda.zeebe.protocol.impl.record.value.user.legacy.UserRecord();
    final var current = new UserRecord();

    // when / then
    assertThatThrownBy(() -> v1.write(new ExpandableArrayBuffer(), 0))
        .isInstanceOf(MsgpackPropertyException.class);
    assertThatThrownBy(() -> current.write(new ExpandableArrayBuffer(), 0))
        .isInstanceOf(MsgpackPropertyException.class);
  }

  @Test
  void shouldReadBytesWrittenByV1() {
    // given
    final var v1 = new io.camunda.zeebe.protocol.impl.record.value.user.legacy.UserRecord();
    setAllProperties(v1);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new UserRecord();

    // when
    current.wrap(bytes, 0, length);

    // then
    assertAllProperties(current);
  }

  @Test
  void shouldRestoreDefaultsWhenReadingBytesWrittenByV1WithDefaults() {
    // given a value with defaults everywhere except the properties without a default
    final var v1 = new io.camunda.zeebe.protocol.impl.record.value.user.legacy.UserRecord();
    setRequiredProperties(v1);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new UserRecord();
    setAllProperties(current);

    // when
    current.wrap(bytes, 0, length);

    // then
    final var defaults = new io.camunda.zeebe.protocol.impl.record.value.user.legacy.UserRecord();
    setRequiredProperties(defaults);
    assertDefaultsOf(defaults, current);
  }

  @Test
  void shouldFailToReadWithoutTheKeysLikeV1() {
    // given an empty map
    final var bytes = new ExpandableArrayBuffer(1);
    bytes.putByte(0, (byte) 0x80);

    // when
    final var v1Failure =
        catchThrowable(
            () ->
                new io.camunda.zeebe.protocol.impl.record.value.user.legacy.UserRecord()
                    .wrap(bytes, 0, 1));
    final var failure = catchThrowable(() -> new UserRecord().wrap(bytes, 0, 1));

    // then the same failure as V1
    assertThat(failure.getCause()).isExactlyInstanceOf(v1Failure.getCause().getClass());
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new UserRecord();
    setAllProperties(current);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new io.camunda.zeebe.protocol.impl.record.value.user.legacy.UserRecord();
    v1.wrap(bytes, 0, length);

    // then
    assertAllProperties(v1);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new UserRecord();
    setAllProperties(source);
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // the map has 5 entries, plus one unknown: 0x85 becomes 0x86
    stored.putByte(0, (byte) (stored.getByte(0) + 1));
    // when read, modified and written again
    final var instance = new UserRecord();
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
    final var v1 = new io.camunda.zeebe.protocol.impl.record.value.user.legacy.UserRecord();
    final var current = new UserRecord();
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
      final io.camunda.zeebe.protocol.impl.record.value.user.legacy.UserRecord record) {
    record
        .setUserKey(42L)
        .setUsername("a username is longer than forty bytes")
        .setName("name")
        .setEmail("email@example.com")
        .setPassword("password");
  }

  private static void setAllProperties(final UserRecord record) {
    record
        .setUserKey(42L)
        .setUsername("a username is longer than forty bytes")
        .setName("name")
        .setEmail("email@example.com")
        .setPassword("password");
  }

  private static void setRequiredProperties(
      final io.camunda.zeebe.protocol.impl.record.value.user.legacy.UserRecord record) {
    record.setUsername("username");
  }

  private static void setRequiredProperties(final UserRecord record) {
    record.setUsername("username");
  }

  private static void assertAllProperties(
      final io.camunda.zeebe.protocol.impl.record.value.user.legacy.UserRecord record) {
    assertThat(record.getUserKey()).isEqualTo(42L);
    assertThat(record.getUsername()).isEqualTo("a username is longer than forty bytes");
    assertThat(record.getName()).isEqualTo("name");
    assertThat(record.getEmail()).isEqualTo("email@example.com");
    assertThat(record.getPassword()).isEqualTo("password");
  }

  private static void assertAllProperties(final UserRecord record) {
    assertThat(record.getUserKey()).isEqualTo(42L);
    assertThat(record.getUsername()).isEqualTo("a username is longer than forty bytes");
    assertThat(record.getName()).isEqualTo("name");
    assertThat(record.getEmail()).isEqualTo("email@example.com");
    assertThat(record.getPassword()).isEqualTo("password");
  }

  private static void assertDefaultsOf(
      final io.camunda.zeebe.protocol.impl.record.value.user.legacy.UserRecord defaults,
      final UserRecord record) {
    assertThat(record.getUserKey()).isEqualTo(defaults.getUserKey());
    assertThat(record.getUsername()).isEqualTo(defaults.getUsername());
    assertThat(record.getName()).isEqualTo(defaults.getName());
    assertThat(record.getEmail()).isEqualTo(defaults.getEmail());
    assertThat(record.getPassword()).isEqualTo(defaults.getPassword());
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
