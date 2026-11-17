/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.expression;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link io.camunda.zeebe.protocol.impl.record.value.expression.legacy.ExpressionRecord} is
 * a frozen copy of the property based implementation the generated codec replaced.
 */
final class ExpressionRecordSerdeTest {

  // a msgpack map with one entry: {"a": 1}
  private static final byte[] DOCUMENT = {(byte) 0x81, (byte) 0xa1, 0x61, 0x01};

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.expression.legacy.ExpressionRecord();
    final var current = new ExpressionRecord();
    v1.setExpression("an-expression-that-is-longer-than-thirty-two-bytes");
    current.setExpression("an-expression-that-is-longer-than-thirty-two-bytes");
    v1.setResultValue(bytes(0x2a));
    current.setResultValue(bytes(0x2a));
    v1.setWarnings(List.of("first-warning", "second-warning"));
    current.setWarnings(List.of("first-warning", "second-warning"));
    v1.setTenantId("tenant-a");
    current.setTenantId("tenant-a");
    v1.setVariables(bytes(DOCUMENT));
    current.setVariables(bytes(DOCUMENT));
    v1.setScopeKey(4_294_967_296L);
    current.setScopeKey(4_294_967_296L);
    v1.addReferencedSecret("a-store-id-that-is-longer-than-thirty-two-bytes", "secret");
    current.addReferencedSecret("a-store-id-that-is-longer-than-thirty-two-bytes", "secret");
    v1.setStorageOrdinal(300);
    current.setStorageOrdinal(300);

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
        new io.camunda.zeebe.protocol.impl.record.value.expression.legacy.ExpressionRecord();
    final var current = new ExpressionRecord();
    v1.setExpression("expression");
    current.setExpression("expression");

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
        new io.camunda.zeebe.protocol.impl.record.value.expression.legacy.ExpressionRecord();
    v1.setExpression("expression-a");
    v1.setResultValue(bytes(0x2a));
    v1.setWarnings(List.of("first-warning", "second-warning"));
    v1.setTenantId("tenant-a");
    v1.setVariables(bytes(DOCUMENT));
    v1.setScopeKey(42L);
    v1.addReferencedSecret("store-a", "secret-a");
    v1.setStorageOrdinal(300);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new ExpressionRecord();
    current.setExpression("expression-b");
    current.setResultValue(bytes(0x01));
    current.setWarnings(List.of("other"));
    current.setTenantId("tenant-b");
    current.setVariables(bytes(0x80));
    current.setScopeKey(7L);
    current.addReferencedSecret("store-b", "secret-b");
    current.setStorageOrdinal(5);

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getExpression()).isEqualTo("expression-a");
    assertThat(current.getResultValue()).hasToString("42");
    assertThat(current.getWarnings()).containsExactly("first-warning", "second-warning");
    assertThat(current.getTenantId()).isEqualTo("tenant-a");
    assertThat(current.getVariables()).hasSize(1).containsKey("a");
    assertThat(current.getScopeKey()).isEqualTo(42L);
    assertThat(current.getReferencedSecrets()).hasSize(1);
    assertThat(current.getReferencedSecrets().getFirst().getStoreId()).isEqualTo("store-a");
    assertThat(current.getStorageOrdinal()).isEqualTo(300);
  }

  @Test
  void shouldRestoreDefaultsWhenReadingBytesWrittenByV1WithDefaults() {
    // given
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.expression.legacy.ExpressionRecord();
    v1.setExpression("expression");
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new ExpressionRecord();
    current.setExpression("other");
    current.setResultValue(bytes(0x01));
    current.setWarnings(List.of("other"));
    current.setTenantId("tenant-b");
    current.setVariables(bytes(DOCUMENT));
    current.setScopeKey(7L);
    current.addReferencedSecret("store-b", "secret-b");
    current.setStorageOrdinal(5);

    // when
    current.wrap(bytes, 0, length);

    // then
    final var defaults =
        new io.camunda.zeebe.protocol.impl.record.value.expression.legacy.ExpressionRecord();
    assertThat(current.getExpression()).isEqualTo("expression");
    assertThat(current.getWarnings()).isEmpty();
    assertThat(current.getTenantId()).isEqualTo(defaults.getTenantId());
    assertThat(current.getVariables()).isEqualTo(defaults.getVariables());
    assertThat(current.getScopeKey()).isEqualTo(defaults.getScopeKey());
    assertThat(current.getReferencedSecrets()).isEmpty();
    assertThat(current.getStorageOrdinal()).isEqualTo(defaults.getStorageOrdinal());
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new ExpressionRecord();
    current.setExpression("expression-a");
    current.setResultValue(bytes(0x2a));
    current.setWarnings(List.of("first-warning", "second-warning"));
    current.setTenantId("tenant-a");
    current.setVariables(bytes(DOCUMENT));
    current.setScopeKey(42L);
    current.addReferencedSecret("store-a", "secret-a");
    current.setStorageOrdinal(300);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.expression.legacy.ExpressionRecord();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getExpression()).isEqualTo("expression-a");
    assertThat(v1.getResultValue()).hasToString("42");
    assertThat(v1.getWarnings()).containsExactly("first-warning", "second-warning");
    assertThat(v1.getTenantId()).isEqualTo("tenant-a");
    assertThat(v1.getVariables()).hasSize(1).containsKey("a");
    assertThat(v1.getScopeKey()).isEqualTo(42L);
    assertThat(v1.getReferencedSecrets().getFirst().getStoreId()).isEqualTo("store-a");
    assertThat(v1.getStorageOrdinal()).isEqualTo(300);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new ExpressionRecord();
    source.setExpression("expression");
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // the map has eight entries, plus one unknown: 0x88 becomes 0x89
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new ExpressionRecord();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setStorageOrdinal(9);
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
        new io.camunda.zeebe.protocol.impl.record.value.expression.legacy.ExpressionRecord();
    final var current = new ExpressionRecord();
    v1.setExpression("expression-a");
    current.setExpression("expression-a");
    v1.setResultValue(bytes(0x2a));
    current.setResultValue(bytes(0x2a));
    v1.setWarnings(List.of("first-warning", "second-warning"));
    current.setWarnings(List.of("first-warning", "second-warning"));
    v1.setTenantId("tenant-a");
    current.setTenantId("tenant-a");
    v1.setVariables(bytes(DOCUMENT));
    current.setVariables(bytes(DOCUMENT));
    v1.setScopeKey(42L);
    current.setScopeKey(42L);
    v1.addReferencedSecret("store-a", "secret-a");
    current.addReferencedSecret("store-a", "secret-a");
    v1.setStorageOrdinal(300);
    current.setStorageOrdinal(300);

    // when
    final var v1Json = v1.toJson();
    final var currentJson = current.toJson();

    // then exporters see the same document; compare the trees, Jackson does not guarantee the order
    // of the properties and it differs between the old and the new class
    final var mapper = new ObjectMapper();
    assertThat(mapper.readTree(currentJson)).isEqualTo(mapper.readTree(v1Json));
  }

  private static DirectBuffer bytes(final int... value) {
    final byte[] bytes = new byte[value.length];
    for (int i = 0; i < value.length; i++) {
      bytes[i] = (byte) value[i];
    }
    return new UnsafeBuffer(bytes);
  }

  private static DirectBuffer bytes(final byte[] value) {
    return new UnsafeBuffer(value.clone());
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
