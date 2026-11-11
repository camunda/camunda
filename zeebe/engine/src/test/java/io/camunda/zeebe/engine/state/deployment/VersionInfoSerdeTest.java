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
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.HexFormat;
import java.util.List;
import org.agrona.ExpandableArrayBuffer;
import org.junit.jupiter.api.Test;

/**
 * The stored format must not change, otherwise migration and rollback break. {@link VersionInfoV1}
 * is a frozen copy of the hand-written implementation the generated codec replaced.
 */
final class VersionInfoSerdeTest {

  /** Values of every msgpack integer width, and more than 15 of them to change the array header. */
  private static final List<Long> MANY_VERSIONS =
      List.of(
          1L,
          2L,
          3L,
          4L,
          5L,
          6L,
          7L,
          8L,
          9L,
          10L,
          11L,
          12L,
          13L,
          14L,
          15L,
          16L,
          17L,
          200L,
          300L,
          70_000L,
          5_000_000_000L);

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new VersionInfoV1();
    final var current = new VersionInfo();
    for (final long version : MANY_VERSIONS) {
      v1.addKnownVersion(version);
      current.addKnownVersion(version);
    }

    // when
    final var v1Bytes = new ExpandableArrayBuffer();
    final int v1Length = v1.write(v1Bytes, 0);
    final var currentBytes = new ExpandableArrayBuffer();
    final int currentLength = current.write(currentBytes, 0);

    // then
    assertThat(currentLength).isEqualTo(current.getLength()).isEqualTo(v1.getLength());
    assertThat(hex(currentBytes, currentLength)).isEqualTo(hex(v1Bytes, v1Length));
    assertThat(current.getHighestVersion()).isEqualTo(v1.getHighestVersion());
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
  void shouldReadBytesWrittenByV1IntoADirtyTarget() {
    // given
    final var v1 = new VersionInfoV1();
    v1.addKnownVersion(3L);
    v1.addKnownVersion(5L);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new VersionInfo();
    current.addKnownVersion(99L);

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getKnownVersions()).containsExactly(3L, 5L);
    assertThat(current.getHighestVersion()).isEqualTo(5L);
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new VersionInfo();
    for (final long version : MANY_VERSIONS) {
      current.addKnownVersion(version);
    }
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new VersionInfoV1();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getKnownVersions()).isEqualTo(MANY_VERSIONS);
    assertThat(v1.getHighestVersion()).isEqualTo(5_000_000_000L);
  }

  @Test
  void shouldRemoveTheSameVersionAsV1() {
    // given
    final var v1 = new VersionInfoV1();
    final var current = new VersionInfo();
    for (final long version : List.of(1L, 2L, 3L)) {
      v1.addKnownVersion(version);
      current.addKnownVersion(version);
    }

    // when
    v1.removeKnownVersion(2L);
    current.removeKnownVersion(2L);
    v1.removeKnownVersion(42L);
    current.removeKnownVersion(42L);

    // then
    assertThat(current.getKnownVersions()).isEqualTo(v1.getKnownVersions()).containsExactly(1L, 3L);
    assertThat(current.getHighestVersion()).isEqualTo(v1.getHighestVersion()).isEqualTo(3L);
  }

  @Test
  void shouldCopyThroughTheCopyConstructor() {
    // given
    final var source = new VersionInfo();
    source.addKnownVersion(7L);
    source.addKnownVersion(9L);

    // when
    final var copy = new VersionInfo(source);
    source.addKnownVersion(11L);

    // then
    assertThat(copy.getKnownVersions()).containsExactly(7L, 9L);
    assertThat(copy.getHighestVersion()).isEqualTo(9L);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a stored value that carries a property this version does not know
    final var source = new VersionInfo();
    source.addKnownVersion(1L);
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(BufferUtil.wrapString("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // 2 declared properties use a fixmap header (0x80 | count): announce one more entry
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

  private static String hex(final ExpandableArrayBuffer buffer, final int length) {
    final var bytes = new byte[length];
    buffer.getBytes(0, bytes);
    return HexFormat.of().formatHex(bytes);
  }
}
