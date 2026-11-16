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
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyUpdateRecordV1;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link PersistedPolicyUpdateV1} is a frozen copy of the property based implementation the
 * generated codec replaced.
 */
final class PersistedPolicyUpdateSerdeTest {

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new PersistedPolicyUpdateV1();
    final var current = new PersistedPolicyUpdate();
    final var update = policyUpdate("policy-with-an-id-longer-than-thirty-two-bytes", 300L);
    v1.setUpdate(RecordV1s.convert(update, new PolicyUpdateRecordV1())).setBufferedChunkCount(1);
    current.setUpdate(update).setBufferedChunkCount(1);

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
    final var v1 = new PersistedPolicyUpdateV1();
    final var current = new PersistedPolicyUpdate();

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
    final var v1 = new PersistedPolicyUpdateV1();
    v1.setUpdate(RecordV1s.convert(policyUpdate("policy-a", 42L), new PolicyUpdateRecordV1()))
        .setBufferedChunkCount(2);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new PersistedPolicyUpdate();
    current.setUpdate(policyUpdate("dirty", -1L)).setBufferedChunkCount(9);

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getUpdate().getPolicyId()).isEqualTo("policy-a");
    assertThat(current.getUpdate().getSeqNo()).isEqualTo(42L);
    assertThat(current.getUpdate().getChunkCount()).isEqualTo(2);
    assertThat(current.getBufferedChunkCount()).isEqualTo(2);
  }

  @Test
  void shouldResetBufferedChunkCountWhenReadFromV1WithDefaults() {
    // given
    final var v1 = new PersistedPolicyUpdateV1();
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new PersistedPolicyUpdate();
    current.setUpdate(policyUpdate("dirty", -1L)).setBufferedChunkCount(9);

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getBufferedChunkCount()).isZero();
    assertThat(current.getUpdate().getPolicyId()).isEmpty();
    assertThat(current.getUpdate().getSeqNo()).isZero();
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new PersistedPolicyUpdate();
    current.setUpdate(policyUpdate("policy-b", 7L)).setBufferedChunkCount(1);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new PersistedPolicyUpdateV1();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getUpdate().getPolicyId()).isEqualTo("policy-b");
    assertThat(v1.getUpdate().getSeqNo()).isEqualTo(7L);
    assertThat(v1.getBufferedChunkCount()).isEqualTo(1);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new PersistedPolicyUpdate();
    source.setUpdate(policyUpdate("policy-c", 1L)).setBufferedChunkCount(1);
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // The map has two entries, so its header is 0x82; announce one more entry (0x83).
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new PersistedPolicyUpdate();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setBufferedChunkCount(2);
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
