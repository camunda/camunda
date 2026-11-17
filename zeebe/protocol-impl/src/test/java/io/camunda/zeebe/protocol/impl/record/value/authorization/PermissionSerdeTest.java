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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.msgpack.MsgpackPropertyException;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.impl.record.value.LegacyObjectValues;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link io.camunda.zeebe.protocol.impl.record.value.authorization.legacy.Permission} is a
 * frozen copy of the property based implementation the generated codec replaced.
 */
final class PermissionSerdeTest {

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.authorization.legacy.Permission();
    final var current = new Permission();
    v1.setPermissionType(PermissionType.READ);
    v1.addResourceIds(resources());
    current.setPermissionType(PermissionType.READ);
    current.addResourceIds(resources());

    // when
    final var v1Bytes = new ExpandableArrayBuffer();
    final int v1Length = LegacyObjectValues.write(v1, v1Bytes);
    final var currentBytes = new ExpandableArrayBuffer();
    final int currentLength = current.write(currentBytes, 0);

    // then
    assertThat(currentLength)
        .isEqualTo(current.getLength())
        .isEqualTo(LegacyObjectValues.length(v1));
    assertThat(hex(currentBytes, currentLength)).isEqualTo(hex(v1Bytes, v1Length));
  }

  @Test
  void shouldWriteTheSameBytesAsV1WithDefaults() {
    // given only what cannot be written unset, everything else stays default
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.authorization.legacy.Permission();
    final var current = new Permission();
    v1.setPermissionType(PermissionType.READ);
    current.setPermissionType(PermissionType.READ);

    // when
    final var v1Bytes = new ExpandableArrayBuffer();
    final int v1Length = LegacyObjectValues.write(v1, v1Bytes);
    final var currentBytes = new ExpandableArrayBuffer();
    final int currentLength = current.write(currentBytes, 0);

    // then
    assertThat(hex(currentBytes, currentLength)).isEqualTo(hex(v1Bytes, v1Length));
  }

  @Test
  void shouldFailToWriteWhilePermissionTypeIsUnset() {
    // given
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.authorization.legacy.Permission();
    final var current = new Permission();
    final var buffer = new ExpandableArrayBuffer();

    // when
    // then
    assertThatThrownBy(() -> LegacyObjectValues.write(v1, buffer))
        .isInstanceOf(MsgpackPropertyException.class);
    assertThatThrownBy(() -> current.write(buffer, 0)).isInstanceOf(MsgpackPropertyException.class);
  }

  @Test
  void shouldReadBytesWrittenByV1() {
    // given
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.authorization.legacy.Permission();
    v1.setPermissionType(PermissionType.READ);
    final var bytes = new ExpandableArrayBuffer();
    final int length = LegacyObjectValues.write(v1, bytes);
    final var current = new Permission();
    current.setPermissionType(PermissionType.DELETE);
    current.addResourceId("dirty");

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getPermissionType()).isEqualTo(PermissionType.READ);
    assertThat(current.getResourceIds()).isEqualTo(Set.of());
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new Permission();
    current.setPermissionType(PermissionType.READ);
    current.addResourceIds(resources());
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.authorization.legacy.Permission();
    LegacyObjectValues.read(v1, bytes, length);

    // then
    assertThat(v1.getPermissionType()).isEqualTo(PermissionType.READ);
    assertThat(v1.getResourceIds()).isEqualTo(resources());
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new Permission();
    source.setPermissionType(PermissionType.READ);
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new Permission();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setPermissionType(PermissionType.DELETE);
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
        new io.camunda.zeebe.protocol.impl.record.value.authorization.legacy.Permission();
    final var current = new Permission();
    v1.setPermissionType(PermissionType.READ);
    v1.addResourceIds(resources());
    current.setPermissionType(PermissionType.READ);
    current.addResourceIds(resources());

    // when
    final var mapper = new ObjectMapper();
    final var v1Json = mapper.writeValueAsString(v1);
    final var currentJson = mapper.writeValueAsString(current);

    // then exporters see the same document; compare the trees, Jackson does not guarantee the order
    // of the properties
    assertThat(mapper.readTree(currentJson)).isEqualTo(mapper.readTree(v1Json));
  }

  private static Set<String> resources() {
    return IntStream.range(0, 20).mapToObj(i -> "resource-" + i).collect(Collectors.toSet());
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
