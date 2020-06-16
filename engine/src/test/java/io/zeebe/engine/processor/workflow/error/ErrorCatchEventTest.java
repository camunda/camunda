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
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class ErrorCatchEventTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String TASK_ELEMENT_ID = "task";
  private static final String PROCESS_ID = "wf";
  private static final String JOB_TYPE = "test";
  private static final String ERROR_CODE = "ERROR";

  @Parameter(0)
  public String description;

  @Parameter(1)
  public BpmnModelInstance workflow;

  @Parameter(2)
  public String expectedEventOccurredElementId;

  @Parameter(3)
  public String expectedActivatedElement;

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Parameters(name = "{0}")
  public static Object[][] parameters() {
    return new Object[][] {
      {
        "boundary event on service task",
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask(TASK_ELEMENT_ID, t -> t.zeebeJobType(JOB_TYPE))
            .boundaryEvent("error-boundary-event", b -> b.error(ERROR_CODE))
            .endEvent()
            .done(),
        TASK_ELEMENT_ID,
        "error-boundary-event"
      },
      {
        "boundary event on subprocess",
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .subProcess(
                "subprocess",
                s ->
                    s.embeddedSubProcess()
                        .startEvent()
                        .serviceTask(TASK_ELEMENT_ID, t -> t.zeebeJobType(JOB_TYPE))
                        .endEvent())
            .boundaryEvent("error-boundary-event", b -> b.error(ERROR_CODE))
            .endEvent()
            .done(),
        "subprocess",
        "error-boundary-event"
      },
      {
        "boundary event on multi-instance subprocess",
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .subProcess(
                "subprocess",
                s ->
                    s.multiInstance(m -> m.zeebeInputCollectionExpression("[1]"))
                        .embeddedSubProcess()
                        .startEvent()
                        .serviceTask(TASK_ELEMENT_ID, t -> t.zeebeJobType(JOB_TYPE))
                        .endEvent())
            .boundaryEvent("error-boundary-event", b -> b.error(ERROR_CODE))
            .endEvent()
            .done(),
        "subprocess",
        "error-boundary-event"
      },
      {
        "boundary event on multi-instance service task",
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask(
                TASK_ELEMENT_ID,
                t ->
                    t.zeebeJobType(JOB_TYPE)
                        .multiInstance(m -> m.zeebeInputCollectionExpression("[1]")))
            .boundaryEvent("error-boundary-event", b -> b.error(ERROR_CODE))
            .endEvent()
            .done(),
        TASK_ELEMENT_ID,
        "error-boundary-event"
      },
      {
        "error event subprocess",
        Bpmn.createExecutableProcess(PROCESS_ID)
            .eventSubProcess(
                "error-event-subprocess",
                s ->
                    s.startEvent("error-start-event")
                        .error(ERROR_CODE)
                        .interrupting(true)
                        .endEvent())
            .startEvent()
            .serviceTask(TASK_ELEMENT_ID, t -> t.zeebeJobType(JOB_TYPE))
            .endEvent()
            .done(),
        "error-start-event",
        "error-event-subprocess"
      },
      {
        "favor boundary event on task over boundary event on subprocess",
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .subProcess(
                "subprocess",
                s ->
                    s.embeddedSubProcess()
                        .startEvent()
                        .serviceTask(TASK_ELEMENT_ID, t -> t.zeebeJobType(JOB_TYPE))
                        .boundaryEvent("error-boundary-event", b -> b.error(ERROR_CODE))
                        .endEvent())
            .boundaryEvent("error-boundary-event-on-subprocess", b -> b.error(ERROR_CODE))
            .endEvent()
            .done(),
        TASK_ELEMENT_ID,
        "error-boundary-event"
      },
      {
        "favor boundary event on task over error event subprocess",
        Bpmn.createExecutableProcess(PROCESS_ID)
            .eventSubProcess(
                "error-event-subprocess",
                s -> s.startEvent().error(ERROR_CODE).interrupting(true).endEvent())
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
            .boundaryEvent("error-boundary-event")
            .error(ERROR_CODE)
            .endEvent()
            .done(),
        TASK_ELEMENT_ID,
        "error-boundary-event"
      },
      {
        "favor error event subprocess over boundary event on subprocess",
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .subProcess(
                "sub",
                s ->
                    s.embeddedSubProcess()
                        .eventSubProcess(
                            "error-event-subprocess",
                            e ->
                                e.startEvent("error-start-event")
                                    .error(ERROR_CODE)
                                    .interrupting(true)
                                    .endEvent())
                        .startEvent()
                        .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
                        .endEvent())
            .boundaryEvent("error", b -> b.error(ERROR_CODE))
            .endEvent()
            .done(),
        "error-start-event",
        "error-event-subprocess"
      },
    };
  }

  @Test
  public void shouldTriggerEvent() {
    // given
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
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple(expectedEventOccurredElementId, WorkflowInstanceIntent.EVENT_OCCURRED),
            tuple(TASK_ELEMENT_ID, WorkflowInstanceIntent.ELEMENT_TERMINATING),
            tuple(TASK_ELEMENT_ID, WorkflowInstanceIntent.ELEMENT_TERMINATED),
            tuple(expectedActivatedElement, WorkflowInstanceIntent.ELEMENT_ACTIVATING),
            tuple(expectedActivatedElement, WorkflowInstanceIntent.ELEMENT_COMPLETED),
            tuple(PROCESS_ID, WorkflowInstanceIntent.ELEMENT_COMPLETED));
  }
}
