/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link VersionInfoV1} is a frozen copy of the property based implementation the generated
 * codec replaced.
 */
final class VersionInfoSerdeTest {

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new VersionInfoV1();
    final var current = new VersionInfo();
    v1.addKnownVersion(200L);
    current.addKnownVersion(200L);
    v1.addKnownVersion(70_000L);
    current.addKnownVersion(70_000L);
    v1.addKnownVersion(3L);
    current.addKnownVersion(3L);
    v1.setHighestVersionIfHigher(70_001L);
    current.setHighestVersionIfHigher(70_001L);

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
    final var v1 = new VersionInfoV1();
    final var current = new VersionInfo();

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
    final var v1 = new VersionInfoV1();
    v1.addKnownVersion(1L);
    v1.addKnownVersion(300L);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new VersionInfo();
    current.setHighestVersionIfHigher(99L);
    current.addKnownVersion(42L);

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getHighestVersion()).isEqualTo(300L);
    assertThat(current.getKnownVersions()).containsExactly(1L, 300L);
  }

  @Test
  void shouldReadDefaultsWhenBytesHoldTheDefaults() {
    // given
    final var v1 = new VersionInfoV1();
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new VersionInfo();
    current.setHighestVersionIfHigher(99L);
    current.addKnownVersion(42L);

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getHighestVersion()).isEqualTo(-1L);
    assertThat(current.getKnownVersions()).isEmpty();
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new VersionInfo();
    current.setHighestVersionIfHigher(12L);
    current.addKnownVersion(7L);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new VersionInfoV1();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getHighestVersion()).isEqualTo(12L);
    assertThat(v1.getKnownVersions()).containsExactly(7L);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new VersionInfo();
    source.addKnownVersion(1L);
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // Two entries are announced by 0x82, so announce one more entry.
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new VersionInfo();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.addKnownVersion(2L);
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  @Test
  void shouldCopyTheStoredPropertiesOfTheOther() {
    // given
    final var source = new VersionInfo();
    source.setHighestVersionIfHigher(5L);
    source.addKnownVersion(4L);

    // when
    final var copy = new VersionInfo(source);
    source.addKnownVersion(6L);

    // then
    assertThat(copy.getHighestVersion()).isEqualTo(5L);
    assertThat(copy.getKnownVersions()).containsExactly(4L);
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
