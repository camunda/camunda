/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.msgpack.MsgpackPropertyException;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.record.value.HistoryDeletionType;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link HistoryDeletionRecordV1} is a frozen copy of the property based implementation the
 * generated codec replaced.
 */
final class HistoryDeletionRecordSerdeTest {

  private static final String PROCESS_ID = "a process id that is longer than forty bytes";
  private static final String TENANT_ID = "a-tenant";
  private static final String DECISION_ID = "a decision definition id";

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new HistoryDeletionRecordV1();
    final var current = new HistoryDeletionRecord();
    setAllProperties(v1);
    setAllProperties(current);

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
    // given only the properties without a default are set, everything else stays default
    final var v1 = new HistoryDeletionRecordV1();
    final var current = new HistoryDeletionRecord();
    setRequiredProperties(v1);
    setRequiredProperties(current);

    // when
    final var v1Bytes = new ExpandableArrayBuffer();
    final int v1Length = v1.write(v1Bytes, 0);
    final var currentBytes = new ExpandableArrayBuffer();
    final int currentLength = current.write(currentBytes, 0);

    // then
    assertThat(hex(currentBytes, currentLength)).isEqualTo(hex(v1Bytes, v1Length));
  }

  @Test
  void shouldFailToWriteWhileUnsetLikeV1() {
    // given
    final var v1 = new HistoryDeletionRecordV1();
    final var current = new HistoryDeletionRecord();

    // when / then
    assertThatThrownBy(() -> v1.write(new ExpandableArrayBuffer(), 0))
        .isInstanceOf(MsgpackPropertyException.class);
    assertThatThrownBy(() -> current.write(new ExpandableArrayBuffer(), 0))
        .isInstanceOf(MsgpackPropertyException.class);
  }

  @Test
  void shouldReadBytesWrittenByV1() {
    // given
    final var v1 = new HistoryDeletionRecordV1();
    setAllProperties(v1);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new HistoryDeletionRecord();
    current
        .setResourceKey(7L)
        .setResourceType(HistoryDeletionType.DECISION_INSTANCE)
        .setResourceStorageOrdinal(5)
        .setProcessId("dirty")
        .setTenantId("dirty")
        .setDecisionDefinitionId("dirty");

    // when
    current.wrap(bytes, 0, length);

    // then
    assertAllProperties(current);
  }

  @Test
  void shouldRestoreDefaultsWhenReadingBytesWrittenByV1WithDefaults() {
    // given a value with defaults everywhere except the properties without a default
    final var v1 = new HistoryDeletionRecordV1();
    setRequiredProperties(v1);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new HistoryDeletionRecord();
    setAllProperties(current);

    // when
    current.wrap(bytes, 0, length);

    // then
    final var defaults = new HistoryDeletionRecordV1();
    setRequiredProperties(defaults);
    assertDefaultsOf(defaults, current);
  }

  @Test
  void shouldFailToReadWithoutTheKeysLikeV1() {
    // given an empty map
    final var bytes = new ExpandableArrayBuffer(1);
    bytes.putByte(0, (byte) 0x80);

    // when
    final var v1Failure = catchThrowable(() -> new HistoryDeletionRecordV1().wrap(bytes, 0, 1));
    final var failure = catchThrowable(() -> new HistoryDeletionRecord().wrap(bytes, 0, 1));

    // then the same failure as V1
    assertThat(failure.getCause()).isExactlyInstanceOf(v1Failure.getCause().getClass());
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new HistoryDeletionRecord();
    setAllProperties(current);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new HistoryDeletionRecordV1();
    v1.wrap(bytes, 0, length);

    // then
    assertAllProperties(v1);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new HistoryDeletionRecord();
    setAllProperties(source);
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // the map has six entries, plus one unknown: 0x86 becomes 0x87
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new HistoryDeletionRecord();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setProcessId("modified");
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  @Test
  void shouldProduceTheSameJsonAsV1() throws Exception {
    // given
    final var v1 = new HistoryDeletionRecordV1();
    final var current = new HistoryDeletionRecord();
    setAllProperties(v1);
    setAllProperties(current);

    // when
    final var v1Json = v1.toJson();
    final var currentJson = current.toJson();

    // then exporters see the same document; compare the trees, Jackson does not guarantee the order
    // of the properties and it differs between the old and the new class
    final var mapper = new ObjectMapper();
    assertThat(mapper.readTree(currentJson)).isEqualTo(mapper.readTree(v1Json));
  }

  private static void setAllProperties(final HistoryDeletionRecordV1 record) {
    record
        .setResourceKey(42L)
        .setResourceType(HistoryDeletionType.PROCESS_DEFINITION)
        .setResourceStorageOrdinal(3)
        .setProcessId(PROCESS_ID)
        .setTenantId(TENANT_ID)
        .setDecisionDefinitionId(DECISION_ID);
  }

  private static void setAllProperties(final HistoryDeletionRecord record) {
    record
        .setResourceKey(42L)
        .setResourceType(HistoryDeletionType.PROCESS_DEFINITION)
        .setResourceStorageOrdinal(3)
        .setProcessId(PROCESS_ID)
        .setTenantId(TENANT_ID)
        .setDecisionDefinitionId(DECISION_ID);
  }

  private static void setRequiredProperties(final HistoryDeletionRecordV1 record) {
    record.setResourceKey(42L).setResourceType(HistoryDeletionType.PROCESS_INSTANCE);
  }

  private static void setRequiredProperties(final HistoryDeletionRecord record) {
    record.setResourceKey(42L).setResourceType(HistoryDeletionType.PROCESS_INSTANCE);
  }

  private static void assertAllProperties(final HistoryDeletionRecordV1 record) {
    assertThat(record.getResourceKey()).isEqualTo(42L);
    assertThat(record.getResourceType()).isEqualTo(HistoryDeletionType.PROCESS_DEFINITION);
    assertThat(record.getResourceStorageOrdinal()).isEqualTo(3);
    assertThat(record.getProcessId()).isEqualTo(PROCESS_ID);
    assertThat(record.getTenantId()).isEqualTo(TENANT_ID);
    assertThat(record.getDecisionDefinitionId()).isEqualTo(DECISION_ID);
  }

  private static void assertAllProperties(final HistoryDeletionRecord record) {
    assertThat(record.getResourceKey()).isEqualTo(42L);
    assertThat(record.getResourceType()).isEqualTo(HistoryDeletionType.PROCESS_DEFINITION);
    assertThat(record.getResourceStorageOrdinal()).isEqualTo(3);
    assertThat(record.getProcessId()).isEqualTo(PROCESS_ID);
    assertThat(record.getTenantId()).isEqualTo(TENANT_ID);
    assertThat(record.getDecisionDefinitionId()).isEqualTo(DECISION_ID);
  }

  private static void assertDefaultsOf(
      final HistoryDeletionRecordV1 defaults, final HistoryDeletionRecord record) {
    assertThat(record.getResourceKey()).isEqualTo(defaults.getResourceKey());
    assertThat(record.getResourceType()).isEqualTo(defaults.getResourceType());
    assertThat(record.getResourceStorageOrdinal()).isEqualTo(defaults.getResourceStorageOrdinal());
    assertThat(record.getProcessId()).isEqualTo(defaults.getProcessId());
    assertThat(record.getTenantId()).isEqualTo(defaults.getTenantId());
    assertThat(record.getDecisionDefinitionId()).isEqualTo(defaults.getDecisionDefinitionId());
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
