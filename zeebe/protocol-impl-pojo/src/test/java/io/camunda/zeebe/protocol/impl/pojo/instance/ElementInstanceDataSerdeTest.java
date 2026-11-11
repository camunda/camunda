/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.pojo.instance;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import java.nio.charset.StandardCharsets;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

final class ElementInstanceDataSerdeTest {

  private final ElementInstanceDataSerializer serializer = new ElementInstanceDataSerializer();
  private final ElementInstanceDataDeserializer deserializer =
      new ElementInstanceDataDeserializer();
  private final ExpandableArrayBuffer scratch = new ExpandableArrayBuffer();

  @Test
  void shouldRoundTripAllProperties() {
    // given
    final var value = new ElementInstanceData();
    value.setParentKey(42L);
    value.setChildCount(3);
    value.setProcessDepth(7);
    value.setInterruptedByRuntimeInstruction(true);
    value.setInterruptingElementId(buffer("boundary"));
    value.getActiveSequenceFlowIds().add(buffer("flow-1"));
    value.getActiveSequenceFlowIds().add(buffer("flow-2"));
    value.getTaskListenerIndicesRecord().setUpdatingTaskListenerIndex(5);
    value.getElementRecord().setKey(9L);
    value.getElementRecord().setState(ProcessInstanceIntent.ELEMENT_ACTIVATED);
    value.getElementRecord().setProcessInstanceRecord(rawMap());

    // when
    final int length = serializer.serialize(value, scratch, 0);
    final var decoded = new ElementInstanceData();
    deserializer.deserialize(scratch, 0, length, decoded);

    // then
    assertThat(length).isEqualTo(serializer.getLength(value));
    assertThat(decoded.getParentKey()).isEqualTo(42L);
    assertThat(decoded.getChildCount()).isEqualTo(3);
    assertThat(decoded.getProcessDepth()).isEqualTo(7);
    assertThat(decoded.isInterruptedByRuntimeInstruction()).isTrue();
    assertThat(decoded.getInterruptingElementId()).isEqualTo(buffer("boundary"));
    assertThat(decoded.getActiveSequenceFlowIds())
        .containsExactly(buffer("flow-1"), buffer("flow-2"));
    assertThat(decoded.getTaskListenerIndicesRecord().getUpdatingTaskListenerIndex()).isEqualTo(5);
    assertThat(decoded.getElementRecord().getKey()).isEqualTo(9L);
    assertThat(decoded.getElementRecord().getState())
        .isEqualTo(ProcessInstanceIntent.ELEMENT_ACTIVATED);
    assertThat(decoded.getElementRecord().getProcessInstanceRecord()).isEqualTo(rawMap());
  }

  @Test
  void shouldNotAliasTheSourceBufferAfterDecoding() {
    // given
    final var value = withState();
    value.setInterruptingElementId(buffer("boundary"));
    final int length = serializer.serialize(value, scratch, 0);
    final var decoded = new ElementInstanceData();
    deserializer.deserialize(scratch, 0, length, decoded);

    // when the scratch buffer is reused
    scratch.setMemory(0, length, (byte) 0);

    // then
    assertThat(decoded.getInterruptingElementId()).isEqualTo(buffer("boundary"));
  }

  @Test
  void shouldUseDefaultsForMissingPropertiesAndResetTheTarget() {
    // given a map with a single property and a dirty target
    final var writer = new MsgPackWriter().wrap(scratch, 0);
    int length = writer.writeMapHeader(1);
    length += writer.writeString(buffer("childCount"));
    length += writer.writeInteger(4);
    final var target = withState();
    target.setParentKey(100L);
    target.getActiveSequenceFlowIds().add(buffer("stale"));

    // when
    deserializer.deserialize(scratch, 0, length, target);

    // then
    assertThat(target.getChildCount()).isEqualTo(4);
    assertThat(target.getParentKey()).isEqualTo(-1L);
    assertThat(target.getProcessDepth()).isEqualTo(1);
    assertThat(target.getActiveSequenceFlowIds()).isEmpty();
  }

  @Test
  void shouldPreserveUnknownProperties() {
    // given a value read from a map written by a newer version, with an unknown property
    final var source = withState();
    source.setJobKey(8L);
    final int sourceLength = serializer.serialize(source, scratch, 0);
    final var unknown = new ExpandableArrayBuffer();
    final var unknownWriter = new MsgPackWriter().wrap(unknown, 0);
    int unknownLength = unknownWriter.writeString(buffer("futureProperty"));
    unknownLength += unknownWriter.writeMapHeader(1);
    unknownLength += unknownWriter.writeString(buffer("nested"));
    unknownLength += unknownWriter.writeInteger(1234);
    // 19 declared entries need a map16 header (0xde, 2 bytes count); announce one more entry
    final int length = sourceLength + unknownLength;
    scratch.putBytes(sourceLength, unknown, 0, unknownLength);
    scratch.putByte(2, (byte) (scratch.getByte(2) + 1));
    final var decoded = new ElementInstanceData();
    deserializer.deserialize(scratch, 0, length, decoded);

    // when
    decoded.setChildCount(3);
    final var out = new ExpandableArrayBuffer();
    final int written = serializer.serialize(decoded, out, 0);
    final var reread = new ElementInstanceData();
    deserializer.deserialize(out, 0, written, reread);

    // then
    assertThat(decoded.getJobKey()).isEqualTo(8L);
    assertThat(decoded.getUnknownProperties().count()).isEqualTo(1);
    assertThat(written).isEqualTo(serializer.getLength(decoded));
    assertThat(reread.getChildCount()).isEqualTo(3);
    assertThat(reread.getUnknownProperties().count()).isEqualTo(1);
    assertThat(reread.getUnknownProperties().length())
        .isEqualTo(decoded.getUnknownProperties().length());
  }

  private static ElementInstanceData withState() {
    final var value = new ElementInstanceData();
    value.getElementRecord().setState(ProcessInstanceIntent.ELEMENT_ACTIVATED);
    value.getElementRecord().setProcessInstanceRecord(rawMap());
    return value;
  }

  private static DirectBuffer rawMap() {
    final var b = new ExpandableArrayBuffer();
    final var w = new MsgPackWriter().wrap(b, 0);
    int n = w.writeMapHeader(1);
    n += w.writeString(buffer("bpmnProcessId"));
    n += w.writeString(buffer("process"));
    return new UnsafeBuffer(b, 0, n);
  }

  private static DirectBuffer buffer(final String s) {
    return new UnsafeBuffer(s.getBytes(StandardCharsets.UTF_8));
  }
}
