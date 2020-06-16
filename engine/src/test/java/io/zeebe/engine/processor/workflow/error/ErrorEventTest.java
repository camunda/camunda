/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.zeebe.engine.util.EngineRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.builder.EventSubProcessBuilder;
import io.zeebe.model.bpmn.builder.ServiceTaskBuilder;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.intent.JobIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.function.Consumer;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class ErrorEventTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "wf";
  private static final String JOB_TYPE = "test";
  private static final String ERROR_CODE = "ERROR";

  private static final BpmnModelInstance SINGLE_BOUNDARY_EVENT =
      workflow(
          serviceTask -> serviceTask.boundaryEvent("error", b -> b.error(ERROR_CODE)).endEvent());

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private static BpmnModelInstance workflow(final Consumer<ServiceTaskBuilder> customizer) {
    final var builder =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE));

    customizer.accept(builder);

    return builder.endEvent().done();
  }

  @Test
  public void shouldTriggerEvent() {
    // given
    ENGINE.deployment().withXmlResource(SINGLE_BOUNDARY_EVENT).deploy();

    final var workflowInstanceKey = ENGINE.workflowInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    ENGINE
        .job()
        .ofInstance(workflowInstanceKey)
        .withType(JOB_TYPE)
        .withErrorCode(ERROR_CODE)
        .throwError();

    // then
    assertThat(
            RecordingExporter.workflowInstanceRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limitToWorkflowInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSequence(
            tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.EVENT_OCCURRED),
            tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.BOUNDARY_EVENT, WorkflowInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.BOUNDARY_EVENT, WorkflowInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.BOUNDARY_EVENT, WorkflowInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.BOUNDARY_EVENT, WorkflowInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.SEQUENCE_FLOW, WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple(BpmnElementType.END_EVENT, WorkflowInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.END_EVENT, WorkflowInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.END_EVENT, WorkflowInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.END_EVENT, WorkflowInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, WorkflowInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.PROCESS, WorkflowInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldCatchErrorEventsByErrorCode() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            workflow(
                serviceTask -> {
                  serviceTask.boundaryEvent("error-1", b -> b.error("error-1").endEvent());
                  serviceTask.boundaryEvent("error-2", b -> b.error("error-2").endEvent());
                }))
        .deploy();

    final var workflowInstanceKey1 = ENGINE.workflowInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var workflowInstanceKey2 = ENGINE.workflowInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    ENGINE
        .job()
        .ofInstance(workflowInstanceKey1)
        .withType(JOB_TYPE)
        .withErrorCode("error-1")
        .throwError();

    ENGINE
        .job()
        .ofInstance(workflowInstanceKey2)
        .withType(JOB_TYPE)
        .withErrorCode("error-2")
        .throwError();

    // then
    assertThat(
            RecordingExporter.workflowInstanceRecords()
                .withWorkflowInstanceKey(workflowInstanceKey1)
                .limitToWorkflowInstanceCompleted()
                .withElementType(BpmnElementType.BOUNDARY_EVENT))
        .extracting(r -> r.getValue().getElementId())
        .containsOnly("error-1");

    assertThat(
            RecordingExporter.workflowInstanceRecords()
                .withWorkflowInstanceKey(workflowInstanceKey2)
                .limitToWorkflowInstanceCompleted()
                .withElementType(BpmnElementType.BOUNDARY_EVENT))
        .extracting(r -> r.getValue().getElementId())
        .containsOnly("error-2");
  }

  @Test
  public void shouldNotCancelJob() {
    // given
    ENGINE.deployment().withXmlResource(SINGLE_BOUNDARY_EVENT).deploy();

    final var workflowInstanceKey = ENGINE.workflowInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    ENGINE
        .job()
        .ofInstance(workflowInstanceKey)
        .withType(JOB_TYPE)
        .withErrorCode(ERROR_CODE)
        .throwError();

    // then
    assertThat(
            RecordingExporter.records().limitToWorkflowInstance(workflowInstanceKey).jobRecords())
        .extracting(Record::getIntent)
        .containsExactly(
            JobIntent.CREATE, JobIntent.CREATED, JobIntent.THROW_ERROR, JobIntent.ERROR_THROWN);
  }

  @Test
  public void shouldCatchErrorFromChildInstance() {
    // given
    final var workflowChild =
        Bpmn.createExecutableProcess("wf-child")
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
            .endEvent()
            .done();

    final var workflowParent =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .callActivity("call", c -> c.zeebeProcessId("wf-child"))
            .boundaryEvent("error", b -> b.error(ERROR_CODE).endEvent())
            .endEvent()
            .done();

    ENGINE
        .deployment()
        .withXmlResource("wf-child.bpmn", workflowChild)
        .withXmlResource("wf-parent.bpmn", workflowParent)
        .deploy();

    final var parentWorkflowInstanceKey =
        ENGINE.workflowInstance().ofBpmnProcessId(PROCESS_ID).create();

    final var childWorkflowInstanceKey =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
            .withParentWorkflowInstanceKey(parentWorkflowInstanceKey)
            .getFirst()
            .getValue()
            .getWorkflowInstanceKey();

    // when
    ENGINE
        .job()
        .ofInstance(childWorkflowInstanceKey)
        .withType(JOB_TYPE)
        .withErrorCode(ERROR_CODE)
        .throwError();

    // then
    assertThat(
            RecordingExporter.workflowInstanceRecords()
                .withWorkflowInstanceKey(childWorkflowInstanceKey)
                .limitToWorkflowInstanceTerminated())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.PROCESS, WorkflowInstanceIntent.ELEMENT_TERMINATED));

    assertThat(
            RecordingExporter.workflowInstanceRecords()
                .withWorkflowInstanceKey(parentWorkflowInstanceKey)
                .limitToWorkflowInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.CALL_ACTIVITY, WorkflowInstanceIntent.EVENT_OCCURRED),
            tuple(BpmnElementType.CALL_ACTIVITY, WorkflowInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.BOUNDARY_EVENT, WorkflowInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.BOUNDARY_EVENT, WorkflowInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, WorkflowInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldCatchErrorInsideMultiInstanceSubprocess() {
    // given
    final Consumer<EventSubProcessBuilder> eventSubprocess =
        s -> s.startEvent("error-start-event").error(ERROR_CODE).endEvent();

    final var workflow =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .subProcess(
                "subprocess",
                s ->
                    s.multiInstance(m -> m.zeebeInputCollectionExpression("items"))
                        .embeddedSubProcess()
                        .eventSubProcess("error-subprocess", eventSubprocess)
                        .startEvent()
                        .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
                        .endEvent())
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(workflow).deploy();

    final var workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("items", List.of(1))
            .create();

    // when
    ENGINE
        .job()
        .ofInstance(workflowInstanceKey)
        .withType(JOB_TYPE)
        .withErrorCode(ERROR_CODE)
        .throwError();

    // then
    assertThat(
            RecordingExporter.workflowInstanceRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limitToWorkflowInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple("error-start-event", WorkflowInstanceIntent.EVENT_OCCURRED),
            tuple("task", WorkflowInstanceIntent.ELEMENT_TERMINATED),
            tuple("error-subprocess", WorkflowInstanceIntent.ELEMENT_ACTIVATED),
            tuple("error-start-event", WorkflowInstanceIntent.ELEMENT_ACTIVATED),
            tuple("error-start-event", WorkflowInstanceIntent.ELEMENT_COMPLETED),
            tuple("error-subprocess", WorkflowInstanceIntent.ELEMENT_COMPLETED),
            tuple("subprocess", WorkflowInstanceIntent.ELEMENT_COMPLETED),
            tuple(PROCESS_ID, WorkflowInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldCatchErrorOutsideMultiInstanceSubprocess() {
    // given
    final var workflow =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .subProcess(
                "subprocess",
                s ->
                    s.multiInstance(m -> m.zeebeInputCollectionExpression("[1]"))
                        .embeddedSubProcess()
                        .startEvent()
                        .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
                        .endEvent())
            .boundaryEvent("error-boundary-event", b -> b.error(ERROR_CODE))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(workflow).deploy();

    final var workflowInstanceKey = ENGINE.workflowInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    ENGINE
        .job()
        .ofInstance(workflowInstanceKey)
        .withType(JOB_TYPE)
        .withErrorCode(ERROR_CODE)
        .throwError();

    // then
    assertThat(
            RecordingExporter.workflowInstanceRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limitToWorkflowInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, WorkflowInstanceIntent.EVENT_OCCURRED),
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, WorkflowInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.SUB_PROCESS, WorkflowInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.SERVICE_TASK, WorkflowInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.SUB_PROCESS, WorkflowInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, WorkflowInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.BOUNDARY_EVENT, WorkflowInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.BOUNDARY_EVENT, WorkflowInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.END_EVENT, WorkflowInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, WorkflowInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldThrowErrorOnEndEvent() {
    // given
    final var workflow =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .subProcess(
                "subProcess",
                subProcess ->
                    subProcess
                        .embeddedSubProcess()
                        .startEvent()
                        .endEvent("throw-error", e -> e.error(ERROR_CODE)))
            .boundaryEvent("catch-error", b -> b.error(ERROR_CODE))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(workflow).deploy();

    // when
    final var workflowInstanceKey = ENGINE.workflowInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    assertThat(
            RecordingExporter.workflowInstanceRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limitToWorkflowInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.END_EVENT, WorkflowInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.SUB_PROCESS, WorkflowInstanceIntent.EVENT_OCCURRED),
            tuple(BpmnElementType.SUB_PROCESS, WorkflowInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.END_EVENT, WorkflowInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.END_EVENT, WorkflowInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.SUB_PROCESS, WorkflowInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.BOUNDARY_EVENT, WorkflowInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.BOUNDARY_EVENT, WorkflowInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, WorkflowInstanceIntent.ELEMENT_COMPLETED));
  }
}
