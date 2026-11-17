/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.msgpack.MsgpackPropertyException;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link io.camunda.zeebe.engine.state.deployment.legacy.PersistedDecision} is a frozen copy
 * of the property based implementation the generated codec replaced.
 */
final class PersistedDecisionSerdeTest {

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 =
        new io.camunda.zeebe.engine.state.deployment.legacy.PersistedDecision()
            .wrap(populatedRecord());
    final var current = new PersistedDecision().wrap(populatedRecord());

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
  void shouldRejectWritingWhileUnsetLikeV1() {
    // given
    final var v1 = new io.camunda.zeebe.engine.state.deployment.legacy.PersistedDecision();
    final var current = new PersistedDecision();

    // then
    assertThatThrownBy(() -> v1.write(new ExpandableArrayBuffer(), 0))
        .isInstanceOf(MsgpackPropertyException.class);
    assertThatThrownBy(() -> current.write(new ExpandableArrayBuffer(), 0))
        .isInstanceOf(MsgpackPropertyException.class);
  }

  @Test
  void shouldReadBytesWrittenByV1() {
    // given
    final var v1 =
        new io.camunda.zeebe.engine.state.deployment.legacy.PersistedDecision()
            .wrap(populatedRecord());
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new PersistedDecision().wrap(otherRecord());

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getVersion()).isEqualTo(3);
    assertThat(current.getDecisionKey()).isEqualTo(11L);
    assertThat(current.getDecisionRequirementsKey()).isEqualTo(12L);
    assertThat(BufferUtil.bufferAsString(current.getDecisionId())).isEqualTo("decision");
    assertThat(BufferUtil.bufferAsString(current.getDecisionName())).isEqualTo("name");
    assertThat(BufferUtil.bufferAsString(current.getDecisionRequirementsId())).isEqualTo("drd");
    assertThat(current.getTenantId()).isEqualTo("tenant");
    assertThat(current.getDeploymentKey()).isEqualTo(13L);
    assertThat(current.getVersionTag()).isEqualTo("tag");
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new PersistedDecision().wrap(populatedRecord());
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new io.camunda.zeebe.engine.state.deployment.legacy.PersistedDecision();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getVersion()).isEqualTo(3);
    assertThat(v1.getDecisionKey()).isEqualTo(11L);
    assertThat(v1.getDecisionRequirementsKey()).isEqualTo(12L);
    assertThat(BufferUtil.bufferAsString(v1.getDecisionId())).isEqualTo("decision");
    assertThat(v1.getTenantId()).isEqualTo("tenant");
    assertThat(v1.getDeploymentKey()).isEqualTo(13L);
    assertThat(v1.getVersionTag()).isEqualTo("tag");
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new PersistedDecision().wrap(populatedRecord());
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // The map has nine entries, a header of 0x80 | count, so announce one more.
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new PersistedDecision();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setDeploymentKey(99L);
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  private static DecisionRecord populatedRecord() {
    return new DecisionRecord()
        .setDecisionId("decision")
        .setDecisionName("name")
        .setVersion(3)
        .setDecisionKey(11L)
        .setDecisionRequirementsId("drd")
        .setDecisionRequirementsKey(12L)
        .setTenantId("tenant")
        .setDeploymentKey(13L)
        .setVersionTag("tag");
  }

  private static DecisionRecord otherRecord() {
    return new DecisionRecord()
        .setDecisionId("other")
        .setDecisionName("other")
        .setVersion(1)
        .setDecisionKey(1L)
        .setDecisionRequirementsId("other")
        .setDecisionRequirementsKey(1L)
        .setTenantId("other")
        .setDeploymentKey(1L)
        .setVersionTag("other");
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
