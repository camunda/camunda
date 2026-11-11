/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.instance;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.impl.pojo.instance.ElementInstanceData;
import io.camunda.zeebe.protocol.impl.pojo.instance.ElementInstanceDataDeserializer;
import io.camunda.zeebe.protocol.impl.pojo.instance.ElementInstanceDataSerializer;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The stored format must not change: state written by one implementation has to be readable by the
 * other, otherwise migration and rollback break.
 */
final class ElementInstancePojoCompatibilityTest {

  private final ElementInstanceDataSerializer serializer = new ElementInstanceDataSerializer();
  private final ElementInstanceDataDeserializer deserializer =
      new ElementInstanceDataDeserializer();

  @Test
  void shouldWriteTheSameBytesAsElementInstance() {
    // given
    final var record = processInstanceRecord();
    final var legacy = new ElementInstance(10L, ProcessInstanceIntent.ELEMENT_ACTIVATED, record);
    legacy.setJobKey(5L);
    legacy.setUserTaskKey(6L);
    legacy.setProcessDepth(3);
    legacy.incrementNumberOfElementInstances();
    legacy.incrementTaskListenerIndex(
        io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskListenerEventType.completing);
    legacy.setInterruptingElementId(BufferUtil.wrapString("boundary"));
    legacy.addActiveSequenceFlowId(BufferUtil.wrapString("flow-a"));
    legacy.addActiveSequenceFlowId(BufferUtil.wrapString("flow-b"));
    legacy.setInterruptedByRuntimeInstruction();

    final var pojo = new ElementInstanceData();
    pojo.getElementRecord().setKey(10L);
    pojo.getElementRecord().setState(ProcessInstanceIntent.ELEMENT_ACTIVATED);
    pojo.getElementRecord().setProcessInstanceRecord(encode(record));
    pojo.setJobKey(5L);
    pojo.setUserTaskKey(6L);
    pojo.setProcessDepth(3);
    pojo.setChildActivatedCount(1);
    pojo.getTaskListenerIndicesRecord().setCompletingTaskListenerIndex(1);
    pojo.setInterruptingElementId(BufferUtil.wrapString("boundary"));
    pojo.getActiveSequenceFlowIds().add(BufferUtil.wrapString("flow-a"));
    pojo.getActiveSequenceFlowIds().add(BufferUtil.wrapString("flow-b"));
    pojo.setInterruptedByRuntimeInstruction(true);

    // when
    final var legacyBytes = new ExpandableArrayBuffer();
    final int legacyLength = legacy.write(legacyBytes, 0);
    final var pojoBytes = new ExpandableArrayBuffer();
    final int pojoLength = serializer.serialize(pojo, pojoBytes, 0);

    // then
    assertThat(pojoLength).isEqualTo(legacyLength);
    assertThat(bytes(pojoBytes, pojoLength)).isEqualTo(bytes(legacyBytes, legacyLength));
  }

  @Test
  void shouldReadBytesWrittenByElementInstance() {
    // given
    final var record = processInstanceRecord();
    final var legacy = new ElementInstance(11L, ProcessInstanceIntent.ELEMENT_COMPLETING, record);
    legacy.setCalledChildInstanceKey(77L);
    legacy.setMultiInstanceLoopCounter(4);
    final var buffer = new ExpandableArrayBuffer();
    final int length = legacy.write(buffer, 0);

    // when
    final var pojo = new ElementInstanceData();
    deserializer.deserialize(buffer, 0, length, pojo);

    // then
    assertThat(pojo.getElementRecord().getKey()).isEqualTo(11L);
    assertThat(pojo.getElementRecord().getState())
        .isEqualTo(ProcessInstanceIntent.ELEMENT_COMPLETING);
    assertThat(pojo.getCalledChildInstanceKey()).isEqualTo(77L);
    assertThat(pojo.getMultiInstanceLoopCounter()).isEqualTo(4);
    assertThat(pojo.getParentKey()).isEqualTo(-1L);
    assertThat(pojo.getElementRecord().getProcessInstanceRecord()).isEqualTo(encode(record));
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

  private static DirectBuffer encode(final ProcessInstanceRecord record) {
    final var bytes = new byte[record.getLength()];
    record.write(new UnsafeBuffer(bytes), 0);
    return new UnsafeBuffer(bytes);
  }

  private static byte[] bytes(final ExpandableArrayBuffer buffer, final int length) {
    final var bytes = new byte[length];
    buffer.getBytes(0, bytes);
    return bytes;
  }
}
