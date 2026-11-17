/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.policy;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.RecordV1s;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyUpdateRecord;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link io.camunda.zeebe.engine.state.policy.legacy.PersistedPolicyUpdateChunk} is a frozen
 * copy of the property based implementation the generated codec replaced.
 */
final class PersistedPolicyUpdateChunkSerdeTest {

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new io.camunda.zeebe.engine.state.policy.legacy.PersistedPolicyUpdateChunk();
    final var current = new PersistedPolicyUpdateChunk();
    final var chunk = policyUpdate("policy-with-an-id-longer-than-thirty-two-bytes", 300L);
    v1.setChunk(
        RecordV1s.convert(
            chunk,
            new io.camunda.zeebe.protocol.impl.record.value.policyupdate.legacy
                .PolicyUpdateRecord()));
    current.setChunk(chunk);

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
    final var v1 = new io.camunda.zeebe.engine.state.policy.legacy.PersistedPolicyUpdateChunk();
    final var current = new PersistedPolicyUpdateChunk();

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
    final var v1 = new io.camunda.zeebe.engine.state.policy.legacy.PersistedPolicyUpdateChunk();
    v1.setChunk(
        RecordV1s.convert(
            policyUpdate("policy-a", 42L),
            new io.camunda.zeebe.protocol.impl.record.value.policyupdate.legacy
                .PolicyUpdateRecord()));
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new PersistedPolicyUpdateChunk();
    current.setChunk(policyUpdate("dirty", -1L));

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getChunk().getPolicyId()).isEqualTo("policy-a");
    assertThat(current.getChunk().getSeqNo()).isEqualTo(42L);
    assertThat(current.getChunk().getChunkCount()).isEqualTo(2);
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new PersistedPolicyUpdateChunk();
    current.setChunk(policyUpdate("policy-b", 7L));
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new io.camunda.zeebe.engine.state.policy.legacy.PersistedPolicyUpdateChunk();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getChunk().getPolicyId()).isEqualTo("policy-b");
    assertThat(v1.getChunk().getSeqNo()).isEqualTo(7L);
    assertThat(v1.getChunk().getChunkCount()).isEqualTo(2);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new PersistedPolicyUpdateChunk();
    source.setChunk(policyUpdate("policy-c", 1L));
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // The map has one entry, so its header is 0x81; announce one more entry (0x82).
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new PersistedPolicyUpdateChunk();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setChunk(policyUpdate("policy-d", 2L));
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  private static PolicyUpdateRecord policyUpdate(final String policyId, final long seqNo) {
    return new PolicyUpdateRecord()
        .setPolicyId(policyId)
        .setPolicyVersion(3L)
        .setBasePolicyVersion(2L)
        .setSeqNo(seqNo)
        .setUpdateId("update-1")
        .setChunkIndex(1)
        .setChunkCount(2);
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
