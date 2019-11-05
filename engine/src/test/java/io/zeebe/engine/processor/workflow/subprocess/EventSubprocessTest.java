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
import io.zeebe.model.bpmn.builder.EmbeddedSubProcessBuilder;
import io.zeebe.model.bpmn.builder.ProcessBuilder;
import io.zeebe.protocol.record.Assertions;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.intent.JobIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import io.zeebe.protocol.record.value.WorkflowInstanceRecordValue;
import io.zeebe.protocol.record.value.deployment.DeployedWorkflow;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.util.List;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class EventSubprocessTest {
  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldTriggerEventSubprocess() {
    // given
    final BpmnModelInstance model = createEventSubProc();
    final DeployedWorkflow workflow =
        ENGINE
            .deployment()
            .withXmlResource(model)
            .deploy()
            .getValue()
            .getDeployedWorkflows()
            .get(0);

    // when
    final long wfInstanceKey =
        ENGINE.workflowInstance().ofBpmnProcessId("proc").withVariable("innerKey", "123").create();

    // then
    final Record<WorkflowInstanceRecordValue> timerTriggered =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.EVENT_OCCURRED)
            .withWorkflowInstanceKey(wfInstanceKey)
            .getFirst();
    Assertions.assertThat(timerTriggered.getValue())
        .hasWorkflowKey(workflow.getWorkflowKey())
        .hasWorkflowInstanceKey(wfInstanceKey)
        .hasBpmnElementType(BpmnElementType.START_EVENT)
        .hasElementId("event_sub_start")
        .hasVersion(workflow.getVersion())
        .hasFlowScopeKey(wfInstanceKey);

    assertEventSubprocessLifecycle(wfInstanceKey);
  }

  @Test
  public void shouldTriggerNestedEventSubproc() {
    // given
    final BpmnModelInstance model = createNestedEventSubproc();
    final DeployedWorkflow workflow =
        ENGINE
            .deployment()
            .withXmlResource(model)
            .deploy()
            .getValue()
            .getDeployedWorkflows()
            .get(0);

    // when
    final long wfInstanceKey = ENGINE.workflowInstance().ofBpmnProcessId("proc").create();

    // then
    final Record<WorkflowInstanceRecordValue> subprocRecord =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
            .withWorkflowInstanceKey(wfInstanceKey)
            .withElementId("sub_proc")
            .getFirst();

    final Record<WorkflowInstanceRecordValue> eventOccurred =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.EVENT_OCCURRED)
            .withWorkflowInstanceKey(wfInstanceKey)
            .getFirst();
    Assertions.assertThat(eventOccurred.getValue())
        .hasWorkflowKey(workflow.getWorkflowKey())
        .hasWorkflowInstanceKey(wfInstanceKey)
        .hasBpmnElementType(BpmnElementType.START_EVENT)
        .hasElementId("event_sub_start")
        .hasVersion(workflow.getVersion())
        .hasFlowScopeKey(subprocRecord.getKey());

    assertEventSubprocessLifecycle(wfInstanceKey);
  }

  @Test
  public void shouldNotInterruptParentProcess() {
    // given
    final BpmnModelInstance model = createEventSubProc();
    ENGINE.deployment().withXmlResource(model).deploy();

    // when
    final long wfInstanceKey =
        ENGINE.workflowInstance().ofBpmnProcessId("proc").withVariable("innerKey", "123").create();
    assertEventSubprocessLifecycle(wfInstanceKey);

    ENGINE.job().ofInstance(wfInstanceKey).withType("type").complete().getKey();

    // then
    assertThat(
            RecordingExporter.workflowInstanceRecords()
                .withWorkflowInstanceKey(wfInstanceKey)
                .limitToWorkflowInstanceCompleted())
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSubsequence(
            tuple(BpmnElementType.START_EVENT, WorkflowInstanceIntent.EVENT_OCCURRED),
            tuple(BpmnElementType.SUB_PROCESS, WorkflowInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.SUB_PROCESS, WorkflowInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.END_EVENT, WorkflowInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, WorkflowInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldCompleteParentAfterEventSubprocCompletes() {
    // given
    final BpmnModelInstance model = createInterruptingEventSubproc();
    ENGINE.deployment().withXmlResource(model).deploy();

    // when
    final long wfInstanceKey =
        ENGINE.workflowInstance().ofBpmnProcessId("proc").withVariable("innerKey", "123").create();

    assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withWorkflowInstanceKey(wfInstanceKey)
                .exists())
        .describedAs("Expected job to be created")
        .isTrue();
    ENGINE.getClock().addTime(Duration.ofSeconds(60));

    // then
    assertEventSubprocessLifecycle(wfInstanceKey);
    assertThat(
            RecordingExporter.workflowInstanceRecords()
                .withWorkflowInstanceKey(wfInstanceKey)
                .limitToWorkflowInstanceCompleted())
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSubsequence(
            tuple(BpmnElementType.START_EVENT, WorkflowInstanceIntent.EVENT_OCCURRED),
            tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.SUB_PROCESS, WorkflowInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.SUB_PROCESS, WorkflowInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, WorkflowInstanceIntent.ELEMENT_COMPLETED));
  }

  private void assertEventSubprocessLifecycle(long worklowInstanceKey) {
    final List<Record<WorkflowInstanceRecordValue>> events =
        RecordingExporter.workflowInstanceRecords()
            .withWorkflowInstanceKey(worklowInstanceKey)
            .filter(r -> r.getValue().getElementId().startsWith("event_sub_"))
            .limit(13)
            .asList();

    assertThat(events)
        .extracting(Record::getIntent, e -> e.getValue().getElementId())
        .containsExactly(
            tuple(WorkflowInstanceIntent.EVENT_OCCURRED, "event_sub_start"),
            tuple(WorkflowInstanceIntent.ELEMENT_ACTIVATING, "event_sub_proc"),
            tuple(WorkflowInstanceIntent.ELEMENT_ACTIVATED, "event_sub_proc"),
            tuple(WorkflowInstanceIntent.ELEMENT_ACTIVATING, "event_sub_start"),
            tuple(WorkflowInstanceIntent.ELEMENT_ACTIVATED, "event_sub_start"),
            tuple(WorkflowInstanceIntent.ELEMENT_COMPLETING, "event_sub_start"),
            tuple(WorkflowInstanceIntent.ELEMENT_COMPLETED, "event_sub_start"),
            tuple(WorkflowInstanceIntent.ELEMENT_ACTIVATING, "event_sub_end"),
            tuple(WorkflowInstanceIntent.ELEMENT_ACTIVATED, "event_sub_end"),
            tuple(WorkflowInstanceIntent.ELEMENT_COMPLETING, "event_sub_end"),
            tuple(WorkflowInstanceIntent.ELEMENT_COMPLETED, "event_sub_end"),
            tuple(WorkflowInstanceIntent.ELEMENT_COMPLETING, "event_sub_proc"),
            tuple(WorkflowInstanceIntent.ELEMENT_COMPLETED, "event_sub_proc"));
  }

  private static BpmnModelInstance createEventSubProc() {
    final ProcessBuilder builder = Bpmn.createExecutableProcess("proc");
    builder
        .eventSubProcess("event_sub_proc")
        .startEvent("event_sub_start")
        .interrupting(false)
        .timerWithDuration("PT0S")
        .endEvent("event_sub_end");

    return builder
        .startEvent("start_proc")
        .serviceTask("task", t -> t.zeebeTaskType("type"))
        .endEvent("end_proc")
        .done();
  }

  private static BpmnModelInstance createNestedEventSubproc() {
    final EmbeddedSubProcessBuilder embeddedBuilder =
        Bpmn.createExecutableProcess("proc")
            .startEvent("proc_start")
            .subProcess("sub_proc")
            .embeddedSubProcess();
    embeddedBuilder
        .eventSubProcess("event_sub_proc")
        .startEvent("event_sub_start")
        .interrupting(false)
        .timerWithDuration("PT0S")
        .endEvent("event_sub_end");
    return embeddedBuilder
        .startEvent("sub_start")
        .serviceTask("task", t -> t.zeebeTaskType("type"))
        .endEvent("sub_end")
        .done();
  }

  private static BpmnModelInstance createInterruptingEventSubproc() {
    final ProcessBuilder builder = Bpmn.createExecutableProcess("proc");
    builder
        .eventSubProcess("event_sub_proc")
        .startEvent("event_sub_start")
        .interrupting(true)
        .timerWithDuration("PT60S")
        .endEvent("event_sub_end");

    return builder
        .startEvent("start_proc")
        .serviceTask("task", t -> t.zeebeTaskType("type"))
        .endEvent("sub_proc")
        .done();
  }
}
