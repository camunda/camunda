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
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.LegacyObjectValues;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Set;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. The legacy class is a frozen copy of the property based implementation the generated codec
 * replaced.
 */
final class ProcessInstanceModificationActivateInstructionSerdeTest {

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.processinstance.legacy
            .ProcessInstanceModificationActivateInstruction();
    final var current = new ProcessInstanceModificationActivateInstruction();
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
        new io.camunda.zeebe.protocol.impl.record.value.processinstance.legacy
            .ProcessInstanceModificationActivateInstruction();
    final var current = new ProcessInstanceModificationActivateInstruction();
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
        new io.camunda.zeebe.protocol.impl.record.value.processinstance.legacy
            .ProcessInstanceModificationActivateInstruction();
    final var current = new ProcessInstanceModificationActivateInstruction();

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
        new io.camunda.zeebe.protocol.impl.record.value.processinstance.legacy
            .ProcessInstanceModificationActivateInstruction();
    final var current = new ProcessInstanceModificationActivateInstruction();

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
        new io.camunda.zeebe.protocol.impl.record.value.processinstance.legacy
            .ProcessInstanceModificationActivateInstruction();
    v1.setElementId("task")
        .setAncestorScopeKey(11L)
        .addVariableInstruction(legacyVariableInstruction("scope"))
        .addAncestorScopeKeys(Set.of(3L));
    final var bytes = new ExpandableArrayBuffer();
    final int length = LegacyObjectValues.write(v1, bytes);
    final var current = new ProcessInstanceModificationActivateInstruction();
    current
        .setElementId("dirty")
        .setAncestorScopeKey(5L)
        .addVariableInstruction(variableInstruction("dirty"))
        .addAncestorScopeKeys(Set.of(9L));

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getElementId()).isEqualTo("task");
    assertThat(current.getAncestorScopeKey()).isEqualTo(11L);
    assertThat(current.getAncestorScopeKeys()).containsExactly(3L);
    assertThat(current.getVariableInstructions()).hasSize(1);
    assertThat(current.getVariableInstructions().get(0).getElementId()).isEqualTo("scope");
    assertThat(current.getVariableInstructions().get(0).getVariables())
        .isEqualTo(v1.getVariableInstructions().get(0).getVariables());
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new ProcessInstanceModificationActivateInstruction();
    current
        .setElementId("task")
        .setAncestorScopeKey(11L)
        .addVariableInstruction(variableInstruction("scope"))
        .addAncestorScopeKeys(Set.of(3L));
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.processinstance.legacy
            .ProcessInstanceModificationActivateInstruction();
    LegacyObjectValues.read(v1, bytes, length);

    // then
    assertThat(v1.getElementId()).isEqualTo("task");
    assertThat(v1.getAncestorScopeKey()).isEqualTo(11L);
    assertThat(v1.getAncestorScopeKeys()).containsExactly(3L);
    assertThat(v1.getVariableInstructions()).hasSize(1);
    assertThat(v1.getVariableInstructions().get(0).getElementId()).isEqualTo("scope");
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new ProcessInstanceModificationActivateInstruction();
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
    final var instance = new ProcessInstanceModificationActivateInstruction();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setAncestorScopeKey(99L);
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
        new io.camunda.zeebe.protocol.impl.record.value.processinstance.legacy
            .ProcessInstanceModificationActivateInstruction();
    final var current = new ProcessInstanceModificationActivateInstruction();
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
      final io.camunda.zeebe.protocol.impl.record.value.processinstance.legacy
              .ProcessInstanceModificationActivateInstruction
          x) {
    x.setElementId("a-long-id-above-thirty-one-bytes-0123456789")
        .setAncestorScopeKey(70000L)
        .addVariableInstruction(legacyVariableInstruction("scope"))
        .addVariableInstruction(legacyVariableInstruction("other"))
        .addAncestorScopeKeys(Set.of(1L, 200L, 70000L, 5_000_000_000L));
  }

  private static void setAllProperties(final ProcessInstanceModificationActivateInstruction x) {
    x.setElementId("a-long-id-above-thirty-one-bytes-0123456789")
        .setAncestorScopeKey(70000L)
        .addVariableInstruction(variableInstruction("scope"))
        .addVariableInstruction(variableInstruction("other"))
        .addAncestorScopeKeys(Set.of(1L, 200L, 70000L, 5_000_000_000L));
  }

  private static void setRequiredProperties(
      final io.camunda.zeebe.protocol.impl.record.value.processinstance.legacy
              .ProcessInstanceModificationActivateInstruction
          x) {
    x.setElementId("task");
  }

  private static void setRequiredProperties(
      final ProcessInstanceModificationActivateInstruction x) {
    x.setElementId("task");
  }

  private static ProcessInstanceModificationVariableInstruction variableInstruction(
      final String elementId) {
    return new ProcessInstanceModificationVariableInstruction()
        .setElementId(elementId)
        .setVariables(
            new UnsafeBuffer(
                MsgPackConverter.convertToMsgPack(
                    "{\"a\":70000,\"b\":\"a-string-above-thirty-one-bytes-long\"}")));
  }

  private static io.camunda.zeebe.protocol.impl.record.value.processinstance.legacy
          .ProcessInstanceModificationVariableInstruction
      legacyVariableInstruction(final String elementId) {
    return new io.camunda.zeebe.protocol.impl.record.value.processinstance.legacy
            .ProcessInstanceModificationVariableInstruction()
        .setElementId(elementId)
        .setVariables(
            new UnsafeBuffer(
                MsgPackConverter.convertToMsgPack(
                    "{\"a\":70000,\"b\":\"a-string-above-thirty-one-bytes-long\"}")));
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
