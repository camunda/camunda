/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.processinstance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.msgpack.MsgpackPropertyException;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link
 * io.camunda.zeebe.protocol.impl.record.value.processinstance.legacy.ProcessEventRecord} is a
 * frozen copy of the property based implementation the generated codec replaced.
 */
final class ProcessEventRecordSerdeTest {

  // the msgpack map {"a": 1}
  private static final DirectBuffer VARIABLES =
      new UnsafeBuffer(new byte[] {(byte) 0x81, (byte) 0xa1, 'a', 0x01});
  private static final String TENANT = "a tenant id that is longer than forty bytes of utf8";
  private static final DirectBuffer TARGET = utf8("a target element id of more than forty bytes");

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.processinstance.legacy.ProcessEventRecord();
    final var current = new ProcessEventRecord();
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
    // given only the properties without a default are set, the rest stays default
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.processinstance.legacy.ProcessEventRecord();
    final var current = new ProcessEventRecord();
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
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.processinstance.legacy.ProcessEventRecord();
    final var current = new ProcessEventRecord();

    // when / then
    assertThatThrownBy(() -> v1.write(new ExpandableArrayBuffer(), 0))
        .isInstanceOf(MsgpackPropertyException.class);
    assertThatThrownBy(() -> current.write(new ExpandableArrayBuffer(), 0))
        .isInstanceOf(MsgpackPropertyException.class);
  }

  @Test
  void shouldReadBytesWrittenByV1() {
    // given
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.processinstance.legacy.ProcessEventRecord();
    setAllProperties(v1);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new ProcessEventRecord();
    current
        .setScopeKey(7L)
        .setTargetElementIdBuffer(utf8("dirty"))
        .setVariablesBuffer(new UnsafeBuffer(new byte[] {(byte) 0x80}))
        .setProcessDefinitionKey(7L)
        .setProcessInstanceKey(7L)
        .setTenantId("dirty")
        .setStorageOrdinal(7);

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getScopeKey()).isEqualTo(42L);
    assertThat(current.getTargetElementIdBuffer()).isEqualTo(TARGET);
    assertThat(current.getVariablesBuffer()).isEqualTo(VARIABLES);
    assertThat(current.getProcessDefinitionKey()).isEqualTo(43L);
    assertThat(current.getProcessInstanceKey()).isEqualTo(44L);
    assertThat(current.getTenantId()).isEqualTo(TENANT);
    assertThat(current.getStorageOrdinal()).isEqualTo(300);
  }

  @Test
  void shouldRestoreDefaultsWhenReadingBytesWrittenByV1WithDefaults() {
    // given a value with every property at its default
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.processinstance.legacy.ProcessEventRecord();
    setRequiredProperties(v1);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new ProcessEventRecord();
    setAllProperties(current);

    // when
    current.wrap(bytes, 0, length);

    // then re-encoding gives the bytes of the defaults, so no dirtied property survived
    final var currentBytes = new ExpandableArrayBuffer();
    final int currentLength = current.write(currentBytes, 0);
    assertThat(hex(currentBytes, currentLength)).isEqualTo(hex(bytes, length));
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new ProcessEventRecord();
    setAllProperties(current);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.processinstance.legacy.ProcessEventRecord();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getScopeKey()).isEqualTo(42L);
    assertThat(v1.getTargetElementIdBuffer()).isEqualTo(TARGET);
    assertThat(v1.getVariablesBuffer()).isEqualTo(VARIABLES);
    assertThat(v1.getProcessDefinitionKey()).isEqualTo(43L);
    assertThat(v1.getProcessInstanceKey()).isEqualTo(44L);
    assertThat(v1.getTenantId()).isEqualTo(TENANT);
    assertThat(v1.getStorageOrdinal()).isEqualTo(300);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new ProcessEventRecord();
    setAllProperties(source);
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // the map has 7 entries, plus one unknown: 0x87 becomes 0x88
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new ProcessEventRecord();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setProcessInstanceKey(9L);
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
        new io.camunda.zeebe.protocol.impl.record.value.processinstance.legacy.ProcessEventRecord();
    final var current = new ProcessEventRecord();
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

  private static void setAllProperties(
      final io.camunda.zeebe.protocol.impl.record.value.processinstance.legacy.ProcessEventRecord
          record) {
    record
        .setScopeKey(42L)
        .setTargetElementIdBuffer(TARGET)
        .setVariablesBuffer(VARIABLES)
        .setProcessDefinitionKey(43L)
        .setProcessInstanceKey(44L)
        .setTenantId(TENANT)
        .setStorageOrdinal(300);
  }

  private static void setAllProperties(final ProcessEventRecord record) {
    record
        .setScopeKey(42L)
        .setTargetElementIdBuffer(TARGET)
        .setVariablesBuffer(VARIABLES)
        .setProcessDefinitionKey(43L)
        .setProcessInstanceKey(44L)
        .setTenantId(TENANT)
        .setStorageOrdinal(300);
  }

  private static void setRequiredProperties(
      final io.camunda.zeebe.protocol.impl.record.value.processinstance.legacy.ProcessEventRecord
          record) {
    record.setScopeKey(5L).setTargetElementIdBuffer(TARGET);
  }

  private static void setRequiredProperties(final ProcessEventRecord record) {
    record.setScopeKey(5L).setTargetElementIdBuffer(TARGET);
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
