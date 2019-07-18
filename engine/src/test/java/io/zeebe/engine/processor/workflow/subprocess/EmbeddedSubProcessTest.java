/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.subprocess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.zeebe.engine.util.EngineRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.builder.SubProcessBuilder;
import io.zeebe.protocol.record.Assertions;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.intent.JobIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceSubscriptionIntent;
import io.zeebe.protocol.record.value.JobRecordValue;
import io.zeebe.protocol.record.value.WorkflowInstanceRecordValue;
import io.zeebe.test.util.MsgPackUtil;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class EmbeddedSubProcessTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  private static final BpmnModelInstance ONE_TASK_SUBPROCESS =
      Bpmn.createExecutableProcess("ONE_TASK_SUBPROCESS")
          .startEvent("start")
          .sequenceFlowId("flow1")
          .subProcess("subProcess")
          .embeddedSubProcess()
          .startEvent("subProcessStart")
          .sequenceFlowId("subProcessFlow1")
          .serviceTask("subProcessTask", b -> b.zeebeTaskType("type"))
          .sequenceFlowId("subProcessFlow2")
          .endEvent("subProcessEnd")
          .subProcessDone()
          .sequenceFlowId("flow2")
          .endEvent("end")
          .done();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @BeforeClass
  public static void init() {
    ENGINE.deployment().withXmlResource(ONE_TASK_SUBPROCESS).deploy();
  }

  @Test
  public void shouldCreateJobForServiceTaskInEmbeddedSubprocess() {
    // given

    // when
    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId("ONE_TASK_SUBPROCESS")
            .withVariable("key", "val")
            .create();

    // then
    final Record<JobRecordValue> jobCreatedEvent =
        RecordingExporter.jobRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withIntent(JobIntent.CREATED)
            .getFirst();

    Assertions.assertThat(jobCreatedEvent.getValue()).hasElementId("subProcessTask");
  }

  @Test
  public void shouldGenerateEventStream() {
    // given

    // when
    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId("ONE_TASK_SUBPROCESS")
            .withVariable("key", "val")
            .create();

    // then
    final List<Record<WorkflowInstanceRecordValue>> workflowInstanceEvents =
        RecordingExporter.workflowInstanceRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .limit(
                r ->
                    r.getIntent() == WorkflowInstanceIntent.ELEMENT_ACTIVATED
                        && "subProcessTask".equals(r.getValue().getElementId()))
            .asList();

    assertThat(workflowInstanceEvents)
        .extracting(Record::getIntent, e -> e.getValue().getElementId())
        .containsExactly(
            tuple(WorkflowInstanceIntent.ELEMENT_ACTIVATING, "ONE_TASK_SUBPROCESS"),
            tuple(WorkflowInstanceIntent.ELEMENT_ACTIVATED, "ONE_TASK_SUBPROCESS"),
            tuple(WorkflowInstanceIntent.ELEMENT_ACTIVATING, "start"),
            tuple(WorkflowInstanceIntent.ELEMENT_ACTIVATED, "start"),
            tuple(WorkflowInstanceIntent.ELEMENT_COMPLETING, "start"),
            tuple(WorkflowInstanceIntent.ELEMENT_COMPLETED, "start"),
            tuple(WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN, "flow1"),
            tuple(WorkflowInstanceIntent.ELEMENT_ACTIVATING, "subProcess"),
            tuple(WorkflowInstanceIntent.ELEMENT_ACTIVATED, "subProcess"),
            tuple(WorkflowInstanceIntent.ELEMENT_ACTIVATING, "subProcessStart"),
            tuple(WorkflowInstanceIntent.ELEMENT_ACTIVATED, "subProcessStart"),
            tuple(WorkflowInstanceIntent.ELEMENT_COMPLETING, "subProcessStart"),
            tuple(WorkflowInstanceIntent.ELEMENT_COMPLETED, "subProcessStart"),
            tuple(WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN, "subProcessFlow1"),
            tuple(WorkflowInstanceIntent.ELEMENT_ACTIVATING, "subProcessTask"),
            tuple(WorkflowInstanceIntent.ELEMENT_ACTIVATED, "subProcessTask"));

    final Record<WorkflowInstanceRecordValue> subProcessReady =
        RecordingExporter.workflowInstanceRecords()
            .withElementId("subProcess")
            .withIntent(WorkflowInstanceIntent.ELEMENT_ACTIVATING)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();
    assertThat(subProcessReady.getValue().getFlowScopeKey()).isEqualTo(workflowInstanceKey);

    final Record<WorkflowInstanceRecordValue> subProcessTaskReady =
        RecordingExporter.workflowInstanceRecords()
            .withElementId("subProcessTask")
            .withIntent(WorkflowInstanceIntent.ELEMENT_ACTIVATING)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();
    assertThat(subProcessTaskReady.getValue().getFlowScopeKey())
        .isEqualTo(subProcessReady.getKey());
  }

  @Test
  public void shouldCompleteEmbeddedSubProcess() {
    // given
    final long workflowInstanceKey =
        ENGINE.workflowInstance().ofBpmnProcessId("ONE_TASK_SUBPROCESS").create();

    // when
    ENGINE.job().ofInstance(workflowInstanceKey).withType("type").complete();

    // then
    final List<Record<WorkflowInstanceRecordValue>> workflowInstanceEvents =
        RecordingExporter.workflowInstanceRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .limitToWorkflowInstanceCompleted()
            .collect(Collectors.toList());

    assertThat(workflowInstanceEvents)
        .extracting(Record::getIntent, e -> e.getValue().getElementId())
        .containsExactly(
            tuple(WorkflowInstanceIntent.ELEMENT_ACTIVATING, "ONE_TASK_SUBPROCESS"),
            tuple(WorkflowInstanceIntent.ELEMENT_ACTIVATED, "ONE_TASK_SUBPROCESS"),
            tuple(WorkflowInstanceIntent.ELEMENT_ACTIVATING, "start"),
            tuple(WorkflowInstanceIntent.ELEMENT_ACTIVATED, "start"),
            tuple(WorkflowInstanceIntent.ELEMENT_COMPLETING, "start"),
            tuple(WorkflowInstanceIntent.ELEMENT_COMPLETED, "start"),
            tuple(WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN, "flow1"),
            tuple(WorkflowInstanceIntent.ELEMENT_ACTIVATING, "subProcess"),
            tuple(WorkflowInstanceIntent.ELEMENT_ACTIVATED, "subProcess"),
            tuple(WorkflowInstanceIntent.ELEMENT_ACTIVATING, "subProcessStart"),
            tuple(WorkflowInstanceIntent.ELEMENT_ACTIVATED, "subProcessStart"),
            tuple(WorkflowInstanceIntent.ELEMENT_COMPLETING, "subProcessStart"),
            tuple(WorkflowInstanceIntent.ELEMENT_COMPLETED, "subProcessStart"),
            tuple(WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN, "subProcessFlow1"),
            tuple(WorkflowInstanceIntent.ELEMENT_ACTIVATING, "subProcessTask"),
            tuple(WorkflowInstanceIntent.ELEMENT_ACTIVATED, "subProcessTask"),
            tuple(WorkflowInstanceIntent.ELEMENT_COMPLETING, "subProcessTask"),
            tuple(WorkflowInstanceIntent.ELEMENT_COMPLETED, "subProcessTask"),
            tuple(WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN, "subProcessFlow2"),
            tuple(WorkflowInstanceIntent.ELEMENT_ACTIVATING, "subProcessEnd"),
            tuple(WorkflowInstanceIntent.ELEMENT_ACTIVATED, "subProcessEnd"),
            tuple(WorkflowInstanceIntent.ELEMENT_COMPLETING, "subProcessEnd"),
            tuple(WorkflowInstanceIntent.ELEMENT_COMPLETED, "subProcessEnd"),
            tuple(WorkflowInstanceIntent.ELEMENT_COMPLETING, "subProcess"),
            tuple(WorkflowInstanceIntent.ELEMENT_COMPLETED, "subProcess"),
            tuple(WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN, "flow2"),
            tuple(WorkflowInstanceIntent.ELEMENT_ACTIVATING, "end"),
            tuple(WorkflowInstanceIntent.ELEMENT_ACTIVATED, "end"),
            tuple(WorkflowInstanceIntent.ELEMENT_COMPLETING, "end"),
            tuple(WorkflowInstanceIntent.ELEMENT_COMPLETED, "end"),
            tuple(WorkflowInstanceIntent.ELEMENT_COMPLETING, "ONE_TASK_SUBPROCESS"),
            tuple(WorkflowInstanceIntent.ELEMENT_COMPLETED, "ONE_TASK_SUBPROCESS"));
  }

  @Test
  public void shouldRunServiceTaskAfterEmbeddedSubProcess() {
    // given
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess("shouldRunServiceTaskAfterEmbeddedSubProcess")
            .startEvent()
            .subProcess()
            .embeddedSubProcess()
            .startEvent()
            .endEvent()
            .subProcessDone()
            .serviceTask("task", b -> b.zeebeTaskType("type"))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(model).deploy();

    // when
    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId("shouldRunServiceTaskAfterEmbeddedSubProcess")
            .create();

    // then
    final Record<JobRecordValue> jobCreatedEvent =
        RecordingExporter.jobRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withIntent(JobIntent.CREATED)
            .getFirst();

    Assertions.assertThat(jobCreatedEvent.getValue()).hasElementId("task");
  }

  @Test
  public void shouldCompleteNestedSubProcess() {
    // given
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess("shouldCompleteNestedSubProcess")
            .startEvent()
            .subProcess("outerSubProcess")
            .embeddedSubProcess()
            .startEvent()
            .subProcess("innerSubProcess")
            .embeddedSubProcess()
            .startEvent()
            .serviceTask("task", b -> b.zeebeTaskType("type"))
            .endEvent()
            .subProcessDone()
            .endEvent()
            .subProcessDone()
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(model).deploy();
    final long workflowInstanceKey =
        ENGINE.workflowInstance().ofBpmnProcessId("shouldCompleteNestedSubProcess").create();

    // when
    ENGINE.job().ofInstance(workflowInstanceKey).withType("type").complete();

    // then
    final List<Record<WorkflowInstanceRecordValue>> workflowInstanceEvents =
        RecordingExporter.workflowInstanceRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .limitToWorkflowInstanceCompleted()
            .asList();

    assertThat(workflowInstanceEvents)
        .extracting(Record::getIntent, e -> e.getValue().getElementId())
        .containsSubsequence(
            tuple(WorkflowInstanceIntent.ELEMENT_ACTIVATING, "outerSubProcess"),
            tuple(WorkflowInstanceIntent.ELEMENT_ACTIVATED, "outerSubProcess"),
            tuple(WorkflowInstanceIntent.ELEMENT_ACTIVATING, "innerSubProcess"),
            tuple(WorkflowInstanceIntent.ELEMENT_ACTIVATED, "innerSubProcess"),
            tuple(WorkflowInstanceIntent.ELEMENT_ACTIVATING, "task"),
            tuple(WorkflowInstanceIntent.ELEMENT_ACTIVATED, "task"),
            tuple(WorkflowInstanceIntent.ELEMENT_COMPLETING, "task"),
            tuple(WorkflowInstanceIntent.ELEMENT_COMPLETED, "task"),
            tuple(WorkflowInstanceIntent.ELEMENT_COMPLETING, "innerSubProcess"),
            tuple(WorkflowInstanceIntent.ELEMENT_COMPLETED, "innerSubProcess"),
            tuple(WorkflowInstanceIntent.ELEMENT_COMPLETING, "outerSubProcess"),
            tuple(WorkflowInstanceIntent.ELEMENT_COMPLETED, "outerSubProcess"));
  }

  @Test
  public void shouldTerminateBeforeTriggeringBoundaryEvent() {
    // given
    final Consumer<SubProcessBuilder> innerSubProcess =
        inner ->
            inner
                .embeddedSubProcess()
                .startEvent()
                .serviceTask("task", b -> b.zeebeTaskType("type"))
                .endEvent();
    final Consumer<SubProcessBuilder> outSubProcess =
        outer ->
            outer
                .embeddedSubProcess()
                .startEvent()
                .subProcess("innerSubProcess", innerSubProcess)
                .endEvent();
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess("shouldTerminateBeforeTriggeringBoundaryEvent")
            .startEvent()
            .subProcess("outerSubProcess", outSubProcess)
            .boundaryEvent("event")
            .message(m -> m.name("msg").zeebeCorrelationKey("key"))
            .endEvent("msgEnd")
            .moveToActivity("outerSubProcess")
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(model).deploy();
    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId("shouldTerminateBeforeTriggeringBoundaryEvent")
            .withVariable("key", "123")
            .create();

    // when
    assertThat(
            RecordingExporter.workflowInstanceSubscriptionRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
                .withIntent(WorkflowInstanceSubscriptionIntent.OPENED)
                .exists())
        .isTrue(); // await first subscription opened
    final Record<WorkflowInstanceRecordValue> activatedRecord =
        RecordingExporter.workflowInstanceRecords()
            .withIntent(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withElementId("task")
            .getFirst();

    ENGINE
        .message()
        .withName("msg")
        .withCorrelationKey("123")
        .withVariables(MsgPackUtil.asMsgPack("foo", 1))
        .publish();

    // then
    final List<Record<WorkflowInstanceRecordValue>> workflowInstanceEvents =
        RecordingExporter.workflowInstanceRecords()
            .skipUntil(r -> r.getPosition() > activatedRecord.getPosition())
            .withWorkflowInstanceKey(workflowInstanceKey)
            .limitToWorkflowInstanceCompleted()
            .asList();

    assertThat(workflowInstanceEvents)
        .extracting(Record::getIntent, e -> e.getValue().getElementId())
        .containsSubsequence(
            tuple(WorkflowInstanceIntent.EVENT_OCCURRED, "outerSubProcess"),
            tuple(WorkflowInstanceIntent.ELEMENT_TERMINATING, "outerSubProcess"),
            tuple(WorkflowInstanceIntent.ELEMENT_TERMINATING, "innerSubProcess"),
            tuple(WorkflowInstanceIntent.ELEMENT_TERMINATING, "task"),
            tuple(WorkflowInstanceIntent.ELEMENT_TERMINATED, "task"),
            tuple(WorkflowInstanceIntent.ELEMENT_TERMINATED, "innerSubProcess"),
            tuple(WorkflowInstanceIntent.ELEMENT_TERMINATED, "outerSubProcess"),
            tuple(WorkflowInstanceIntent.ELEMENT_ACTIVATING, "event"),
            tuple(WorkflowInstanceIntent.ELEMENT_ACTIVATED, "event"),
            tuple(WorkflowInstanceIntent.ELEMENT_COMPLETING, "event"),
            tuple(WorkflowInstanceIntent.ELEMENT_COMPLETED, "event"));
  }
}
