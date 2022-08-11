/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Map;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class CreateProcessInstanceTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldActivateProcess() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(Bpmn.createExecutableProcess("process").startEvent().endEvent().done())
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId("process").create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted()
                .withElementType(BpmnElementType.PROCESS))
        .extracting(Record::getIntent)
        .containsSequence(
            ProcessInstanceIntent.ELEMENT_ACTIVATING, ProcessInstanceIntent.ELEMENT_ACTIVATED);

    final Record<ProcessInstanceRecordValue> process =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst();

    Assertions.assertThat(process.getValue())
        .hasElementId("process")
        .hasBpmnElementType(BpmnElementType.PROCESS)
        .hasFlowScopeKey(-1)
        .hasBpmnProcessId("process")
        .hasProcessInstanceKey(processInstanceKey);
  }

  @Test
  public void shouldCreateProcessInstanceWithVariables() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(Bpmn.createExecutableProcess("process").startEvent().endEvent().done())
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId("process")
            .withVariables(Map.of("x", 1, "y", 2))
            .create();

    // then
    assertThat(
            RecordingExporter.variableRecords().withProcessInstanceKey(processInstanceKey).limit(2))
        .extracting(Record::getValue)
        .allMatch(v -> v.getScopeKey() == processInstanceKey)
        .extracting(v -> tuple(v.getName(), v.getValue()))
        .contains(tuple("x", "1"), tuple("y", "2"));
  }

  @Test
  public void shouldActivateNoneStartEvent() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("process").startEvent("start").endEvent().done())
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId("process").create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSequence(
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ACTIVATE_ELEMENT),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.COMPLETE_ELEMENT),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED));

    final Record<ProcessInstanceRecordValue> startEvent =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withElementType(BpmnElementType.START_EVENT)
            .getFirst();

    Assertions.assertThat(startEvent.getValue())
        .hasElementId("start")
        .hasBpmnElementType(BpmnElementType.START_EVENT)
        .hasFlowScopeKey(processInstanceKey)
        .hasBpmnProcessId("process")
        .hasProcessInstanceKey(processInstanceKey);
  }

  @Test
  public void shouldActivateOnlyNoneStartEvent() {
    // given
    final var processBuilder = Bpmn.createExecutableProcess("process");
    processBuilder.startEvent("none-start").endEvent();
    processBuilder.startEvent("timer-start").timerWithCycle("R/PT1H").endEvent();
    processBuilder.startEvent("message-start").message("start").endEvent();

    ENGINE.deployment().withXmlResource(processBuilder.done()).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId("process").create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted()
                .withElementType(BpmnElementType.START_EVENT))
        .extracting(r -> r.getValue().getElementId())
        .containsOnly("none-start");
  }

  @Test
  public void shouldTakeSequenceFlowFromStartEvent() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .sequenceFlowId("flow")
                .endEvent()
                .done())
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId("process").create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSequence(
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.SEQUENCE_FLOW, ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ACTIVATE_ELEMENT));

    final Record<ProcessInstanceRecordValue> sequenceFlow =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SEQUENCE_FLOW)
            .getFirst();

    Assertions.assertThat(sequenceFlow.getValue())
        .hasElementId("flow")
        .hasBpmnElementType(BpmnElementType.SEQUENCE_FLOW)
        .hasFlowScopeKey(processInstanceKey)
        .hasBpmnProcessId("process")
        .hasProcessInstanceKey(processInstanceKey);
  }

  @Test
  public void shouldCreateProcessInstanceForTenant() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(Bpmn.createExecutableProcess("process").startEvent().endEvent().done())
        .withTenantId("foo")
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId("process").withTenantId("foo").create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted()
                .withElementType(BpmnElementType.PROCESS))
        .extracting(Record::getIntent)
        .containsSequence(
            ProcessInstanceIntent.ELEMENT_ACTIVATING, ProcessInstanceIntent.ELEMENT_ACTIVATED);

    final Record<ProcessInstanceRecordValue> process =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst();

    Assertions.assertThat(process.getValue())
        .hasElementId("process")
        .hasBpmnElementType(BpmnElementType.PROCESS)
        .hasFlowScopeKey(-1)
        .hasBpmnProcessId("process")
        .hasProcessInstanceKey(processInstanceKey)
        .hasTenantId("foo");
  }
}
