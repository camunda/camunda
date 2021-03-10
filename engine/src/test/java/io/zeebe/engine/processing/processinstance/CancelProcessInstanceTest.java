/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.workflowinstance;

import static io.zeebe.protocol.record.intent.WorkflowInstanceIntent.CANCEL;
import static io.zeebe.protocol.record.intent.WorkflowInstanceIntent.ELEMENT_ACTIVATED;
import static io.zeebe.protocol.record.intent.WorkflowInstanceIntent.ELEMENT_TERMINATED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.zeebe.engine.util.EngineRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import io.zeebe.protocol.record.Assertions;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.RecordType;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.intent.JobIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import io.zeebe.protocol.record.value.JobRecordValue;
import io.zeebe.protocol.record.value.WorkflowInstanceRecordValue;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class CancelWorkflowInstanceTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  private static final BpmnModelInstance WORKFLOW =
      Bpmn.createExecutableProcess("WORKFLOW")
          .startEvent()
          .serviceTask("task", t -> t.zeebeJobType("test").zeebeJobRetries("5"))
          .endEvent()
          .done();
  private static final BpmnModelInstance SUB_PROCESS_WORKFLOW =
      Bpmn.createExecutableProcess("SUB_PROCESS_WORKFLOW")
          .startEvent()
          .subProcess("subProcess")
          .embeddedSubProcess()
          .startEvent()
          .serviceTask("task", t -> t.zeebeJobType("test").zeebeJobRetries("5"))
          .endEvent()
          .subProcessDone()
          .endEvent()
          .done();
  private static final BpmnModelInstance FORK_PROCESS;

  static {
    final AbstractFlowNodeBuilder<?, ?> builder =
        Bpmn.createExecutableProcess("FORK_PROCESS")
            .startEvent("start")
            .parallelGateway("fork")
            .serviceTask("task1", b -> b.zeebeJobType("type1"))
            .endEvent("end1")
            .moveToNode("fork");

    FORK_PROCESS =
        builder.serviceTask("task2", b -> b.zeebeJobType("type2")).endEvent("end2").done();
  }

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @BeforeClass
  public static void init() {
    ENGINE.deployment().withXmlResource(WORKFLOW).deploy();
    ENGINE.deployment().withXmlResource(SUB_PROCESS_WORKFLOW).deploy();
    ENGINE.deployment().withXmlResource(FORK_PROCESS).deploy();
  }

  @Test
  public void shouldCancelWorkflowInstance() {
    // given
    final long workflowInstanceKey = ENGINE.workflowInstance().ofBpmnProcessId("WORKFLOW").create();
    RecordingExporter.workflowInstanceRecords()
        .withWorkflowInstanceKey(workflowInstanceKey)
        .withElementId("task")
        .withIntent(ELEMENT_ACTIVATED)
        .getFirst();

    // when
    ENGINE.workflowInstance().withInstanceKey(workflowInstanceKey).cancel();

    // then
    final Record<WorkflowInstanceRecordValue> workflowInstanceCanceledEvent =
        RecordingExporter.workflowInstanceRecords()
            .withRecordKey(workflowInstanceKey)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withIntent(ELEMENT_TERMINATED)
            .getFirst();

    Assertions.assertThat(workflowInstanceCanceledEvent.getValue())
        .hasBpmnProcessId("WORKFLOW")
        .hasVersion(1)
        .hasWorkflowInstanceKey(workflowInstanceKey)
        .hasElementId("WORKFLOW");

    final List<Record<WorkflowInstanceRecordValue>> workflowEvents =
        RecordingExporter.workflowInstanceRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .skipUntil(r -> r.getIntent() == CANCEL)
            .limit(r -> r.getKey() == workflowInstanceKey && r.getIntent() == ELEMENT_TERMINATED)
            .asList();

    assertThat(workflowEvents)
        .hasSize(5)
        .extracting(e -> e.getValue().getElementId(), e -> e.getIntent())
        .containsSequence(
            tuple("", CANCEL),
            tuple("WORKFLOW", WorkflowInstanceIntent.ELEMENT_TERMINATING),
            tuple("task", WorkflowInstanceIntent.ELEMENT_TERMINATING),
            tuple("task", ELEMENT_TERMINATED),
            tuple("WORKFLOW", ELEMENT_TERMINATED));
  }

  @Test
  public void shouldNotCancelElementInstance() {
    // given
    final long workflowInstanceKey = ENGINE.workflowInstance().ofBpmnProcessId("WORKFLOW").create();
    final Record<WorkflowInstanceRecordValue> task =
        RecordingExporter.workflowInstanceRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withElementId("task")
            .withIntent(ELEMENT_ACTIVATED)
            .getFirst();

    // when
    final Record<WorkflowInstanceRecordValue> rejectedCancel =
        ENGINE
            .workflowInstance()
            .withInstanceKey(task.getKey())
            .onPartition(1)
            .expectRejection()
            .cancel();

    // then
    assertThat(rejectedCancel.getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);
    assertThat(rejectedCancel.getRejectionReason())
        .isEqualTo(
            "Expected to cancel a workflow instance with key '"
                + task.getKey()
                + "', but no such workflow was found");
  }

  @Test
  public void shouldCancelWorkflowInstanceWithEmbeddedSubProcess() {
    // given
    final long workflowInstanceKey =
        ENGINE.workflowInstance().ofBpmnProcessId("SUB_PROCESS_WORKFLOW").create();

    RecordingExporter.workflowInstanceRecords()
        .withWorkflowInstanceKey(workflowInstanceKey)
        .withElementId("task")
        .withIntent(ELEMENT_ACTIVATED)
        .getFirst();

    // when
    ENGINE.workflowInstance().withInstanceKey(workflowInstanceKey).cancel();

    // then
    final List<Record<WorkflowInstanceRecordValue>> workflowEvents =
        RecordingExporter.workflowInstanceRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .skipUntil(r -> r.getIntent() == WorkflowInstanceIntent.CANCEL)
            .limitToWorkflowInstanceTerminated()
            .asList();

    assertThat(workflowEvents)
        .hasSize(7)
        .extracting(e -> e.getValue().getElementId(), e -> e.getIntent())
        .containsSequence(
            tuple("", WorkflowInstanceIntent.CANCEL),
            tuple("SUB_PROCESS_WORKFLOW", WorkflowInstanceIntent.ELEMENT_TERMINATING),
            tuple("subProcess", WorkflowInstanceIntent.ELEMENT_TERMINATING),
            tuple("task", WorkflowInstanceIntent.ELEMENT_TERMINATING),
            tuple("task", ELEMENT_TERMINATED),
            tuple("subProcess", ELEMENT_TERMINATED),
            tuple("SUB_PROCESS_WORKFLOW", ELEMENT_TERMINATED));
  }

  @Test
  public void shouldCancelActivityInstance() {
    // given
    final long workflowInstanceKey = ENGINE.workflowInstance().ofBpmnProcessId("WORKFLOW").create();
    final Record<WorkflowInstanceRecordValue> activityActivatedEvent =
        RecordingExporter.workflowInstanceRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withElementId("task")
            .withIntent(ELEMENT_ACTIVATED)
            .getFirst();

    // when
    ENGINE.workflowInstance().withInstanceKey(workflowInstanceKey).cancel();

    // then
    final Record<WorkflowInstanceRecordValue> activityTerminatedEvent =
        RecordingExporter.workflowInstanceRecords()
            .withElementId("task")
            .withIntent(ELEMENT_TERMINATED)
            .getFirst();

    assertThat(activityTerminatedEvent.getKey()).isEqualTo(activityActivatedEvent.getKey());

    Assertions.assertThat(activityActivatedEvent.getValue())
        .hasBpmnProcessId("WORKFLOW")
        .hasVersion(1)
        .hasWorkflowInstanceKey(workflowInstanceKey)
        .hasElementId("task");
  }

  @Test
  public void shouldCancelWorkflowInstanceWithParallelExecution() {
    // given
    final long workflowInstanceKey =
        ENGINE.workflowInstance().ofBpmnProcessId("FORK_PROCESS").create();

    RecordingExporter.workflowInstanceRecords()
        .withWorkflowInstanceKey(workflowInstanceKey)
        .withElementType(BpmnElementType.SERVICE_TASK)
        .withIntent(ELEMENT_ACTIVATED)
        .limit(2)
        .asList();

    // when
    ENGINE.workflowInstance().withInstanceKey(workflowInstanceKey).cancel();

    // then
    final List<Record<WorkflowInstanceRecordValue>> terminatedElements =
        RecordingExporter.workflowInstanceRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .skipUntil(r -> r.getIntent() == WorkflowInstanceIntent.CANCEL)
            .limitToWorkflowInstanceTerminated()
            .filter(r -> r.getIntent() == ELEMENT_TERMINATED)
            .asList();

    assertThat(terminatedElements).hasSize(3);
    assertThat(terminatedElements)
        .extracting(Record::getValue)
        .extracting(WorkflowInstanceRecordValue::getElementId)
        .containsSubsequence("task1", "FORK_PROCESS")
        .containsSubsequence("task2", "FORK_PROCESS")
        .contains("task1", "task2", "FORK_PROCESS");
  }

  @Test
  public void shouldCancelIntermediateCatchEvent() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("shouldCancelIntermediateCatchEvent")
                .startEvent()
                .intermediateCatchEvent("catch-event")
                .message(b -> b.name("msg").zeebeCorrelationKeyExpression("id"))
                .done())
        .deploy();

    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId("shouldCancelIntermediateCatchEvent")
            .withVariable("id", "123")
            .create();

    RecordingExporter.workflowInstanceRecords()
        .withWorkflowInstanceKey(workflowInstanceKey)
        .withElementId("catch-event")
        .withIntent(ELEMENT_ACTIVATED)
        .getFirst();

    // when
    ENGINE.workflowInstance().withInstanceKey(workflowInstanceKey).cancel();

    // then
    final Record<WorkflowInstanceRecordValue> terminatedEvent =
        RecordingExporter.workflowInstanceRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withElementId("shouldCancelIntermediateCatchEvent")
            .withIntent(ELEMENT_TERMINATED)
            .getFirst();

    Assertions.assertThat(terminatedEvent.getValue())
        .hasBpmnProcessId("shouldCancelIntermediateCatchEvent")
        .hasVersion(1)
        .hasWorkflowInstanceKey(workflowInstanceKey)
        .hasElementId("shouldCancelIntermediateCatchEvent");
  }

  @Test
  public void shouldCancelJobForActivity() {
    // given
    final long workflowInstanceKey = ENGINE.workflowInstance().ofBpmnProcessId("WORKFLOW").create();
    final Record<JobRecordValue> jobCreatedEvent =
        RecordingExporter.jobRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withIntent(JobIntent.CREATED)
            .getFirst();

    // when
    ENGINE.workflowInstance().withInstanceKey(workflowInstanceKey).cancel();

    // then
    final Record<WorkflowInstanceRecordValue> terminateActivity =
        RecordingExporter.workflowInstanceRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withElementId("task")
            .withIntent(WorkflowInstanceIntent.ELEMENT_TERMINATING)
            .getFirst();

    final Record<JobRecordValue> jobCancelCmd =
        RecordingExporter.jobRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .onlyCommands()
            .withIntent(JobIntent.CANCEL)
            .getFirst();
    final Record<JobRecordValue> jobCanceledEvent =
        RecordingExporter.jobRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withIntent(JobIntent.CANCELED)
            .getFirst();

    assertThat(jobCanceledEvent.getKey()).isEqualTo(jobCreatedEvent.getKey());
    assertThat(jobCancelCmd.getSourceRecordPosition()).isEqualTo(terminateActivity.getPosition());
    assertThat(jobCanceledEvent.getSourceRecordPosition()).isEqualTo(jobCancelCmd.getPosition());

    final JobRecordValue jobCanceledEventValue = jobCanceledEvent.getValue();
    assertThat(jobCanceledEventValue.getWorkflowInstanceKey()).isEqualTo(workflowInstanceKey);

    Assertions.assertThat(jobCanceledEventValue)
        .hasElementId("task")
        .hasWorkflowDefinitionVersion(1)
        .hasBpmnProcessId("WORKFLOW");
  }

  @Test
  public void shouldRejectCancelNonExistingWorkflowInstance() {
    // when
    final Record<WorkflowInstanceRecordValue> rejectedCancel =
        ENGINE.workflowInstance().withInstanceKey(-1).onPartition(1).expectRejection().cancel();

    // then
    assertThat(rejectedCancel.getRecordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(rejectedCancel.getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);
    assertThat(rejectedCancel.getRejectionReason())
        .isEqualTo(
            "Expected to cancel a workflow instance with key '-1', but no such workflow was found");

    assertThat(
            RecordingExporter.workflowInstanceRecords()
                .withPosition(rejectedCancel.getSourceRecordPosition())
                .withIntent(CANCEL)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldRejectCancelCompletedWorkflowInstance() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("shouldRejectCancelCompletedWorkflowInstance")
                .startEvent()
                .endEvent()
                .done())
        .deploy();

    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId("shouldRejectCancelCompletedWorkflowInstance")
            .create();

    RecordingExporter.workflowInstanceRecords()
        .withElementId("shouldRejectCancelCompletedWorkflowInstance")
        .withIntent(WorkflowInstanceIntent.ELEMENT_COMPLETED)
        .getFirst();

    // when
    final Record<WorkflowInstanceRecordValue> rejectedCancel =
        ENGINE.workflowInstance().withInstanceKey(workflowInstanceKey).expectRejection().cancel();

    // then
    assertThat(rejectedCancel.getRecordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(rejectedCancel.getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);
    assertThat(rejectedCancel.getRejectionReason())
        .isEqualTo(
            "Expected to cancel a workflow instance with key '"
                + workflowInstanceKey
                + "', but no such workflow was found");

    assertThat(
            RecordingExporter.workflowInstanceRecords()
                .withPosition(rejectedCancel.getSourceRecordPosition())
                .withIntent(CANCEL)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldRejectCancelAlreadyCanceledWorkflowInstance() {
    // given
    final long workflowInstanceKey = ENGINE.workflowInstance().ofBpmnProcessId("WORKFLOW").create();
    ENGINE.workflowInstance().withInstanceKey(workflowInstanceKey).cancel();

    // when
    final Record<WorkflowInstanceRecordValue> rejectedCancel =
        ENGINE.workflowInstance().withInstanceKey(workflowInstanceKey).expectRejection().cancel();

    // then
    assertThat(rejectedCancel.getRecordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(rejectedCancel.getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);
    assertThat(rejectedCancel.getRejectionReason())
        .isEqualTo(
            "Expected to cancel a workflow instance with key '"
                + workflowInstanceKey
                + "', but no such workflow was found");
  }

  @Test
  public void shouldWriteEntireEventOnCancel() {
    // given
    final long workflowInstanceKey = ENGINE.workflowInstance().ofBpmnProcessId("WORKFLOW").create();
    final Record<WorkflowInstanceRecordValue> activatedEvent =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
            .withElementId("WORKFLOW")
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    // when
    final Record<WorkflowInstanceRecordValue> canceledRecord =
        ENGINE.workflowInstance().withInstanceKey(workflowInstanceKey).cancel();

    // then
    assertThat(canceledRecord.getValue()).isEqualTo(activatedEvent.getValue());
  }
}
