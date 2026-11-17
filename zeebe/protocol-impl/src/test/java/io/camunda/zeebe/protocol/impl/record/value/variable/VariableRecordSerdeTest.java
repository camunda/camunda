/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.variable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.msgpack.MsgpackPropertyException;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.record.value.VariableOperationType;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link io.camunda.zeebe.protocol.impl.record.value.variable.legacy.VariableRecord} is a
 * frozen copy of the property based implementation the generated codec replaced.
 */
final class VariableRecordSerdeTest {

  private static final String LONG_0 = "a variable name is longer than forty bytes";

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new io.camunda.zeebe.protocol.impl.record.value.variable.legacy.VariableRecord();
    final var current = new VariableRecord();
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
    final var v1 = new io.camunda.zeebe.protocol.impl.record.value.variable.legacy.VariableRecord();
    final var current = new VariableRecord();
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
    final var v1 = new io.camunda.zeebe.protocol.impl.record.value.variable.legacy.VariableRecord();
    final var current = new VariableRecord();

    // when / then
    assertThatThrownBy(() -> v1.write(new ExpandableArrayBuffer(), 0))
        .isInstanceOf(MsgpackPropertyException.class);
    assertThatThrownBy(() -> current.write(new ExpandableArrayBuffer(), 0))
        .isInstanceOf(MsgpackPropertyException.class);
  }

  @Test
  void shouldReadBytesWrittenByV1() {
    // given
    final var v1 = new io.camunda.zeebe.protocol.impl.record.value.variable.legacy.VariableRecord();
    setAllProperties(v1);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new VariableRecord();

    // when
    current.wrap(bytes, 0, length);

    // then
    assertAllProperties(current);
  }

  @Test
  void shouldRestoreDefaultsWhenReadingBytesWrittenByV1WithDefaults() {
    // given a value with defaults everywhere except the properties without a default
    final var v1 = new io.camunda.zeebe.protocol.impl.record.value.variable.legacy.VariableRecord();
    setRequiredProperties(v1);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new VariableRecord();
    setAllProperties(current);

    // when
    current.wrap(bytes, 0, length);

    // then
    final var defaults =
        new io.camunda.zeebe.protocol.impl.record.value.variable.legacy.VariableRecord();
    setRequiredProperties(defaults);
    assertDefaultsOf(defaults, current);
  }

  @Test
  void shouldFailToReadWithoutTheKeysLikeV1() {
    // given an empty map
    final var bytes = new ExpandableArrayBuffer(1);
    bytes.putByte(0, (byte) 0x80);

    // when
    final var v1Failure =
        catchThrowable(
            () ->
                new io.camunda.zeebe.protocol.impl.record.value.variable.legacy.VariableRecord()
                    .wrap(bytes, 0, 1));
    final var failure = catchThrowable(() -> new VariableRecord().wrap(bytes, 0, 1));

    // then the same failure as V1
    assertThat(failure.getCause()).isExactlyInstanceOf(v1Failure.getCause().getClass());
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new VariableRecord();
    setAllProperties(current);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new io.camunda.zeebe.protocol.impl.record.value.variable.legacy.VariableRecord();
    v1.wrap(bytes, 0, length);

    // then
    assertAllProperties(v1);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new VariableRecord();
    setAllProperties(source);
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // the map has 10 entries, plus one unknown: 0x8a becomes 0x8b
    stored.putByte(0, (byte) (stored.getByte(0) + 1));
    // when read, modified and written again
    final var instance = new VariableRecord();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setTenantId("modified");
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  @Test
  void shouldProduceTheSameJsonAsV1() throws Exception {
    // given
    final var v1 = new io.camunda.zeebe.protocol.impl.record.value.variable.legacy.VariableRecord();
    final var current = new VariableRecord();
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
      final io.camunda.zeebe.protocol.impl.record.value.variable.legacy.VariableRecord record) {
    record
        .setName(buf(LONG_0))
        .setValue(doc())
        .setScopeKey(1L)
        .setProcessInstanceKey(2L)
        .setProcessDefinitionKey(3L)
        .setBpmnProcessId(buf("process"))
        .setTenantId("tenant")
        .setRootProcessInstanceKey(4L)
        .setStorageOrdinal(5)
        .setSource(
            new io.camunda.zeebe.protocol.impl.record.value.variable.legacy.VariableSourceRecord()
                .setType(VariableOperationType.API));
  }

  private static void setAllProperties(final VariableRecord record) {
    record
        .setName(buf(LONG_0))
        .setValue(doc())
        .setScopeKey(1L)
        .setProcessInstanceKey(2L)
        .setProcessDefinitionKey(3L)
        .setBpmnProcessId(buf("process"))
        .setTenantId("tenant")
        .setRootProcessInstanceKey(4L)
        .setStorageOrdinal(5)
        .setSource(new VariableSourceRecord().setType(VariableOperationType.API));
  }

  private static void setRequiredProperties(
      final io.camunda.zeebe.protocol.impl.record.value.variable.legacy.VariableRecord record) {
    record
        .setName(buf("name"))
        .setValue(doc())
        .setScopeKey(1L)
        .setProcessInstanceKey(2L)
        .setProcessDefinitionKey(3L);
  }

  private static void setRequiredProperties(final VariableRecord record) {
    record
        .setName(buf("name"))
        .setValue(doc())
        .setScopeKey(1L)
        .setProcessInstanceKey(2L)
        .setProcessDefinitionKey(3L);
  }

  private static void assertAllProperties(
      final io.camunda.zeebe.protocol.impl.record.value.variable.legacy.VariableRecord record) {
    assertThat(record.getName()).isEqualTo(LONG_0);
    assertThat(record.getValue()).isEqualTo("{\"a\":1}");
    assertThat(record.getScopeKey()).isEqualTo(1L);
    assertThat(record.getProcessInstanceKey()).isEqualTo(2L);
    assertThat(record.getProcessDefinitionKey()).isEqualTo(3L);
    assertThat(record.getBpmnProcessId()).isEqualTo("process");
    assertThat(record.getTenantId()).isEqualTo("tenant");
    assertThat(record.getRootProcessInstanceKey()).isEqualTo(4L);
    assertThat(record.getStorageOrdinal()).isEqualTo(5);
    assertThat(record.getSource().getType()).isEqualTo(VariableOperationType.API);
  }

  private static void assertAllProperties(final VariableRecord record) {
    assertThat(record.getName()).isEqualTo(LONG_0);
    assertThat(record.getValue()).isEqualTo("{\"a\":1}");
    assertThat(record.getScopeKey()).isEqualTo(1L);
    assertThat(record.getProcessInstanceKey()).isEqualTo(2L);
    assertThat(record.getProcessDefinitionKey()).isEqualTo(3L);
    assertThat(record.getBpmnProcessId()).isEqualTo("process");
    assertThat(record.getTenantId()).isEqualTo("tenant");
    assertThat(record.getRootProcessInstanceKey()).isEqualTo(4L);
    assertThat(record.getStorageOrdinal()).isEqualTo(5);
    assertThat(record.getSource().getType()).isEqualTo(VariableOperationType.API);
  }

  private static void assertDefaultsOf(
      final io.camunda.zeebe.protocol.impl.record.value.variable.legacy.VariableRecord defaults,
      final VariableRecord record) {
    assertThat(record.getName()).isEqualTo(defaults.getName());
    assertThat(record.getValue()).isEqualTo(defaults.getValue());
    assertThat(record.getScopeKey()).isEqualTo(defaults.getScopeKey());
    assertThat(record.getProcessInstanceKey()).isEqualTo(defaults.getProcessInstanceKey());
    assertThat(record.getProcessDefinitionKey()).isEqualTo(defaults.getProcessDefinitionKey());
    assertThat(record.getBpmnProcessId()).isEqualTo(defaults.getBpmnProcessId());
    assertThat(record.getTenantId()).isEqualTo(defaults.getTenantId());
    assertThat(record.getRootProcessInstanceKey()).isEqualTo(defaults.getRootProcessInstanceKey());
    assertThat(record.getStorageOrdinal()).isEqualTo(defaults.getStorageOrdinal());
    assertThat(record.getSource().getType()).isEqualTo(defaults.getSource().getType());
  }

  private static DirectBuffer doc() {
    return new UnsafeBuffer(MsgPackConverter.convertToMsgPack("{\"a\":1}"));
  }

  private static DirectBuffer buf(final String s) {
    return utf8(s);
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
