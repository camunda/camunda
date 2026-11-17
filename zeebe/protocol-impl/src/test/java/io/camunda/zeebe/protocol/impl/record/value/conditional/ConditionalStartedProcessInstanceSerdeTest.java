/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.conditional;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.impl.record.value.LegacyObjectValues;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. The legacy class is a frozen copy of the property based implementation the generated codec
 * replaced.
 */
final class ConditionalStartedProcessInstanceSerdeTest {

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.conditional.legacy
            .ConditionalStartedProcessInstance();
    final var current = new ConditionalStartedProcessInstance();
    setAllProperties(v1);
    setAllProperties(current);

    // when
    final var v1Bytes = new ExpandableArrayBuffer();
    final int v1Length = LegacyObjectValues.write(v1, v1Bytes);
    final var currentBytes = new ExpandableArrayBuffer();
    final int currentLength = current.write(currentBytes, 0);

    // then
    assertThat(currentLength)
        .isEqualTo(current.getLength())
        .isEqualTo(LegacyObjectValues.length(v1));
    assertThat(hex(currentBytes, currentLength)).isEqualTo(hex(v1Bytes, v1Length));
  }

  @Test
  void shouldWriteTheSameBytesAsV1WithDefaults() {
    // given only what cannot be written unset, everything else stays default
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.conditional.legacy
            .ConditionalStartedProcessInstance();
    final var current = new ConditionalStartedProcessInstance();
    setRequiredProperties(v1);
    setRequiredProperties(current);

    // when
    final var v1Bytes = new ExpandableArrayBuffer();
    final int v1Length = LegacyObjectValues.write(v1, v1Bytes);
    final var currentBytes = new ExpandableArrayBuffer();
    final int currentLength = current.write(currentBytes, 0);

    // then
    assertThat(hex(currentBytes, currentLength)).isEqualTo(hex(v1Bytes, v1Length));
  }

  @Test
  void shouldReadBytesWrittenByV1() {
    // given
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.conditional.legacy
            .ConditionalStartedProcessInstance();
    v1.setProcessDefinitionKey(11L).setProcessInstanceKey(22L).setStorageOrdinal(3);
    final var bytes = new ExpandableArrayBuffer();
    final int length = LegacyObjectValues.write(v1, bytes);
    final var current = new ConditionalStartedProcessInstance();
    current.setProcessDefinitionKey(7L).setProcessInstanceKey(8L).setStorageOrdinal(9);

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getProcessDefinitionKey()).isEqualTo(11L);
    assertThat(current.getProcessInstanceKey()).isEqualTo(22L);
    assertThat(current.getStorageOrdinal()).isEqualTo(3);
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new ConditionalStartedProcessInstance();
    current.setProcessDefinitionKey(11L).setProcessInstanceKey(22L).setStorageOrdinal(3);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.conditional.legacy
            .ConditionalStartedProcessInstance();
    LegacyObjectValues.read(v1, bytes, length);

    // then
    assertThat(v1.getProcessDefinitionKey()).isEqualTo(11L);
    assertThat(v1.getProcessInstanceKey()).isEqualTo(22L);
    assertThat(v1.getStorageOrdinal()).isEqualTo(3);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new ConditionalStartedProcessInstance();
    setRequiredProperties(source);
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new ConditionalStartedProcessInstance();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setProcessInstanceKey(99L);
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
        new io.camunda.zeebe.protocol.impl.record.value.conditional.legacy
            .ConditionalStartedProcessInstance();
    final var current = new ConditionalStartedProcessInstance();
    setAllProperties(v1);
    setAllProperties(current);

    // when
    final var mapper = new ObjectMapper();
    final var v1Json = mapper.readTree(mapper.writeValueAsString(v1));
    final var currentJson = mapper.readTree(mapper.writeValueAsString(current));

    // then the parent record serializes the value with Jackson, so the documents must be equal
    assertThat(currentJson).isEqualTo(v1Json);
  }

  private static void setAllProperties(
      final io.camunda.zeebe.protocol.impl.record.value.conditional.legacy
              .ConditionalStartedProcessInstance
          x) {
    x.setProcessDefinitionKey(70000L).setProcessInstanceKey(5_000_000_000L).setStorageOrdinal(300);
  }

  private static void setAllProperties(final ConditionalStartedProcessInstance x) {
    x.setProcessDefinitionKey(70000L).setProcessInstanceKey(5_000_000_000L).setStorageOrdinal(300);
  }

  private static void setRequiredProperties(
      final io.camunda.zeebe.protocol.impl.record.value.conditional.legacy
              .ConditionalStartedProcessInstance
          x) {
    // every property has a default
  }

  private static void setRequiredProperties(final ConditionalStartedProcessInstance x) {
    // every property has a default
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
