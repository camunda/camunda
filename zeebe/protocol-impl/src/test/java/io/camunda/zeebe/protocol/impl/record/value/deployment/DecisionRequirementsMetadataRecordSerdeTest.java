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
 * break. {@link DecisionRequirementsMetadataRecordV1} is a frozen copy of the property based
 * implementation the generated codec replaced.
 */
final class DecisionRequirementsMetadataRecordSerdeTest {

  private static final byte[] CHECKSUM = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new DecisionRequirementsMetadataRecordV1();
    final var current = new DecisionRequirementsMetadataRecord();
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
    final var v1 = new DecisionRequirementsMetadataRecordV1();
    final var current = new DecisionRequirementsMetadataRecord();

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
    final var v1 = new DecisionRequirementsMetadataRecordV1();
    populate(v1);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new DecisionRequirementsMetadataRecord();
    populate(current);
    current.setDecisionRequirementsId("other");
    current.setDecisionRequirementsName("other");
    current.setDecisionRequirementsVersion(1);
    current.setDecisionRequirementsKey(7L);
    current.setNamespace("other");
    current.setResourceName("other.dmn");
    current.setDuplicate(false);
    current.setTenantId("tenant-b");
    current.setDeploymentKey(9L);

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getDecisionRequirementsId())
        .isEqualTo("drg-id-that-is-longer-than-thirty-two-bytes");
    assertThat(current.getDecisionRequirementsName()).isEqualTo("drg-name");
    assertThat(current.getDecisionRequirementsVersion()).isEqualTo(300);
    assertThat(current.getDecisionRequirementsKey()).isEqualTo(4_294_967_296L);
    assertThat(current.getNamespace()).isEqualTo("namespace");
    assertThat(current.getResourceName()).isEqualTo("drg.dmn");
    assertThat(current.getChecksum()).containsExactly(CHECKSUM);
    assertThat(current.isDuplicate()).isTrue();
    assertThat(current.getTenantId()).isEqualTo("tenant-a");
    assertThat(current.getDeploymentKey()).isEqualTo(4_294_967_297L);
  }

  @Test
  void shouldRestoreDefaultsWhenReadingBytesWrittenByV1WithDefaults() {
    // given
    final var v1 = new DecisionRequirementsMetadataRecordV1();
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new DecisionRequirementsMetadataRecord();
    populate(current);

    // when
    current.wrap(bytes, 0, length);

    // then
    final var defaults = new DecisionRequirementsMetadataRecordV1();
    assertThat(current.getDecisionRequirementsId()).isEqualTo(defaults.getDecisionRequirementsId());
    assertThat(current.getDecisionRequirementsName())
        .isEqualTo(defaults.getDecisionRequirementsName());
    assertThat(current.getDecisionRequirementsVersion())
        .isEqualTo(defaults.getDecisionRequirementsVersion());
    assertThat(current.getDecisionRequirementsKey())
        .isEqualTo(defaults.getDecisionRequirementsKey());
    assertThat(current.getNamespace()).isEqualTo(defaults.getNamespace());
    assertThat(current.getResourceName()).isEqualTo(defaults.getResourceName());
    assertThat(current.getChecksum()).isEmpty();
    assertThat(current.isDuplicate()).isFalse();
    assertThat(current.getTenantId()).isEqualTo(defaults.getTenantId());
    assertThat(current.getDeploymentKey()).isEqualTo(defaults.getDeploymentKey());
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new DecisionRequirementsMetadataRecord();
    populate(current);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new DecisionRequirementsMetadataRecordV1();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getDecisionRequirementsId())
        .isEqualTo("drg-id-that-is-longer-than-thirty-two-bytes");
    assertThat(v1.getDecisionRequirementsName()).isEqualTo("drg-name");
    assertThat(v1.getDecisionRequirementsVersion()).isEqualTo(300);
    assertThat(v1.getDecisionRequirementsKey()).isEqualTo(4_294_967_296L);
    assertThat(v1.getNamespace()).isEqualTo("namespace");
    assertThat(v1.getResourceName()).isEqualTo("drg.dmn");
    assertThat(v1.getChecksum()).containsExactly(CHECKSUM);
    assertThat(v1.isDuplicate()).isTrue();
    assertThat(v1.getDeploymentKey()).isEqualTo(4_294_967_297L);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new DecisionRequirementsMetadataRecord();
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
    final var instance = new DecisionRequirementsMetadataRecord();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setDeploymentKey(9L);
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  @Test
  void shouldProduceTheSameJsonAsV1() throws Exception {
    // given
    final var v1 = new DecisionRequirementsMetadataRecordV1();
    final var current = new DecisionRequirementsMetadataRecord();
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
  private static void populate(final DecisionRequirementsMetadataRecordV1 record) {
    record.setDecisionRequirementsId("drg-id-that-is-longer-than-thirty-two-bytes");
    record.setDecisionRequirementsName("drg-name");
    record.setDecisionRequirementsVersion(300);
    record.setDecisionRequirementsKey(4_294_967_296L);
    record.setNamespace("namespace");
    record.setResourceName("drg.dmn");
    record.setChecksum(new UnsafeBuffer(CHECKSUM));
    record.setDuplicate(true);
    record.setTenantId("tenant-a");
    record.setDeploymentKey(4_294_967_297L);
  }

  private static void populate(final DecisionRequirementsMetadataRecord record) {
    record.setDecisionRequirementsId("drg-id-that-is-longer-than-thirty-two-bytes");
    record.setDecisionRequirementsName("drg-name");
    record.setDecisionRequirementsVersion(300);
    record.setDecisionRequirementsKey(4_294_967_296L);
    record.setNamespace("namespace");
    record.setResourceName("drg.dmn");
    record.setChecksum(new UnsafeBuffer(CHECKSUM));
    record.setDuplicate(true);
    record.setTenantId("tenant-a");
    record.setDeploymentKey(4_294_967_297L);
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
