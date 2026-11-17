/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.processinstance;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
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
final class ProcessInstanceModificationMoveInstructionSerdeTest {

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.processinstance.legacy
            .ProcessInstanceModificationMoveInstruction();
    final var current = new ProcessInstanceModificationMoveInstruction();
    setAll(v1);
    setAll(current);

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
    // given nothing is required, everything stays default
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.processinstance.legacy
            .ProcessInstanceModificationMoveInstruction();
    final var current = new ProcessInstanceModificationMoveInstruction();

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
        new io.camunda.zeebe.protocol.impl.record.value.processinstance.legacy
            .ProcessInstanceModificationMoveInstruction();
    setAll(v1);
    final var bytes = new ExpandableArrayBuffer();
    final int length = LegacyObjectValues.write(v1, bytes);
    final var current = new ProcessInstanceModificationMoveInstruction();
    setOther(current);

    // when
    current.wrap(bytes, 0, length);

    // then
    final var expected = new ProcessInstanceModificationMoveInstruction();
    setAll(expected);
    assertThat(current).isEqualTo(expected);
    assertThat(hex(current)).isEqualTo(hex(expected));
    assertThat(current.getVariableInstructions()).hasSize(2);
    assertThat(current.getVariableInstructions().get(1).getElementId()).isEqualTo("other");
  }

  @Test
  void shouldRestoreDefaultsWhenReadingDefaultBytes() {
    // given bytes of a value that only has defaults
    final var bytes = new ExpandableArrayBuffer();
    final int length =
        LegacyObjectValues.write(
            new io.camunda.zeebe.protocol.impl.record.value.processinstance.legacy
                .ProcessInstanceModificationMoveInstruction(),
            bytes);
    final var current = new ProcessInstanceModificationMoveInstruction();
    setOther(current);

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current).isEqualTo(new ProcessInstanceModificationMoveInstruction());
    assertThat(hex(current)).isEqualTo(hex(new ProcessInstanceModificationMoveInstruction()));
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new ProcessInstanceModificationMoveInstruction();
    setAll(current);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.processinstance.legacy
            .ProcessInstanceModificationMoveInstruction();
    LegacyObjectValues.read(v1, bytes, length);

    // then
    final var expected =
        new io.camunda.zeebe.protocol.impl.record.value.processinstance.legacy
            .ProcessInstanceModificationMoveInstruction();
    setAll(expected);
    assertThat(hex(v1)).isEqualTo(hex(expected));
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new ProcessInstanceModificationMoveInstruction();
    setAll(source);
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new ProcessInstanceModificationMoveInstruction();
    instance.wrap(stored, 0, storedLength + futureLength);
    modify(instance);
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
            .ProcessInstanceModificationMoveInstruction();
    final var current = new ProcessInstanceModificationMoveInstruction();
    setAll(v1);
    setAll(current);

    // when
    final var mapper = new ObjectMapper();
    final var v1Json = mapper.valueToTree(v1);
    final var currentJson = mapper.valueToTree(current);

    // then the parent record serializes the value with Jackson, so the documents must be equal
    assertThat(currentJson).isEqualTo(v1Json);
  }

  private static String hex(final io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue<?> value) {
    final var buffer = new ExpandableArrayBuffer();
    final int length = value.write(buffer, 0);
    return hex(buffer, length);
  }

  private static String hex(final io.camunda.zeebe.msgpack.value.ObjectValue value) {
    final var buffer = new ExpandableArrayBuffer();
    final int length = LegacyObjectValues.write(value, buffer);
    return hex(buffer, length);
  }

  private static void setAll(
      final io.camunda.zeebe.protocol.impl.record.value.processinstance.legacy
              .ProcessInstanceModificationMoveInstruction
          x) {
    x.setSourceElementId("a-long-id-above-thirty-one-bytes-0123456789")
        .setSourceElementInstanceKey(70000L)
        .setTargetElementId("target")
        .setAncestorScopeKey(5_000_000_000L)
        .setInferAncestorScopeFromSourceHierarchy(true)
        .setUseSourceParentKeyAsAncestorScopeKey(true);
    x.addVariableInstruction(legacyVariable("scope"))
        .addVariableInstruction(legacyVariable("other"));
  }

  private static void setAll(final ProcessInstanceModificationMoveInstruction x) {
    x.setSourceElementId("a-long-id-above-thirty-one-bytes-0123456789")
        .setSourceElementInstanceKey(70000L)
        .setTargetElementId("target")
        .setAncestorScopeKey(5_000_000_000L)
        .setInferAncestorScopeFromSourceHierarchy(true)
        .setUseSourceParentKeyAsAncestorScopeKey(true);
    x.addVariableInstruction(variable("scope")).addVariableInstruction(variable("other"));
  }

  private static void setOther(final ProcessInstanceModificationMoveInstruction x) {
    x.setSourceElementId("dirty")
        .setSourceElementInstanceKey(9L)
        .setTargetElementId("dirty")
        .setAncestorScopeKey(8L)
        .setInferAncestorScopeFromSourceHierarchy(true)
        .setUseSourceParentKeyAsAncestorScopeKey(true)
        .addVariableInstruction(variable("dirty"));
  }

  private static void modify(final ProcessInstanceModificationMoveInstruction x) {
    x.setAncestorScopeKey(99L);
  }

  private static ProcessInstanceModificationVariableInstruction variable(final String elementId) {
    return new ProcessInstanceModificationVariableInstruction()
        .setElementId(elementId)
        .setVariables(new UnsafeBuffer(MsgPackConverter.convertToMsgPack("{\"a\":70000}")));
  }

  private static io.camunda.zeebe.protocol.impl.record.value.processinstance.legacy
          .ProcessInstanceModificationVariableInstruction
      legacyVariable(final String elementId) {
    return new io.camunda.zeebe.protocol.impl.record.value.processinstance.legacy
            .ProcessInstanceModificationVariableInstruction()
        .setElementId(elementId)
        .setVariables(new UnsafeBuffer(MsgPackConverter.convertToMsgPack("{\"a\":70000}")));
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
