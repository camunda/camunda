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
import java.util.List;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link io.camunda.zeebe.protocol.impl.record.value.deployment.legacy.ProcessRecord} is a
 * frozen copy of the property based implementation the generated codec replaced.
 */
final class ProcessRecordSerdeTest {

  private static final String BPMN_PROCESS_ID = "a-process-id-that-is-longer-than-thirty-two-bytes";
  private static final String RESOURCE = "<?xml version=\"1.0\"?><definitions/>";
  private static final byte[] CHECKSUM = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.deployment.legacy.ProcessRecord();
    final var current = new ProcessRecord();
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
    // given only what cannot be written unset, everything else stays default
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.deployment.legacy.ProcessRecord();
    final var current = new ProcessRecord();
    setRequired(v1);
    setRequired(current);

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
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.deployment.legacy.ProcessRecord();
    populate(v1);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new ProcessRecord();
    populate(current);
    current.setVersion(1);
    current.setKey(7L);
    current.setBpmnProcessId("other");
    current.setResourceName("other.bpmn");
    current.setDeleteHistory(false);
    current.setDrainPartitions(List.of(4));
    current.setTransformerVersions(Map.of(1, 1));

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getVersion()).isEqualTo(300);
    assertThat(current.getKey()).isEqualTo(4_294_967_296L);
    assertThat(current.getBpmnProcessId()).isEqualTo(BPMN_PROCESS_ID);
    assertThat(current.getResourceName()).isEqualTo("resource.bpmn");
    assertThat(current.getChecksum()).containsExactly(CHECKSUM);
    assertThat(current.getResource()).isEqualTo(RESOURCE.getBytes(StandardCharsets.UTF_8));
    assertThat(current.getTenantId()).isEqualTo("tenant-a");
    assertThat(current.getDeploymentKey()).isEqualTo(4_294_967_297L);
    assertThat(current.getVersionTag()).isEqualTo("version-tag");
    assertThat(current.getTransformerVersions()).isEqualTo(Map.of(3, 2, 300, 5));
    assertThat(current.isDeleteHistory()).isTrue();
    assertThat(current.getDrainPartitions()).containsExactly(1, 300, 70_000);
  }

  @Test
  void shouldRestoreDefaultsWhenReadingBytesWrittenByV1WithDefaults() {
    // given
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.deployment.legacy.ProcessRecord();
    setRequired(v1);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new ProcessRecord();
    populate(current);

    // when
    current.wrap(bytes, 0, length);

    // then
    final var defaults =
        new io.camunda.zeebe.protocol.impl.record.value.deployment.legacy.ProcessRecord();
    assertThat(current.getDeploymentKey()).isEqualTo(defaults.getDeploymentKey());
    assertThat(current.getVersionTag()).isEqualTo(defaults.getVersionTag());
    assertThat(current.getTenantId()).isEqualTo(defaults.getTenantId());
    assertThat(current.getChecksum()).isEmpty();
    assertThat(current.getResource()).isEmpty();
    assertThat(current.getTransformerVersions()).isEmpty();
    assertThat(current.isDeleteHistory()).isFalse();
    assertThat(current.getDrainPartitions()).isEmpty();
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new ProcessRecord();
    populate(current);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.deployment.legacy.ProcessRecord();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getVersion()).isEqualTo(300);
    assertThat(v1.getKey()).isEqualTo(4_294_967_296L);
    assertThat(v1.getBpmnProcessId()).isEqualTo(BPMN_PROCESS_ID);
    assertThat(v1.getResourceName()).isEqualTo("resource.bpmn");
    assertThat(v1.getChecksum()).containsExactly(CHECKSUM);
    assertThat(v1.getTransformerVersions()).isEqualTo(Map.of(3, 2, 300, 5));
    assertThat(v1.isDeleteHistory()).isTrue();
    assertThat(v1.getDrainPartitions()).containsExactly(1, 300, 70_000);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new ProcessRecord();
    setRequired(source);
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // the map has twelve entries, plus one unknown: 0x8c becomes 0x8d
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new ProcessRecord();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setDeleteHistory(true);
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
        new io.camunda.zeebe.protocol.impl.record.value.deployment.legacy.ProcessRecord();
    final var current = new ProcessRecord();
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
  private static void populate(
      final io.camunda.zeebe.protocol.impl.record.value.deployment.legacy.ProcessRecord record) {
    setRequired(record);
    record.setChecksum(new UnsafeBuffer(CHECKSUM));
    record.setResource(utf8(RESOURCE));
    record.setTenantId("tenant-a");
    record.setDeploymentKey(4_294_967_297L);
    record.setVersionTag("version-tag");
    record.setTransformerVersions(Map.of(3, 2, 300, 5));
    record.setDeleteHistory(true);
    record.setDrainPartitions(List.of(300, 1, 70_000));
  }

  private static void populate(final ProcessRecord record) {
    setRequired(record);
    record.setChecksum(new UnsafeBuffer(CHECKSUM));
    record.setResource(utf8(RESOURCE));
    record.setTenantId("tenant-a");
    record.setDeploymentKey(4_294_967_297L);
    record.setVersionTag("version-tag");
    record.setTransformerVersions(Map.of(3, 2, 300, 5));
    record.setDeleteHistory(true);
    record.setDrainPartitions(List.of(300, 1, 70_000));
  }

  // the properties without a default cannot be written while unset
  private static void setRequired(
      final io.camunda.zeebe.protocol.impl.record.value.deployment.legacy.ProcessRecord record) {
    record.setBpmnProcessId(BPMN_PROCESS_ID);
    record.setVersion(300);
    record.setKey(4_294_967_296L);
    record.setResourceName("resource.bpmn");
  }

  private static void setRequired(final ProcessRecord record) {
    record.setBpmnProcessId(BPMN_PROCESS_ID);
    record.setVersion(300);
    record.setKey(4_294_967_296L);
    record.setResourceName("resource.bpmn");
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
