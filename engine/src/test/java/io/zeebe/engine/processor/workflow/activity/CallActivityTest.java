/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.zeebe.engine.util.EngineRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.record.Assertions;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class CallActivityTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID_PARENT = "wf-parent";
  private static final String PROCESS_ID_CHILD = "wf-child";

  private static final BpmnModelInstance WORKFLOW_PARENT =
      Bpmn.createExecutableProcess(PROCESS_ID_PARENT)
          .startEvent()
          .callActivity("call", c -> c.zeebeProcessId(PROCESS_ID_CHILD))
          .endEvent()
          .done();

  private static final BpmnModelInstance WORKFLOW_CHILD =
      Bpmn.createExecutableProcess(PROCESS_ID_CHILD).startEvent().endEvent().done();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Before
  public void init() {
    ENGINE
        .deployment()
        .withXmlResource("wf-parent.bpmn", WORKFLOW_PARENT)
        .withXmlResource("wf-child.bpmn", WORKFLOW_CHILD)
        .deploy();
  }

  @Test
  public void shouldActivateCallActivity() {
    // when
    final var workflowInstanceKey =
        ENGINE.workflowInstance().ofBpmnProcessId(PROCESS_ID_PARENT).create();

    // then
    assertThat(
            RecordingExporter.workflowInstanceRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
                .withElementId("call")
                .limit(2))
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsExactly(
            tuple(BpmnElementType.CALL_ACTIVITY, WorkflowInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.CALL_ACTIVITY, WorkflowInstanceIntent.ELEMENT_ACTIVATED));
  }

  @Test
  public void shouldCreateInstanceOfCalledElement() {
    // when
    final var workflowInstanceKey =
        ENGINE.workflowInstance().ofBpmnProcessId(PROCESS_ID_PARENT).create();

    // then
    assertThat(
            RecordingExporter.workflowInstanceRecords()
                .withParentWorkflowInstanceKey(workflowInstanceKey)
                .limit(4))
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsExactly(
            tuple(BpmnElementType.PROCESS, WorkflowInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.PROCESS, WorkflowInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.START_EVENT, WorkflowInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.START_EVENT, WorkflowInstanceIntent.ELEMENT_ACTIVATED));
  }

  @Test
  public void shouldCreateInstanceOfLatestVersionOfCalledElement() {
    // given
    final var workflowChildV2 =
        Bpmn.createExecutableProcess(PROCESS_ID_CHILD).startEvent("v2").endEvent().done();

    final var secondDeployment =
        ENGINE.deployment().withXmlResource("wf-child.bpmn", workflowChildV2).deploy();
    final var secondVersion = secondDeployment.getValue().getDeployedWorkflows().get(0);

    // when
    final var workflowInstanceKey =
        ENGINE.workflowInstance().ofBpmnProcessId(PROCESS_ID_PARENT).create();

    // then
    Assertions.assertThat(
            RecordingExporter.workflowInstanceRecords()
                .withParentWorkflowInstanceKey(workflowInstanceKey)
                .getFirst()
                .getValue())
        .hasVersion(secondVersion.getVersion())
        .hasWorkflowKey(secondVersion.getWorkflowKey());
  }

  @Test
  public void shouldHaveReferenceToParentInstance() {
    // when
    final var workflowInstanceKey =
        ENGINE.workflowInstance().ofBpmnProcessId(PROCESS_ID_PARENT).create();

    // then
    final var parentElementInstanceKey =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withElementType(BpmnElementType.CALL_ACTIVITY)
            .getFirst()
            .getKey();

    assertThat(
            RecordingExporter.records()
                .limitToWorkflowInstance(workflowInstanceKey)
                .workflowInstanceRecords()
                .withParentWorkflowInstanceKey(workflowInstanceKey))
        .extracting(Record::getValue)
        .extracting(v -> tuple(v.getParentWorkflowInstanceKey(), v.getParentElementInstanceKey()))
        .containsOnly(tuple(workflowInstanceKey, parentElementInstanceKey));
  }

  @Test
  public void shouldCompleteCallActivity() {
    // when
    final var workflowInstanceKey =
        ENGINE.workflowInstance().ofBpmnProcessId(PROCESS_ID_PARENT).create();

    // then
    assertThat(
            RecordingExporter.records()
                .limitToWorkflowInstance(workflowInstanceKey)
                .workflowInstanceRecords())
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSubsequence(
            tuple(BpmnElementType.END_EVENT, WorkflowInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, WorkflowInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.CALL_ACTIVITY, WorkflowInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.CALL_ACTIVITY, WorkflowInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.SEQUENCE_FLOW, WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple(BpmnElementType.PROCESS, WorkflowInstanceIntent.ELEMENT_COMPLETED));
  }
}
