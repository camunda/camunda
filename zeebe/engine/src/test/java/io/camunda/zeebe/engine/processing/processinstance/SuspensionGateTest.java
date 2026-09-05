/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.immutable.SuspensionState.State;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BufferedCommandIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.BufferedCommandRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

/**
 * Verifies the primary suspension gate in {@code Engine.process}: commands targeting a suspended
 * process instance are buffered or passed through based on the classification of their {@code
 * TypedRecordProcessor}.
 */
public final class SuspensionGateTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldBufferInternalCommandWhileSuspended() {
    // given - an internal COMPLETE_ELEMENT command (BUFFER category via BpmnStreamProcessor) is
    // targeted at an element of the process instance while it is suspended
    final String processId = Strings.newRandomValidBpmnId();
    final String jobType = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(jobType))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    final Record<ProcessInstanceRecordValue> taskActivated =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .getFirst();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when
    ENGINE.writeRecords(
        RecordToWrite.command()
            .processInstance(ProcessInstanceIntent.COMPLETE_ELEMENT, taskActivated.getValue())
            .key(taskActivated.getKey()));

    // then - the command is buffered instead of being handed to BpmnStreamProcessor
    final Record<RecordValue> buffered =
        RecordingExporter.records()
            .withValueType(ValueType.BUFFERED_COMMAND)
            .withIntent(BufferedCommandIntent.BUFFERED)
            .filter(
                r ->
                    ((BufferedCommandRecordValue) r.getValue()).getProcessInstanceKey()
                        == processInstanceKey)
            .getFirst();
    final var bufferedValue = (BufferedCommandRecordValue) buffered.getValue();
    assertThat(bufferedValue.getProcessInstanceKey()).isEqualTo(processInstanceKey);
    assertThat(bufferedValue.getCommandKey()).isEqualTo(taskActivated.getKey());
    assertThat(bufferedValue.getIntent()).isEqualTo(ProcessInstanceIntent.COMPLETE_ELEMENT);

    // and no forward progress was made: the element is still activated, i.e. it was never
    // completed by BpmnStreamProcessor
    final var elementInstance =
        ((MutableProcessingState) ENGINE.getProcessingState())
            .getElementInstanceState()
            .getInstance(taskActivated.getKey());
    assertThat(elementInstance).isNotNull();
    assertThat(elementInstance.getState()).isEqualTo(ProcessInstanceIntent.ELEMENT_ACTIVATED);
  }

  @Test
  public void shouldPassThroughBufferCategoryCommandWhileResuming() {
    // given - seed the RESUMING marker directly to isolate the gate from the drain that puts the
    // instance into that state in production
    final String processId = Strings.newRandomValidBpmnId();
    final String jobType = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(jobType))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    final Record<ProcessInstanceRecordValue> taskActivated =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .getFirst();
    ((MutableProcessingState) ENGINE.getProcessingState())
        .getSuspensionState()
        .setSuspensionState(processInstanceKey, State.RESUMING);

    // when
    ENGINE.writeRecords(
        RecordToWrite.command()
            .processInstance(ProcessInstanceIntent.COMPLETE_ELEMENT, taskActivated.getValue())
            .key(taskActivated.getKey()));

    // then - the BUFFER-category command passes through and is actually processed
    final Record<ProcessInstanceRecordValue> completed =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .getFirst();
    Assertions.assertThat(completed.getValue()).hasProcessInstanceKey(processInstanceKey);
  }
}
