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
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.protocol.record.value.VariableDocumentUpdateSemantic;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link io.camunda.zeebe.protocol.impl.record.value.variable.legacy.VariableDocumentRecord}
 * is a frozen copy of the property based implementation the generated codec replaced.
 */
final class VariableDocumentRecordSerdeTest {

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.variable.legacy.VariableDocumentRecord();
    final var current = new VariableDocumentRecord();
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
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.variable.legacy.VariableDocumentRecord();
    final var current = new VariableDocumentRecord();
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
        new io.camunda.zeebe.protocol.impl.record.value.variable.legacy.VariableDocumentRecord();
    final var current = new VariableDocumentRecord();

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
        new io.camunda.zeebe.protocol.impl.record.value.variable.legacy.VariableDocumentRecord();
    setAllProperties(v1);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new VariableDocumentRecord();

    // when
    current.wrap(bytes, 0, length);

    // then
    assertAllProperties(current);
  }

  @Test
  void shouldRestoreDefaultsWhenReadingBytesWrittenByV1WithDefaults() {
    // given a value with defaults everywhere except the properties without a default
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.variable.legacy.VariableDocumentRecord();
    setRequiredProperties(v1);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new VariableDocumentRecord();
    setAllProperties(current);

    // when
    current.wrap(bytes, 0, length);

    // then
    final var defaults =
        new io.camunda.zeebe.protocol.impl.record.value.variable.legacy.VariableDocumentRecord();
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
                new io.camunda.zeebe.protocol.impl.record.value.variable.legacy
                        .VariableDocumentRecord()
                    .wrap(bytes, 0, 1));
    final var failure = catchThrowable(() -> new VariableDocumentRecord().wrap(bytes, 0, 1));

    // then the same failure as V1
    assertThat(failure.getCause()).isExactlyInstanceOf(v1Failure.getCause().getClass());
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new VariableDocumentRecord();
    setAllProperties(current);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.variable.legacy.VariableDocumentRecord();
    v1.wrap(bytes, 0, length);

    // then
    assertAllProperties(v1);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new VariableDocumentRecord();
    setAllProperties(source);
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // the map has 4 entries, plus one unknown: 0x84 becomes 0x85
    stored.putByte(0, (byte) (stored.getByte(0) + 1));
    // when read, modified and written again
    final var instance = new VariableDocumentRecord();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setStorageOrdinal(99);
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
        new io.camunda.zeebe.protocol.impl.record.value.variable.legacy.VariableDocumentRecord();
    final var current = new VariableDocumentRecord();
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
      final io.camunda.zeebe.protocol.impl.record.value.variable.legacy.VariableDocumentRecord
          record) {
    record
        .setScopeKey(1L)
        .setUpdateSemantics(VariableDocumentUpdateSemantic.LOCAL)
        .setVariables(doc())
        .setStorageOrdinal(2);
  }

  private static void setAllProperties(final VariableDocumentRecord record) {
    record
        .setScopeKey(1L)
        .setUpdateSemantics(VariableDocumentUpdateSemantic.LOCAL)
        .setVariables(doc())
        .setStorageOrdinal(2);
  }

  private static void setRequiredProperties(
      final io.camunda.zeebe.protocol.impl.record.value.variable.legacy.VariableDocumentRecord
          record) {
    record.setScopeKey(1L);
  }

  private static void setRequiredProperties(final VariableDocumentRecord record) {
    record.setScopeKey(1L);
  }

  private static void assertAllProperties(
      final io.camunda.zeebe.protocol.impl.record.value.variable.legacy.VariableDocumentRecord
          record) {
    assertThat(record.getScopeKey()).isEqualTo(1L);
    assertThat(record.getUpdateSemantics()).isEqualTo(VariableDocumentUpdateSemantic.LOCAL);
    assertThat(record.getVariables()).isEqualTo(Map.of("a", 1));
    assertThat(record.getStorageOrdinal()).isEqualTo(2);
    assertThat(record.getTenantId()).isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  }

  private static void assertAllProperties(final VariableDocumentRecord record) {
    assertThat(record.getScopeKey()).isEqualTo(1L);
    assertThat(record.getUpdateSemantics()).isEqualTo(VariableDocumentUpdateSemantic.LOCAL);
    assertThat(record.getVariables()).isEqualTo(Map.of("a", 1));
    assertThat(record.getStorageOrdinal()).isEqualTo(2);
    assertThat(record.getTenantId()).isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  }

  private static void assertDefaultsOf(
      final io.camunda.zeebe.protocol.impl.record.value.variable.legacy.VariableDocumentRecord
          defaults,
      final VariableDocumentRecord record) {
    assertThat(record.getScopeKey()).isEqualTo(defaults.getScopeKey());
    assertThat(record.getUpdateSemantics()).isEqualTo(defaults.getUpdateSemantics());
    assertThat(record.getVariables()).isEqualTo(defaults.getVariables());
    assertThat(record.getStorageOrdinal()).isEqualTo(defaults.getStorageOrdinal());
    assertThat(record.getTenantId()).isEqualTo(defaults.getTenantId());
  }

  private static DirectBuffer doc() {
    return new UnsafeBuffer(MsgPackConverter.convertToMsgPack("{\"a\":1}"));
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
