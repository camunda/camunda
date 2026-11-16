/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link DecisionRecordV1} is a frozen copy of the property based implementation the
 * generated codec replaced.
 */
final class DecisionRecordSerdeTest {

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new DecisionRecordV1();
    final var current = new DecisionRecord();
    populate(v1);
    populate(current);

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
    // given nothing set, everything stays default
    final var v1 = new DecisionRecordV1();
    final var current = new DecisionRecord();

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
    final var v1 = new DecisionRecordV1();
    populate(v1);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new DecisionRecord();
    populate(current);
    current.setDecisionId("other");
    current.setDecisionName("other");
    current.setVersion(1);
    current.setDecisionKey(7L);
    current.setDecisionRequirementsId("other");
    current.setDecisionRequirementsKey(8L);
    current.setDuplicate(false);
    current.setTenantId("tenant-b");
    current.setDeploymentKey(9L);
    current.setVersionTag("other");

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getDecisionId())
        .isEqualTo("decision-id-that-is-longer-than-thirty-two-bytes");
    assertThat(current.getDecisionName()).isEqualTo("decision-name");
    assertThat(current.getVersion()).isEqualTo(300);
    assertThat(current.getDecisionKey()).isEqualTo(4_294_967_296L);
    assertThat(current.getDecisionRequirementsId()).isEqualTo("drg-id");
    assertThat(current.getDecisionRequirementsKey()).isEqualTo(4_294_967_298L);
    assertThat(current.isDuplicate()).isTrue();
    assertThat(current.getTenantId()).isEqualTo("tenant-a");
    assertThat(current.getDeploymentKey()).isEqualTo(4_294_967_297L);
    assertThat(current.getVersionTag()).isEqualTo("version-tag");
  }

  @Test
  void shouldRestoreDefaultsWhenReadingBytesWrittenByV1WithDefaults() {
    // given
    final var v1 = new DecisionRecordV1();
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new DecisionRecord();
    populate(current);

    // when
    current.wrap(bytes, 0, length);

    // then
    final var defaults = new DecisionRecordV1();
    assertThat(current.getDecisionId()).isEqualTo(defaults.getDecisionId());
    assertThat(current.getDecisionName()).isEqualTo(defaults.getDecisionName());
    assertThat(current.getVersion()).isEqualTo(defaults.getVersion());
    assertThat(current.getDecisionKey()).isEqualTo(defaults.getDecisionKey());
    assertThat(current.getDecisionRequirementsId()).isEqualTo(defaults.getDecisionRequirementsId());
    assertThat(current.getDecisionRequirementsKey())
        .isEqualTo(defaults.getDecisionRequirementsKey());
    assertThat(current.isDuplicate()).isFalse();
    assertThat(current.getTenantId()).isEqualTo(defaults.getTenantId());
    assertThat(current.getDeploymentKey()).isEqualTo(defaults.getDeploymentKey());
    assertThat(current.getVersionTag()).isEqualTo(defaults.getVersionTag());
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new DecisionRecord();
    populate(current);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new DecisionRecordV1();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getDecisionId()).isEqualTo("decision-id-that-is-longer-than-thirty-two-bytes");
    assertThat(v1.getDecisionName()).isEqualTo("decision-name");
    assertThat(v1.getVersion()).isEqualTo(300);
    assertThat(v1.getDecisionKey()).isEqualTo(4_294_967_296L);
    assertThat(v1.getDecisionRequirementsId()).isEqualTo("drg-id");
    assertThat(v1.getDecisionRequirementsKey()).isEqualTo(4_294_967_298L);
    assertThat(v1.isDuplicate()).isTrue();
    assertThat(v1.getDeploymentKey()).isEqualTo(4_294_967_297L);
    assertThat(v1.getVersionTag()).isEqualTo("version-tag");
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new DecisionRecord();
    populate(source);
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // the map has ten entries, plus one unknown: 0x8a becomes 0x8b
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new DecisionRecord();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setVersion(9);
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  @Test
  void shouldProduceTheSameJsonAsV1() throws Exception {
    // given
    final var v1 = new DecisionRecordV1();
    final var current = new DecisionRecord();
    populate(v1);
    populate(current);

    // when
    final var v1Json = v1.toJson();
    final var currentJson = current.toJson();

    // then exporters see the same document; compare the trees, Jackson does not guarantee the order
    // of the properties and it differs between the old and the new class
    final var mapper = new ObjectMapper();
    assertThat(mapper.readTree(currentJson)).isEqualTo(mapper.readTree(v1Json));
  }

  // the same values on both classes, so that every property differs from its default
  private static void populate(final DecisionRecordV1 record) {
    record.setDecisionId("decision-id-that-is-longer-than-thirty-two-bytes");
    record.setDecisionName("decision-name");
    record.setVersion(300);
    record.setDecisionKey(4_294_967_296L);
    record.setDecisionRequirementsId("drg-id");
    record.setDecisionRequirementsKey(4_294_967_298L);
    record.setDuplicate(true);
    record.setTenantId("tenant-a");
    record.setDeploymentKey(4_294_967_297L);
    record.setVersionTag("version-tag");
  }

  private static void populate(final DecisionRecord record) {
    record.setDecisionId("decision-id-that-is-longer-than-thirty-two-bytes");
    record.setDecisionName("decision-name");
    record.setVersion(300);
    record.setDecisionKey(4_294_967_296L);
    record.setDecisionRequirementsId("drg-id");
    record.setDecisionRequirementsKey(4_294_967_298L);
    record.setDuplicate(true);
    record.setTenantId("tenant-a");
    record.setDeploymentKey(4_294_967_297L);
    record.setVersionTag("version-tag");
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
