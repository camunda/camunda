/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.policy;

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
 * break. {@link io.camunda.zeebe.engine.state.policy.legacy.PersistedPolicy} is a frozen copy of
 * the property based implementation the generated codec replaced.
 */
final class PersistedPolicySerdeTest {

  private static final String POLICY_ID = "a-policy-id-that-is-longer-than-thirty-two-bytes";

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new io.camunda.zeebe.engine.state.policy.legacy.PersistedPolicy();
    final var current = new PersistedPolicy();
    v1.setPolicyId(POLICY_ID).setPolicyVersion(70_000L).setSeqNo(300L);
    current.setPolicyId(POLICY_ID).setPolicyVersion(70_000L).setSeqNo(300L);

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
    // given only defaults
    final var v1 = new io.camunda.zeebe.engine.state.policy.legacy.PersistedPolicy();
    final var current = new PersistedPolicy();

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
    final var v1 = new io.camunda.zeebe.engine.state.policy.legacy.PersistedPolicy();
    v1.setPolicyId(POLICY_ID).setPolicyVersion(42L).setSeqNo(7L);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new PersistedPolicy();
    current.setPolicyId("dirty").setPolicyVersion(-5L).setSeqNo(-6L);

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getPolicyId()).isEqualTo(POLICY_ID);
    assertThat(current.getPolicyVersion()).isEqualTo(42L);
    assertThat(current.getSeqNo()).isEqualTo(7L);
  }

  @Test
  void shouldResetToDefaultsWhenReadFromV1WithDefaults() {
    // given
    final var v1 = new io.camunda.zeebe.engine.state.policy.legacy.PersistedPolicy();
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new PersistedPolicy();
    current.setPolicyId("dirty").setPolicyVersion(-5L).setSeqNo(-6L);

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getPolicyId()).isEmpty();
    assertThat(current.getPolicyVersion()).isZero();
    assertThat(current.getSeqNo()).isZero();
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new PersistedPolicy();
    current.setPolicyId(POLICY_ID).setPolicyVersion(42L).setSeqNo(7L);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new io.camunda.zeebe.engine.state.policy.legacy.PersistedPolicy();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getPolicyId()).isEqualTo(POLICY_ID);
    assertThat(v1.getPolicyVersion()).isEqualTo(42L);
    assertThat(v1.getSeqNo()).isEqualTo(7L);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new PersistedPolicy();
    source.setPolicyId(POLICY_ID).setSeqNo(1L);
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // The map has three entries, so its header is 0x83; announce one more entry (0x84).
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new PersistedPolicy();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setSeqNo(2L);
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
