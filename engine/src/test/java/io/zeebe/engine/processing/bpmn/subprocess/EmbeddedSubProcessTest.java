/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.bpmn.subprocess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.zeebe.engine.util.EngineRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.builder.EmbeddedSubProcessBuilder;
import io.zeebe.model.bpmn.builder.SubProcessBuilder;
import io.zeebe.protocol.record.Assertions;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.intent.JobIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import io.zeebe.protocol.record.value.JobRecordValue;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.function.Consumer;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class EmbeddedSubProcessTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "workflow-with-sub-process";

  private static final BpmnModelInstance NO_TASK_SUB_PROCESS =
      workflowWithSubProcess(subProcess -> subProcess.startEvent().endEvent());

  private static final BpmnModelInstance ONE_TASK_SUB_PROCESS =
      workflowWithSubProcess(
          subProcess ->
              subProcess.startEvent().serviceTask("task", b -> b.zeebeJobType("task")).endEvent());

  private static final BpmnModelInstance PARALLEL_TASKS_SUB_PROCESS =
      workflowWithSubProcess(
          subProcess ->
              subProcess
                  .startEvent()
                  .parallelGateway("fork")
                  .serviceTask("task-1", b -> b.zeebeJobType("task-1"))
                  .sequenceFlowId("join-1")
                  .parallelGateway("join")
                  .moveToNode("fork")
                  .serviceTask("task-2", b -> b.zeebeJobType("task-2"))
                  .sequenceFlowId("join-2")
                  .connectTo("join")
                  .endEvent());

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private static BpmnModelInstance workflowWithSubProcess(
      final Consumer<EmbeddedSubProcessBuilder> subProcessBuilder) {
    return Bpmn.createExecutableProcess(PROCESS_ID)
        .startEvent()
        .subProcess(
            "sub-process", subProcess -> subProcessBuilder.accept(subProcess.embeddedSubProcess()))
        .endEvent()
        .done();
  }

  @Test
  public void shouldActivateSubProcess() {
    // given
    ENGINE.deployment().withXmlResource(NO_TASK_SUB_PROCESS).deploy();

    // when
    final long workflowInstanceKey = ENGINE.workflowInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    assertThat(
            RecordingExporter.workflowInstanceRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limitToWorkflowInstanceCompleted())
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSequence(
            tuple(BpmnElementType.SEQUENCE_FLOW, WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple(BpmnElementType.SUB_PROCESS, WorkflowInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.SUB_PROCESS, WorkflowInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.START_EVENT, WorkflowInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.START_EVENT, WorkflowInstanceIntent.ELEMENT_ACTIVATED));

    final var subProcessActivating =
        RecordingExporter.workflowInstanceRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withElementType(BpmnElementType.SUB_PROCESS)
            .withIntent(WorkflowInstanceIntent.ELEMENT_ACTIVATING)
            .getFirst();

    Assertions.assertThat(subProcessActivating.getValue())
        .hasFlowScopeKey(workflowInstanceKey)
        .hasElementId("sub-process");
  }

  @Test
  public void shouldCompleteSubProcess() {
    // given
    ENGINE.deployment().withXmlResource(NO_TASK_SUB_PROCESS).deploy();

    // when
    final long workflowInstanceKey = ENGINE.workflowInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    assertThat(
            RecordingExporter.workflowInstanceRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limitToWorkflowInstanceCompleted())
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSequence(
            tuple(BpmnElementType.END_EVENT, WorkflowInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.SUB_PROCESS, WorkflowInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.SUB_PROCESS, WorkflowInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.SEQUENCE_FLOW, WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple(BpmnElementType.END_EVENT, WorkflowInstanceIntent.ELEMENT_ACTIVATING));

    assertThat(
            RecordingExporter.workflowInstanceRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
                .withElementType(BpmnElementType.PROCESS)
                .limitToWorkflowInstanceCompleted())
        .extracting(Record::getIntent)
        .contains(WorkflowInstanceIntent.ELEMENT_COMPLETED);
  }

  @Test
  public void shouldCreateJobForInnerTask() {
    // given
    ENGINE.deployment().withXmlResource(ONE_TASK_SUB_PROCESS).deploy();

    // when
    final long workflowInstanceKey = ENGINE.workflowInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final var serviceTaskActivated =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .getFirst();

    final Record<JobRecordValue> jobCreated =
        RecordingExporter.jobRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withIntent(JobIntent.CREATED)
            .getFirst();

    Assertions.assertThat(jobCreated.getValue())
        .hasElementId("task")
        .hasElementInstanceKey(serviceTaskActivated.getKey())
        .hasBpmnProcessId(serviceTaskActivated.getValue().getBpmnProcessId())
        .hasWorkflowDefinitionVersion(serviceTaskActivated.getValue().getVersion())
        .hasWorkflowKey(serviceTaskActivated.getValue().getWorkflowKey());
  }

  @Test
  public void shouldTerminateSubProcess() {
    // given
    ENGINE.deployment().withXmlResource(ONE_TASK_SUB_PROCESS).deploy();

    final long workflowInstanceKey = ENGINE.workflowInstance().ofBpmnProcessId(PROCESS_ID).create();

    RecordingExporter.workflowInstanceRecords()
        .withWorkflowInstanceKey(workflowInstanceKey)
        .withElementType(BpmnElementType.SERVICE_TASK)
        .withIntent(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
        .await();

    // when
    ENGINE.workflowInstance().withInstanceKey(workflowInstanceKey).cancel();

    // then
    assertThat(
            RecordingExporter.workflowInstanceRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limitToWorkflowInstanceTerminated())
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSequence(
            tuple(BpmnElementType.PROCESS, WorkflowInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.SUB_PROCESS, WorkflowInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.SUB_PROCESS, WorkflowInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.PROCESS, WorkflowInstanceIntent.ELEMENT_TERMINATED));
  }

  @Test
  public void shouldInterruptSubProcess() {
    // given
    final var workflow =
        workflowWithSubProcess(
            subProcess ->
                subProcess
                    .startEvent()
                    .serviceTask("task", t -> t.zeebeJobType("task"))
                    .endEvent()
                    .subProcessDone()
                    .boundaryEvent(
                        "cancel",
                        b -> b.message(m -> m.name("cancel").zeebeCorrelationKeyExpression("key")))
                    .endEvent());

    ENGINE.deployment().withXmlResource(workflow).deploy();

    final long workflowInstanceKey =
        ENGINE.workflowInstance().ofBpmnProcessId(PROCESS_ID).withVariable("key", "key-1").create();

    RecordingExporter.workflowInstanceRecords()
        .withWorkflowInstanceKey(workflowInstanceKey)
        .withElementType(BpmnElementType.SERVICE_TASK)
        .withIntent(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
        .await();

    // when
    ENGINE.message().withName("cancel").withCorrelationKey("key-1").publish();

    // then
    assertThat(
            RecordingExporter.workflowInstanceRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limitToWorkflowInstanceCompleted())
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSequence(
            tuple(BpmnElementType.SUB_PROCESS, WorkflowInstanceIntent.EVENT_OCCURRED),
            tuple(BpmnElementType.SUB_PROCESS, WorkflowInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.SUB_PROCESS, WorkflowInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.BOUNDARY_EVENT, WorkflowInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.BOUNDARY_EVENT, WorkflowInstanceIntent.ELEMENT_ACTIVATED));

    assertThat(
            RecordingExporter.workflowInstanceRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
                .withElementType(BpmnElementType.PROCESS)
                .limitToWorkflowInstanceCompleted())
        .extracting(Record::getIntent)
        .contains(WorkflowInstanceIntent.ELEMENT_COMPLETED);
  }

  @Test
  public void shouldCompleteNestedSubProcess() {
    // given
    final Consumer<SubProcessBuilder> nestedSubProcess =
        subProcess -> subProcess.embeddedSubProcess().startEvent().endEvent();

    final BpmnModelInstance workflow =
        workflowWithSubProcess(
            subProcess ->
                subProcess
                    .startEvent()
                    .subProcess("nestedSubProcess", nestedSubProcess)
                    .endEvent());

    ENGINE.deployment().withXmlResource(workflow).deploy();

    // when
    final long workflowInstanceKey = ENGINE.workflowInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    assertThat(
            RecordingExporter.workflowInstanceRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limitToWorkflowInstanceCompleted())
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSubsequence(
            tuple(BpmnElementType.SUB_PROCESS, WorkflowInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.SUB_PROCESS, WorkflowInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.END_EVENT, WorkflowInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.SUB_PROCESS, WorkflowInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.END_EVENT, WorkflowInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.SUB_PROCESS, WorkflowInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.END_EVENT, WorkflowInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, WorkflowInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldCompleteSubProcessWithParallelFlow() {
    // given
    final var workflow =
        workflowWithSubProcess(
            subProcess ->
                subProcess
                    .startEvent()
                    .parallelGateway("fork")
                    .serviceTask("task-1", b -> b.zeebeJobType("task-1"))
                    .endEvent()
                    .moveToLastGateway()
                    .serviceTask("task-2", b -> b.zeebeJobType("task-2"))
                    .endEvent());

    ENGINE.deployment().withXmlResource(workflow).deploy();

    final long workflowInstanceKey = ENGINE.workflowInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    ENGINE.job().ofInstance(workflowInstanceKey).withType("task-1").complete();

    RecordingExporter.workflowInstanceRecords()
        .withWorkflowInstanceKey(workflowInstanceKey)
        .withElementType(BpmnElementType.END_EVENT)
        .withIntent(WorkflowInstanceIntent.ELEMENT_COMPLETED)
        .await();

    ENGINE.job().ofInstance(workflowInstanceKey).withType("task-2").complete();

    // then
    assertThat(
            RecordingExporter.workflowInstanceRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limitToWorkflowInstanceCompleted())
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSubsequence(
            tuple(BpmnElementType.PARALLEL_GATEWAY, WorkflowInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.END_EVENT, WorkflowInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.END_EVENT, WorkflowInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.SUB_PROCESS, WorkflowInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.SUB_PROCESS, WorkflowInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, WorkflowInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldTerminateSubProcessWithParallelFlow() {
    // given
    ENGINE.deployment().withXmlResource(PARALLEL_TASKS_SUB_PROCESS).deploy();

    final long workflowInstanceKey = ENGINE.workflowInstance().ofBpmnProcessId(PROCESS_ID).create();

    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withWorkflowInstanceKey(workflowInstanceKey)
        .limit(2)
        .await();

    // when
    ENGINE.workflowInstance().withInstanceKey(workflowInstanceKey).cancel();

    // then
    assertThat(
            RecordingExporter.workflowInstanceRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limitToWorkflowInstanceTerminated())
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSequence(
            tuple(BpmnElementType.PROCESS, WorkflowInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.SUB_PROCESS, WorkflowInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.SUB_PROCESS, WorkflowInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.PROCESS, WorkflowInstanceIntent.ELEMENT_TERMINATED));
  }

  @Test
  public void shouldTerminateSubProcessWithPendingParallelGateway() {
    // given
    ENGINE.deployment().withXmlResource(PARALLEL_TASKS_SUB_PROCESS).deploy();

    final long workflowInstanceKey = ENGINE.workflowInstance().ofBpmnProcessId(PROCESS_ID).create();

    ENGINE.job().ofInstance(workflowInstanceKey).withType("task-1").complete();

    // await that one sequence flow on the joining parallel gateway is taken
    RecordingExporter.workflowInstanceRecords()
        .withWorkflowInstanceKey(workflowInstanceKey)
        .withElementId("join-1")
        .withIntent(WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN)
        .await();

    // when
    ENGINE.workflowInstance().withInstanceKey(workflowInstanceKey).cancel();

    // then
    assertThat(
            RecordingExporter.workflowInstanceRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limitToWorkflowInstanceTerminated())
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSequence(
            tuple(BpmnElementType.PROCESS, WorkflowInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.SUB_PROCESS, WorkflowInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.SUB_PROCESS, WorkflowInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.PROCESS, WorkflowInstanceIntent.ELEMENT_TERMINATED));
  }
}
