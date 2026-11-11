/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.instance;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskListenerEventType;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.HexFormat;
import org.agrona.ExpandableArrayBuffer;
import org.junit.jupiter.api.Test;

/**
 * The stored format must not change, otherwise migration and rollback break. {@link
 * ElementInstanceV1} is a frozen copy of the hand-written implementation the generated serializers
 * replaced.
 */
final class ElementInstanceSerdeTest {

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 =
        new ElementInstanceV1(
            10L, ProcessInstanceIntent.ELEMENT_ACTIVATED, processInstanceRecord());
    final var current =
        new ElementInstance(10L, ProcessInstanceIntent.ELEMENT_ACTIVATED, processInstanceRecord());
    v1.setJobKey(5L);
    current.setJobKey(5L);
    v1.setUserTaskKey(6L);
    current.setUserTaskKey(6L);
    v1.setProcessDepth(3);
    current.setProcessDepth(3);
    v1.incrementNumberOfElementInstances();
    current.incrementNumberOfElementInstances();
    v1.incrementTaskListenerIndex(ZeebeTaskListenerEventType.completing);
    current.incrementTaskListenerIndex(ZeebeTaskListenerEventType.completing);
    v1.setInterruptingElementId(BufferUtil.wrapString("boundary"));
    current.setInterruptingElementId(BufferUtil.wrapString("boundary"));
    v1.addActiveSequenceFlowId(BufferUtil.wrapString("flow-a"));
    current.addActiveSequenceFlowId(BufferUtil.wrapString("flow-a"));
    v1.addActiveSequenceFlowId(BufferUtil.wrapString("flow-b"));
    current.addActiveSequenceFlowId(BufferUtil.wrapString("flow-b"));
    v1.setInterruptedByRuntimeInstruction();
    current.setInterruptedByRuntimeInstruction();

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
    // given
    final var v1 =
        new ElementInstanceV1(
            11L, ProcessInstanceIntent.ELEMENT_COMPLETING, processInstanceRecord());
    final var current =
        new ElementInstance(11L, ProcessInstanceIntent.ELEMENT_COMPLETING, processInstanceRecord());

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
        new ElementInstanceV1(
            11L, ProcessInstanceIntent.ELEMENT_COMPLETING, processInstanceRecord());
    v1.setCalledChildInstanceKey(77L);
    v1.setMultiInstanceLoopCounter(4);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var instance = new ElementInstance();
    instance.setJobKey(99L);
    instance.addActiveSequenceFlowId(BufferUtil.wrapString("stale"));

    // when
    instance.wrap(bytes, 0, length);

    // then
    assertThat(instance.getKey()).isEqualTo(11L);
    assertThat(instance.getState()).isEqualTo(ProcessInstanceIntent.ELEMENT_COMPLETING);
    assertThat(instance.getCalledChildInstanceKey()).isEqualTo(77L);
    assertThat(instance.getMultiInstanceLoopCounter()).isEqualTo(4);
    assertThat(instance.getJobKey()).isZero();
    assertThat(instance.getParentKey()).isEqualTo(-1L);
    assertThat(instance.getActiveSequenceFlowIds()).isEmpty();
    assertThat(instance.getValue().getElementId()).isEqualTo("task");
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current =
        new ElementInstance(14L, ProcessInstanceIntent.ELEMENT_ACTIVATED, processInstanceRecord());
    current.setAgentInstanceKey(3L);
    current.addActiveSequenceFlowId(BufferUtil.wrapString("flow"));
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new ElementInstanceV1();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getKey()).isEqualTo(14L);
    assertThat(v1.getAgentInstanceKey()).isEqualTo(3L);
    assertThat(v1.getActiveSequenceFlowIds()).containsExactly(BufferUtil.wrapString("flow"));
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a stored value that carries a property this version does not know
    final var source =
        new ElementInstance(12L, ProcessInstanceIntent.ELEMENT_ACTIVATED, processInstanceRecord());
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(BufferUtil.wrapString("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // 19 declared properties use a map16 header (0xde, 2 byte count): announce one more entry
    stored.putByte(2, (byte) (stored.getByte(2) + 1));

    // when read, modified and written again
    final var instance = new ElementInstance();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setJobKey(8L);
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then
    final var reread = new ElementInstance();
    reread.wrap(rewritten, 0, rewrittenLength);
    assertThat(reread.getJobKey()).isEqualTo(8L);
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  @Test
  void shouldReadViewsOnTheBufferLikeV1() {
    // given
    final var instance =
        new ElementInstance(13L, ProcessInstanceIntent.ELEMENT_ACTIVATED, processInstanceRecord());
    instance.setInterruptingElementId(BufferUtil.wrapString("boundary"));
    instance.addActiveSequenceFlowId(BufferUtil.wrapString("flow"));
    final var buffer = new ExpandableArrayBuffer();
    final int length = instance.write(buffer, 0);
    final var read = new ElementInstance();
    read.wrap(buffer, 0, length);
    final var v1 = new ElementInstanceV1();
    v1.wrap(buffer, 0, length);

    // when the buffer is reused
    buffer.setMemory(0, buffer.capacity(), (byte) 0);

    // then
    assertThat(read.getInterruptingElementId()).isEqualTo(v1.getInterruptingElementId());
    assertThat(read.getActiveSequenceFlowIds()).isEqualTo(v1.getActiveSequenceFlowIds());
  }

  private static ProcessInstanceRecord processInstanceRecord() {
    return new ProcessInstanceRecord()
        .setBpmnProcessId("process")
        .setElementId("task")
        .setProcessInstanceKey(1L)
        .setProcessDefinitionKey(2L)
        .setVersion(1)
        .setFlowScopeKey(1L);
  }

  private static String hex(final ExpandableArrayBuffer buffer, final int length) {
    final var bytes = new byte[length];
    buffer.getBytes(0, bytes);
    return HexFormat.of().formatHex(bytes);
  }
}
