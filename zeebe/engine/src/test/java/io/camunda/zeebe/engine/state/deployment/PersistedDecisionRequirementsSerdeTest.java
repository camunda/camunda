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
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRequirementsRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link PersistedDecisionRequirementsV1} is a frozen copy of the property based
 * implementation the generated codec replaced.
 */
final class PersistedDecisionRequirementsSerdeTest {

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new PersistedDecisionRequirementsV1().wrap(populatedRecord());
    final var current = new PersistedDecisionRequirements().wrap(populatedRecord());

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
  void shouldWriteTheSameBytesAsV1ForACopy() {
    // given
    final var v1 = new PersistedDecisionRequirementsV1().wrap(populatedRecord()).copy();
    final var current = new PersistedDecisionRequirements().wrap(populatedRecord()).copy();

    // when
    final var v1Bytes = new ExpandableArrayBuffer();
    final int v1Length = v1.write(v1Bytes, 0);
    final var currentBytes = new ExpandableArrayBuffer();
    final int currentLength = current.write(currentBytes, 0);

    // then
    assertThat(hex(currentBytes, currentLength)).isEqualTo(hex(v1Bytes, v1Length));
  }

  @Test
  void shouldRejectWritingWhileUnsetLikeV1() {
    // given
    final var v1 = new PersistedDecisionRequirementsV1();
    final var current = new PersistedDecisionRequirements();

    // then
    assertThatThrownBy(() -> v1.write(new ExpandableArrayBuffer(), 0))
        .isInstanceOf(MsgpackPropertyException.class);
    assertThatThrownBy(() -> current.write(new ExpandableArrayBuffer(), 0))
        .isInstanceOf(MsgpackPropertyException.class);
  }

  @Test
  void shouldReadBytesWrittenByV1() {
    // given
    final var v1 = new PersistedDecisionRequirementsV1().wrap(populatedRecord());
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new PersistedDecisionRequirements().wrap(otherRecord());

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getDecisionRequirementsVersion()).isEqualTo(2);
    assertThat(current.getDecisionRequirementsKey()).isEqualTo(5L);
    assertThat(BufferUtil.bufferAsString(current.getDecisionRequirementsId())).isEqualTo("drd");
    assertThat(BufferUtil.bufferAsString(current.getResourceName())).isEqualTo("file.dmn");
    assertThat(BufferUtil.bufferAsString(current.getResource())).isEqualTo("<dmn/>");
    assertThat(current.getTenantId()).isEqualTo("tenant");
    assertThat(current.getDeploymentKey()).isEqualTo(6L);
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new PersistedDecisionRequirements().wrap(populatedRecord());
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new PersistedDecisionRequirementsV1();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getDecisionRequirementsVersion()).isEqualTo(2);
    assertThat(v1.getDecisionRequirementsKey()).isEqualTo(5L);
    assertThat(BufferUtil.bufferAsString(v1.getChecksum())).isEqualTo("checksum");
    assertThat(v1.getTenantId()).isEqualTo("tenant");
    assertThat(v1.getDeploymentKey()).isEqualTo(6L);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new PersistedDecisionRequirements().wrap(populatedRecord());
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
    final var instance = new PersistedDecisionRequirements();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setDeploymentKey(99L);
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  private static DecisionRequirementsRecord populatedRecord() {
    return new DecisionRequirementsRecord()
        .setDecisionRequirementsId("drd")
        .setDecisionRequirementsName("name")
        .setDecisionRequirementsVersion(2)
        .setDecisionRequirementsKey(5L)
        .setResourceName("file.dmn")
        .setChecksum(BufferUtil.wrapString("checksum"))
        .setResource(BufferUtil.wrapString("<dmn/>"))
        .setTenantId("tenant")
        .setDeploymentKey(6L);
  }

  private static DecisionRequirementsRecord otherRecord() {
    return new DecisionRequirementsRecord()
        .setDecisionRequirementsId("other")
        .setDecisionRequirementsName("other")
        .setDecisionRequirementsVersion(1)
        .setDecisionRequirementsKey(1L)
        .setResourceName("other.dmn")
        .setChecksum(BufferUtil.wrapString("other"))
        .setResource(BufferUtil.wrapString("other"))
        .setTenantId("other")
        .setDeploymentKey(1L);
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
