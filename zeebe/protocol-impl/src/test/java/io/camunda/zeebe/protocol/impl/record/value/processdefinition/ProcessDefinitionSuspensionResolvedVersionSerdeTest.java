/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.processdefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.msgpack.MsgpackPropertyException;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.impl.record.value.LegacyObjectValues;
import io.camunda.zeebe.protocol.record.value.ProcessDefinitionSuspensionOutcome;
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
final class ProcessDefinitionSuspensionResolvedVersionSerdeTest {

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.processdefinition.legacy
            .ProcessDefinitionSuspensionResolvedVersion();
    final var current = new ProcessDefinitionSuspensionResolvedVersion();
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
        new io.camunda.zeebe.protocol.impl.record.value.processdefinition.legacy
            .ProcessDefinitionSuspensionResolvedVersion();
    final var current = new ProcessDefinitionSuspensionResolvedVersion();
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
  void shouldFailToWriteWhileUnsetLikeV1() {
    // given
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.processdefinition.legacy
            .ProcessDefinitionSuspensionResolvedVersion();
    final var current = new ProcessDefinitionSuspensionResolvedVersion();

    // when / then
    assertThatThrownBy(() -> LegacyObjectValues.write(v1, new ExpandableArrayBuffer()))
        .isInstanceOf(MsgpackPropertyException.class);
    assertThatThrownBy(() -> current.write(new ExpandableArrayBuffer(), 0))
        .isInstanceOf(MsgpackPropertyException.class);
  }

  @Test
  void shouldFailToReadWhilePropertiesAreMissingLikeV1() {
    // given an empty map
    final var empty = new UnsafeBuffer(new byte[] {(byte) 0x80});
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.processdefinition.legacy
            .ProcessDefinitionSuspensionResolvedVersion();
    final var current = new ProcessDefinitionSuspensionResolvedVersion();

    // when / then
    assertThatThrownBy(() -> LegacyObjectValues.read(v1, empty, empty.capacity()))
        .isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> current.wrap(empty, 0, empty.capacity()))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void shouldReadBytesWrittenByV1() {
    // given
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.processdefinition.legacy
            .ProcessDefinitionSuspensionResolvedVersion();
    v1.setProcessDefinitionKey(11L)
        .setVersion(3)
        .setOutcome(ProcessDefinitionSuspensionOutcome.RESUMED);
    final var bytes = new ExpandableArrayBuffer();
    final int length = LegacyObjectValues.write(v1, bytes);
    final var current = new ProcessDefinitionSuspensionResolvedVersion();
    current
        .setProcessDefinitionKey(7L)
        .setVersion(8)
        .setOutcome(ProcessDefinitionSuspensionOutcome.SUSPENDED);

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getProcessDefinitionKey()).isEqualTo(11L);
    assertThat(current.getVersion()).isEqualTo(3);
    assertThat(current.getOutcome()).isEqualTo(ProcessDefinitionSuspensionOutcome.RESUMED);
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new ProcessDefinitionSuspensionResolvedVersion();
    current
        .setProcessDefinitionKey(11L)
        .setVersion(3)
        .setOutcome(ProcessDefinitionSuspensionOutcome.RESUMED);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.processdefinition.legacy
            .ProcessDefinitionSuspensionResolvedVersion();
    LegacyObjectValues.read(v1, bytes, length);

    // then
    assertThat(v1.getProcessDefinitionKey()).isEqualTo(11L);
    assertThat(v1.getVersion()).isEqualTo(3);
    assertThat(v1.getOutcome()).isEqualTo(ProcessDefinitionSuspensionOutcome.RESUMED);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new ProcessDefinitionSuspensionResolvedVersion();
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
    final var instance = new ProcessDefinitionSuspensionResolvedVersion();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setVersion(99);
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
        new io.camunda.zeebe.protocol.impl.record.value.processdefinition.legacy
            .ProcessDefinitionSuspensionResolvedVersion();
    final var current = new ProcessDefinitionSuspensionResolvedVersion();
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
      final io.camunda.zeebe.protocol.impl.record.value.processdefinition.legacy
              .ProcessDefinitionSuspensionResolvedVersion
          x) {
    x.setProcessDefinitionKey(5_000_000_000L)
        .setVersion(300)
        .setOutcome(ProcessDefinitionSuspensionOutcome.ALREADY_RESUMED);
  }

  private static void setAllProperties(final ProcessDefinitionSuspensionResolvedVersion x) {
    x.setProcessDefinitionKey(5_000_000_000L)
        .setVersion(300)
        .setOutcome(ProcessDefinitionSuspensionOutcome.ALREADY_RESUMED);
  }

  private static void setRequiredProperties(
      final io.camunda.zeebe.protocol.impl.record.value.processdefinition.legacy
              .ProcessDefinitionSuspensionResolvedVersion
          x) {
    x.setOutcome(ProcessDefinitionSuspensionOutcome.SUSPENDED);
  }

  private static void setRequiredProperties(final ProcessDefinitionSuspensionResolvedVersion x) {
    x.setOutcome(ProcessDefinitionSuspensionOutcome.SUSPENDED);
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
