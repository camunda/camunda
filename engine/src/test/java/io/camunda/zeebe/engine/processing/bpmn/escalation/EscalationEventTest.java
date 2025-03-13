/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.bpmn.escalation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.AbstractBoundaryEventBuilder;
import io.camunda.zeebe.model.bpmn.builder.AbstractStartEventBuilder;
import io.camunda.zeebe.model.bpmn.builder.EventSubProcessBuilder;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.EscalationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class EscalationEventTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String TASK_ELEMENT_ID = "task";
  private static final String PROCESS_ID = "wf";
  private static final String ESCALATION_CODE = "ESCALATION";
  private static final String ESCALATION_CODE_NUMBER = "404";
  private static final String THROW_ELEMENT_ID = "throw";
  private static final String CATCH_ELEMENT_ID = "catch";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldThrowEscalationFromEscalationIntermediateThrowEvent() {
    // given
    final var process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .intermediateThrowEvent("throw", b -> b.escalation(ESCALATION_CODE))
            .manualTask(TASK_ELEMENT_ID)
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(
                BpmnElementType.INTERMEDIATE_THROW_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(
                BpmnElementType.INTERMEDIATE_THROW_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.MANUAL_TASK, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.MANUAL_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));

    assertIsNotEscalated("throw", ESCALATION_CODE);
  }

  @Test
  public void shouldThrowEscalationFromEndEvent() {
    // given
    final var process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .endEvent("end", e -> e.escalation(ESCALATION_CODE))
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));

    assertIsNotEscalated("end", ESCALATION_CODE);
  }

  @Test
  public void shouldCatchEscalationOnBoundaryEventWithoutEscalationCode() {
    // given
    final var process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .subProcess(
                "subprocess",
                s ->
                    s.embeddedSubProcess()
                        .startEvent()
                        .intermediateThrowEvent(
                            THROW_ELEMENT_ID, i -> i.escalation(ESCALATION_CODE)))
            .boundaryEvent(CATCH_ELEMENT_ID, AbstractBoundaryEventBuilder::escalation)
            .manualTask(TASK_ELEMENT_ID)
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(
                BpmnElementType.INTERMEDIATE_THROW_EVENT,
                ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(
                BpmnElementType.INTERMEDIATE_THROW_EVENT, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.BOUNDARY_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.BOUNDARY_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.MANUAL_TASK, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.MANUAL_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));

    assertIsEscalated(processInstanceKey, CATCH_ELEMENT_ID, THROW_ELEMENT_ID, ESCALATION_CODE);
  }

  @Test
  public void shouldCatchEscalationOnBoundaryEventWithNumericEscalationCode() {
    // Regression for https://github.com/camunda/zeebe/issues/12326
    // given
    final var process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .subProcess(
                "subprocess",
                s ->
                    s.embeddedSubProcess()
                        .startEvent()
                        .intermediateThrowEvent(
                            THROW_ELEMENT_ID, i -> i.escalation(ESCALATION_CODE_NUMBER)))
            .boundaryEvent(CATCH_ELEMENT_ID, AbstractBoundaryEventBuilder::escalation)
            .manualTask(TASK_ELEMENT_ID)
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(
                BpmnElementType.INTERMEDIATE_THROW_EVENT,
                ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(
                BpmnElementType.INTERMEDIATE_THROW_EVENT, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.BOUNDARY_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.BOUNDARY_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.MANUAL_TASK, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.MANUAL_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));

    assertIsEscalated(
        processInstanceKey, CATCH_ELEMENT_ID, THROW_ELEMENT_ID, ESCALATION_CODE_NUMBER);
  }

  @Test
  public void shouldCatchEscalationInsideMultiInstanceSubprocess() {
    // given
    final Consumer<EventSubProcessBuilder> eventSubprocess =
        s ->
            s.startEvent("escalation-start-event")
                .escalation(ESCALATION_CODE)
                .interrupting(false)
                .endEvent();

    final var process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .subProcess(
                "subprocess",
                s ->
                    s.multiInstance(m -> m.zeebeInputCollectionExpression("items"))
                        .embeddedSubProcess()
                        .eventSubProcess("escalation-subprocess", eventSubprocess)
                        .startEvent()
                        .intermediateThrowEvent(
                            THROW_ELEMENT_ID, i -> i.escalation(ESCALATION_CODE))
                        .manualTask(TASK_ELEMENT_ID))
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("items", List.of(1, 2))
            .create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType())
        .containsSubsequence(
            BpmnElementType.INTERMEDIATE_THROW_EVENT,
            BpmnElementType.INTERMEDIATE_THROW_EVENT,
            BpmnElementType.START_EVENT,
            BpmnElementType.MANUAL_TASK,
            BpmnElementType.START_EVENT,
            BpmnElementType.MANUAL_TASK,
            BpmnElementType.EVENT_SUB_PROCESS,
            BpmnElementType.SUB_PROCESS,
            BpmnElementType.EVENT_SUB_PROCESS,
            BpmnElementType.SUB_PROCESS,
            BpmnElementType.MULTI_INSTANCE_BODY,
            BpmnElementType.PROCESS);

    assertIsEscalated(
        processInstanceKey, "escalation-start-event", THROW_ELEMENT_ID, ESCALATION_CODE);
  }

  @Test
  public void shouldCatchEscalationOutsideMultiInstanceSubprocess() {
    // given
    final var process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .subProcess(
                "subprocess",
                s ->
                    s.multiInstance(m -> m.zeebeInputCollectionExpression("[1]"))
                        .embeddedSubProcess()
                        .startEvent()
                        .endEvent(THROW_ELEMENT_ID, e -> e.escalation(ESCALATION_CODE)))
            .boundaryEvent("escalation-boundary-event", b -> b.escalation(ESCALATION_CODE))
            .manualTask(TASK_ELEMENT_ID)
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.MANUAL_TASK, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.MANUAL_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));

    assertIsEscalated(
        processInstanceKey, "escalation-boundary-event", THROW_ELEMENT_ID, ESCALATION_CODE);
  }

  @Test
  public void shouldThrowEscalationEventHierarchical() {
    // given
    final var processChild =
        Bpmn.createExecutableProcess("wf-child")
            .startEvent()
            .subProcess(
                "subprocess",
                s ->
                    s.embeddedSubProcess()
                        .startEvent()
                        .intermediateThrowEvent(
                            THROW_ELEMENT_ID, b -> b.escalation(ESCALATION_CODE))
                        .endEvent())
            .endEvent()
            .done();

    final var processParent =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .callActivity("call", c -> c.zeebeProcessId("wf-child"))
            .boundaryEvent(CATCH_ELEMENT_ID, b -> b.escalation(ESCALATION_CODE))
            .endEvent()
            .done();

    ENGINE
        .deployment()
        .withXmlResource("wf-child.bpmn", processChild)
        .withXmlResource("wf-parent.bpmn", processParent)
        .deploy();

    // when
    final var parentProcessInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(parentProcessInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.CALL_ACTIVITY, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.CALL_ACTIVITY, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.BOUNDARY_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.BOUNDARY_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));

    final var childProcessInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withParentProcessInstanceKey(parentProcessInstanceKey)
            .getFirst()
            .getValue()
            .getProcessInstanceKey();
    assertIsEscalated(childProcessInstanceKey, CATCH_ELEMENT_ID, THROW_ELEMENT_ID, ESCALATION_CODE);
  }

  @Test
  public void
      shouldCatchEscalationOnNonInterruptingBoundaryEventWhenThrowEscalationEventHierarchical() {
    // given
    final var processChild =
        Bpmn.createExecutableProcess("wf-child")
            .startEvent()
            .subProcess(
                "subprocess",
                s ->
                    s.embeddedSubProcess()
                        .startEvent()
                        .intermediateThrowEvent(
                            THROW_ELEMENT_ID, b -> b.escalation(ESCALATION_CODE))
                        .manualTask(TASK_ELEMENT_ID)
                        .endEvent())
            .endEvent()
            .done();

    final var processParent =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .callActivity("call", c -> c.zeebeProcessId("wf-child"))
            .boundaryEvent(CATCH_ELEMENT_ID, b -> b.escalation(ESCALATION_CODE))
            .cancelActivity(false)
            .endEvent()
            .done();

    ENGINE
        .deployment()
        .withXmlResource("wf-child.bpmn", processChild)
        .withXmlResource("wf-parent.bpmn", processParent)
        .deploy();

    // when
    final var parentProcessInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(parentProcessInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.BOUNDARY_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.BOUNDARY_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.CALL_ACTIVITY, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.CALL_ACTIVITY, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));

    final var childProcessInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withParentProcessInstanceKey(parentProcessInstanceKey)
            .getFirst()
            .getValue()
            .getProcessInstanceKey();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(childProcessInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType())
        .containsSubsequence(
            BpmnElementType.INTERMEDIATE_THROW_EVENT,
            BpmnElementType.MANUAL_TASK,
            BpmnElementType.END_EVENT);

    assertIsEscalated(childProcessInstanceKey, CATCH_ELEMENT_ID, THROW_ELEMENT_ID, ESCALATION_CODE);
  }

  @Test
  public void shouldCatchEscalationOnEscalationStartEventWhenThrowEscalationEventHierarchical() {
    // given
    final var processChild =
        Bpmn.createExecutableProcess("wf-child")
            .startEvent()
            .intermediateThrowEvent(THROW_ELEMENT_ID, b -> b.escalation(ESCALATION_CODE))
            .endEvent()
            .done();

    final var processParent =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .eventSubProcess(
                "escalation-event-subprocess",
                e ->
                    e.startEvent(CATCH_ELEMENT_ID)
                        .interrupting(false)
                        .escalation(ESCALATION_CODE)
                        .manualTask(TASK_ELEMENT_ID)
                        .endEvent())
            .startEvent()
            .callActivity("call", c -> c.zeebeProcessId("wf-child"))
            .endEvent()
            .done();

    ENGINE
        .deployment()
        .withXmlResource("wf-child.bpmn", processChild)
        .withXmlResource("wf-parent.bpmn", processParent)
        .deploy();

    // when
    final var parentProcessInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(parentProcessInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.CALL_ACTIVITY, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.CALL_ACTIVITY, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.MANUAL_TASK, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.MANUAL_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.EVENT_SUB_PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.EVENT_SUB_PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));

    final var childProcessInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withParentProcessInstanceKey(parentProcessInstanceKey)
            .getFirst()
            .getValue()
            .getProcessInstanceKey();

    assertIsEscalated(childProcessInstanceKey, CATCH_ELEMENT_ID, THROW_ELEMENT_ID, ESCALATION_CODE);
  }

  @Test
  public void shouldCatchEscalationEventsByEscalationCode() {
    // given
    final var process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .subProcess(
                "subprocess",
                s ->
                    s.embeddedSubProcess()
                        .startEvent()
                        .parallelGateway("fork")
                        .intermediateThrowEvent("throw-1", i -> i.escalation("escalation-1"))
                        .moveToLastGateway()
                        .intermediateThrowEvent("throw-2", i -> i.escalation("escalation-2")))
            .boundaryEvent("catch-1", b -> b.escalation("escalation-1"))
            .cancelActivity(false)
            .endEvent()
            .moveToActivity("subprocess")
            .boundaryEvent("catch-2", b -> b.escalation("escalation-2"))
            .cancelActivity(false)
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted()
                .withElementType(BpmnElementType.BOUNDARY_EVENT))
        .extracting(r -> r.getValue().getElementId())
        .contains("catch-1", "catch-2");

    assertIsEscalated(processInstanceKey, "catch-1", "throw-1", "escalation-1");
    assertIsEscalated(processInstanceKey, "catch-2", "throw-2", "escalation-2");
  }

  @Test
  public void shouldCatchEscalationOnNonInterruptingBoundaryEventWithEscalationCode() {
    // given
    final var process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .subProcess(
                "subprocess",
                s ->
                    s.embeddedSubProcess()
                        .startEvent()
                        .intermediateThrowEvent(
                            THROW_ELEMENT_ID, i -> i.escalation(ESCALATION_CODE))
                        .manualTask(TASK_ELEMENT_ID))
            .boundaryEvent(CATCH_ELEMENT_ID, b -> b.escalation(ESCALATION_CODE))
            .cancelActivity(false)
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(
                BpmnElementType.INTERMEDIATE_THROW_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(
                BpmnElementType.INTERMEDIATE_THROW_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.BOUNDARY_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.BOUNDARY_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.BOUNDARY_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.BOUNDARY_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                BpmnElementType.INTERMEDIATE_THROW_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(
                BpmnElementType.INTERMEDIATE_THROW_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.MANUAL_TASK, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.MANUAL_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));

    assertIsEscalated(processInstanceKey, CATCH_ELEMENT_ID, THROW_ELEMENT_ID, ESCALATION_CODE);
  }

  @Test
  public void shouldCatchEscalationOnNonInterruptingBoundaryEventWithoutEscalationCode() {
    // given
    final var process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .subProcess(
                "subprocess",
                s ->
                    s.embeddedSubProcess()
                        .startEvent()
                        .intermediateThrowEvent(
                            THROW_ELEMENT_ID, i -> i.escalation(ESCALATION_CODE)))
            .boundaryEvent(CATCH_ELEMENT_ID, AbstractBoundaryEventBuilder::escalation)
            .cancelActivity(false)
            .manualTask(TASK_ELEMENT_ID)
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.BOUNDARY_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.BOUNDARY_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                BpmnElementType.INTERMEDIATE_THROW_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(
                BpmnElementType.INTERMEDIATE_THROW_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.MANUAL_TASK, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.MANUAL_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));

    assertIsEscalated(processInstanceKey, CATCH_ELEMENT_ID, THROW_ELEMENT_ID, ESCALATION_CODE);
  }

  @Test
  public void shouldCatchEscalationOnNonInterruptingEscalationStartEventWithEscalationCode() {
    // given
    final var process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .eventSubProcess(
                "escalation-event-subprocess",
                e ->
                    e.startEvent(CATCH_ELEMENT_ID)
                        .interrupting(false)
                        .escalation(ESCALATION_CODE)
                        .manualTask(TASK_ELEMENT_ID)
                        .endEvent())
            .startEvent()
            .intermediateThrowEvent(THROW_ELEMENT_ID, i -> i.escalation(ESCALATION_CODE))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.EVENT_SUB_PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.EVENT_SUB_PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(
                BpmnElementType.INTERMEDIATE_THROW_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(
                BpmnElementType.INTERMEDIATE_THROW_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.MANUAL_TASK, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.MANUAL_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.EVENT_SUB_PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.EVENT_SUB_PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));

    assertIsEscalated(processInstanceKey, CATCH_ELEMENT_ID, THROW_ELEMENT_ID, ESCALATION_CODE);
  }

  @Test
  public void shouldCatchEscalationOnNonInterruptingEscalationStartEventWithoutEscalationCode() {
    // given
    final var process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .eventSubProcess(
                "escalation-event-subprocess",
                e ->
                    e.startEvent(CATCH_ELEMENT_ID)
                        .interrupting(false)
                        .escalation()
                        .manualTask(TASK_ELEMENT_ID)
                        .endEvent())
            .startEvent()
            .intermediateThrowEvent(THROW_ELEMENT_ID, i -> i.escalation(ESCALATION_CODE))
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.EVENT_SUB_PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.EVENT_SUB_PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(
                BpmnElementType.INTERMEDIATE_THROW_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(
                BpmnElementType.INTERMEDIATE_THROW_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.MANUAL_TASK, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.MANUAL_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.EVENT_SUB_PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.EVENT_SUB_PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));

    assertIsEscalated(processInstanceKey, CATCH_ELEMENT_ID, THROW_ELEMENT_ID, ESCALATION_CODE);
  }

  @Test
  public void shouldThrowEscalationFromIntermediateThrowEventWithExpression() {
    // given
    final var process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .intermediateThrowEvent("throw", b -> b.escalationExpression("escalationCode"))
            .manualTask(TASK_ELEMENT_ID)
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables(Map.of("escalationCode", ESCALATION_CODE))
            .create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.INTERMEDIATE_THROW_EVENT, ProcessInstanceIntent.ACTIVATE_ELEMENT),
            tuple(
                BpmnElementType.INTERMEDIATE_THROW_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(
                BpmnElementType.INTERMEDIATE_THROW_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.INTERMEDIATE_THROW_EVENT, ProcessInstanceIntent.COMPLETE_ELEMENT),
            tuple(
                BpmnElementType.INTERMEDIATE_THROW_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(
                BpmnElementType.INTERMEDIATE_THROW_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.MANUAL_TASK, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.MANUAL_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));

    assertIsNotEscalated("throw", ESCALATION_CODE);
  }

  @Test
  public void shouldThrowEscalationFromEndEventWithExpression() {
    // given
    final var process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .endEvent("end", e -> e.escalationExpression("escalationCode"))
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables(Map.of("escalationCode", ESCALATION_CODE))
            .create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ACTIVATE_ELEMENT),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.COMPLETE_ELEMENT),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));

    assertIsNotEscalated("end", ESCALATION_CODE);
  }

  @Test
  public void shouldCatchEscalationByEscalationCodeWithExpression() {
    // given
    final var process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .eventSubProcess(
                "escalation-event-subprocess",
                e ->
                    e.startEvent(CATCH_ELEMENT_ID)
                        .interrupting(false)
                        .escalation(ESCALATION_CODE)
                        .manualTask(TASK_ELEMENT_ID)
                        .endEvent())
            .startEvent()
            .endEvent(THROW_ELEMENT_ID, e -> e.escalationExpression("escalationCode"))
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables(Map.of("escalationCode", ESCALATION_CODE))
            .create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ACTIVATE_ELEMENT),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.COMPLETE_ELEMENT),
            tuple(BpmnElementType.EVENT_SUB_PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.EVENT_SUB_PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ACTIVATE_ELEMENT),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.COMPLETE_ELEMENT),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.MANUAL_TASK, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.MANUAL_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.EVENT_SUB_PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.EVENT_SUB_PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));

    assertIsEscalated(processInstanceKey, CATCH_ELEMENT_ID, THROW_ELEMENT_ID, ESCALATION_CODE);
  }

  @Test
  public void shouldCatchEscalationOnBoundaryEventWithSpecificEscalationCode() {
    // given
    final var process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .subProcess(
                "subprocess",
                s ->
                    s.embeddedSubProcess()
                        .startEvent()
                        .endEvent(THROW_ELEMENT_ID, i -> i.escalation(ESCALATION_CODE)))
            .boundaryEvent("catch-all", AbstractBoundaryEventBuilder::escalation)
            .endEvent()
            .moveToActivity("subprocess")
            .boundaryEvent("code-specific", b -> b.escalation(ESCALATION_CODE))
            .manualTask(TASK_ELEMENT_ID)
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.BOUNDARY_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.BOUNDARY_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.MANUAL_TASK, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.MANUAL_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));

    assertIsEscalated(processInstanceKey, "code-specific", THROW_ELEMENT_ID, ESCALATION_CODE);
  }

  @Test
  public void shouldCatchEscalationOnEscalstionStartEventWithSpecificEscalationCode() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .eventSubProcess(
                "sub-1",
                e -> e.startEvent("catch-all", AbstractStartEventBuilder::escalation).endEvent())
            .eventSubProcess(
                "sub-2",
                e ->
                    e.startEvent("code-specific", s -> s.escalation(ESCALATION_CODE))
                        .manualTask(TASK_ELEMENT_ID))
            .startEvent()
            .endEvent(THROW_ELEMENT_ID, e -> e.escalation(ESCALATION_CODE))
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.MANUAL_TASK, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.MANUAL_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.EVENT_SUB_PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.EVENT_SUB_PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));

    assertIsEscalated(processInstanceKey, "code-specific", THROW_ELEMENT_ID, ESCALATION_CODE);
  }

  private void assertIsNotEscalated(final String throwElementId, final String escalationCode) {
    assertThat(
            RecordingExporter.escalationRecords(EscalationIntent.NOT_ESCALATED)
                .withCatchElementId("")
                .withThrowElementId(throwElementId)
                .withEscalationCode(escalationCode)
                .exists())
        .isTrue();
  }

  private void assertIsEscalated(
      final long processInstanceKey,
      final String catchElementId,
      final String throwElementId,
      final String escalationCode) {
    assertThat(
            RecordingExporter.escalationRecords(EscalationIntent.ESCALATED)
                .withProcessInstanceKey(processInstanceKey)
                .withCatchElementId(catchElementId)
                .withThrowElementId(throwElementId)
                .withEscalationCode(escalationCode)
                .exists())
        .isTrue();
  }
}
