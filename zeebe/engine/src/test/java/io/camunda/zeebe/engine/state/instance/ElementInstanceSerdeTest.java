/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.instance;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.RecordV1s;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskListenerEventType;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecordV1;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link ElementInstanceV1} is a frozen copy of the property based implementation the
 * generated codec replaced.
 */
final class ElementInstanceSerdeTest {

  // longer than 31 bytes, so the msgpack string header changes
  private static final String LONG_ID = "an-element-id-that-is-longer-than-thirty-one-bytes";
  private static final ProcessInstanceIntent ACTIVATED = ProcessInstanceIntent.ELEMENT_ACTIVATED;

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = populatedV1();
    final var current = populated();

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
    // given only the state, which has no default, and everything else stays default
    final var v1 = new ElementInstanceV1();
    v1.setState(ACTIVATED);
    final var current = new ElementInstance();
    current.setState(ACTIVATED);

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
    final var bytes = new ExpandableArrayBuffer();
    final int length = populatedV1().write(bytes, 0);
    final var current = new ElementInstance();
    current.setJobKey(5L);
    current.setProcessDepth(9);
    current.setInterruptedByRuntimeInstruction();
    current.incrementActiveSequenceFlows();
    current.addActiveSequenceFlowId(utf8("dirty"));
    current.incrementTaskListenerIndex(ZeebeTaskListenerEventType.completing);

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getKey()).isEqualTo(2L);
    assertThat(current.getParentKey()).isEqualTo(1L);
    assertThat(current.getNumberOfActiveElementInstances()).isEqualTo(1);
    assertThat(current.getNumberOfElementInstances()).isEqualTo(1);
    assertThat(current.getNumberOfCompletedElementInstances()).isEqualTo(1);
    assertThat(current.getNumberOfTerminatedElementInstances()).isEqualTo(1);
    assertThat(current.getJobKey()).isEqualTo(77L);
    assertThat(current.getMultiInstanceLoopCounter()).isEqualTo(300);
    assertThat(current.getInterruptingElementId()).isEqualTo(utf8(LONG_ID));
    assertThat(current.getCalledChildInstanceKey()).isEqualTo(88L);
    assertThat(current.getActiveSequenceFlows()).isEqualTo(2);
    assertThat(current.getActiveSequenceFlowIds()).containsExactly(utf8(LONG_ID), utf8("flow-b"));
    assertThat(current.getUserTaskKey()).isEqualTo(66L);
    assertThat(current.getExecutionListenerIndex()).isEqualTo(1);
    assertThat(current.getTaskListenerIndex(ZeebeTaskListenerEventType.creating)).isEqualTo(1);
    assertThat(current.getTaskListenerIndex(ZeebeTaskListenerEventType.completing)).isZero();
    assertThat(current.getProcessDepth()).isEqualTo(4);
    assertThat(current.isInterruptedByRuntimeInstruction()).isTrue();
    assertThat(current.isCompletionConditionFulfilled()).isTrue();
    assertThat(current.getAgentInstanceKey()).isEqualTo(55L);
    assertThat(current.getState()).isEqualTo(ProcessInstanceIntent.ELEMENT_ACTIVATED);
  }

  @Test
  void shouldReadTheDefaultsWhenBytesHoldTheDefaults() {
    // given
    final var v1 = new ElementInstanceV1();
    v1.setState(ACTIVATED);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = populated();

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getParentKey()).isEqualTo(-1L);
    assertThat(current.getJobKey()).isZero();
    assertThat(current.getProcessDepth()).isEqualTo(1);
    assertThat(current.getActiveSequenceFlowIds()).isEmpty();
    assertThat(current.isInterrupted()).isFalse();
    assertThat(current.getTaskListenerIndex(ZeebeTaskListenerEventType.creating)).isZero();
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = populated();
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new ElementInstanceV1();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getKey()).isEqualTo(2L);
    assertThat(v1.getParentKey()).isEqualTo(1L);
    assertThat(v1.getJobKey()).isEqualTo(77L);
    assertThat(v1.getInterruptingElementId()).isEqualTo(utf8(LONG_ID));
    assertThat(v1.getActiveSequenceFlowIds()).containsExactly(utf8(LONG_ID), utf8("flow-b"));
    assertThat(v1.getAgentInstanceKey()).isEqualTo(55L);
  }

  @Test
  void shouldNotReferenceTheBufferItWasReadFrom() {
    // given
    final var instance = populated();
    final var buffer = new ExpandableArrayBuffer();
    final int length = instance.write(buffer, 0);
    final var read = new ElementInstance();
    read.wrap(buffer, 0, length);

    // when the buffer is reused
    buffer.setMemory(0, buffer.capacity(), (byte) 0);

    // then
    assertThat(read.getInterruptingElementId()).isEqualTo(utf8(LONG_ID));
    assertThat(read.getActiveSequenceFlowIds()).containsExactly(utf8(LONG_ID), utf8("flow-b"));
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = populated();
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // 19 entries: the map header is 0xde followed by a 2 byte count, so announce one more entry.
    stored.putByte(2, (byte) (stored.getByte(2) + 1));

    // when read, modified and written again
    final var instance = new ElementInstance();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setJobKey(78L);
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  private static ElementInstance populated() {
    final var record = processInstanceRecord();
    final var grandparent = new ElementInstance(1L, ACTIVATED, record);
    final var instance = new ElementInstance(2L, grandparent, ACTIVATED, record);
    // a child increments the child count of this instance
    new ElementInstance(3L, instance, ACTIVATED, record);
    instance.incrementNumberOfElementInstances();
    instance.incrementNumberOfCompletedElementInstances();
    instance.incrementNumberOfTerminatedElementInstances();
    instance.setJobKey(77L);
    instance.setMultiInstanceLoopCounter(300);
    instance.setInterruptingElementId(utf8(LONG_ID));
    instance.setCalledChildInstanceKey(88L);
    instance.incrementActiveSequenceFlows();
    instance.incrementActiveSequenceFlows();
    instance.addActiveSequenceFlowId(utf8(LONG_ID));
    instance.addActiveSequenceFlowId(utf8("flow-b"));
    instance.setUserTaskKey(66L);
    instance.incrementExecutionListenerIndex();
    instance.incrementTaskListenerIndex(ZeebeTaskListenerEventType.creating);
    instance.setProcessDepth(4);
    instance.setInterruptedByRuntimeInstruction();
    instance.setCompletionConditionFulfilled(true);
    instance.setAgentInstanceKey(55L);
    return instance;
  }

  private static ElementInstanceV1 populatedV1() {
    final var record = RecordV1s.convert(processInstanceRecord(), new ProcessInstanceRecordV1());
    final var grandparent = new ElementInstanceV1(1L, ACTIVATED, record);
    final var instance = new ElementInstanceV1(2L, grandparent, ACTIVATED, record);
    // a child increments the child count of this instance
    new ElementInstanceV1(3L, instance, ACTIVATED, record);
    instance.incrementNumberOfElementInstances();
    instance.incrementNumberOfCompletedElementInstances();
    instance.incrementNumberOfTerminatedElementInstances();
    instance.setJobKey(77L);
    instance.setMultiInstanceLoopCounter(300);
    instance.setInterruptingElementId(utf8(LONG_ID));
    instance.setCalledChildInstanceKey(88L);
    instance.incrementActiveSequenceFlows();
    instance.incrementActiveSequenceFlows();
    instance.addActiveSequenceFlowId(utf8(LONG_ID));
    instance.addActiveSequenceFlowId(utf8("flow-b"));
    instance.setUserTaskKey(66L);
    instance.incrementExecutionListenerIndex();
    instance.incrementTaskListenerIndex(ZeebeTaskListenerEventType.creating);
    instance.setProcessDepth(4);
    instance.setInterruptedByRuntimeInstruction();
    instance.setCompletionConditionFulfilled(true);
    instance.setAgentInstanceKey(55L);
    return instance;
  }

  private static ProcessInstanceRecord processInstanceRecord() {
    return new ProcessInstanceRecord().setBpmnProcessId(LONG_ID).setProcessDefinitionKey(3L);
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
