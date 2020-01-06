/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.incident;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.engine.util.EngineRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.record.Assertions;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.intent.IncidentIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import io.zeebe.protocol.record.value.ErrorType;
import io.zeebe.protocol.record.value.IncidentRecordValue;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class ErrorEventIncidentTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "wf";
  private static final String JOB_TYPE = "test";
  private static final String ERROR_CODE = "error";

  private static final BpmnModelInstance WORKFLOW =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .serviceTask("task", t -> t.zeebeTaskType(JOB_TYPE))
          .boundaryEvent("error", b -> b.error(ERROR_CODE))
          .endEvent()
          .done();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Before
  public void init() {
    ENGINE.deployment().withXmlResource(WORKFLOW).deploy();
  }

  @Test
  public void shouldCreateIncident() {
    // given
    final long workflowInstanceKey = ENGINE.workflowInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    final var jobEvent =
        ENGINE
            .job()
            .ofInstance(workflowInstanceKey)
            .withType(JOB_TYPE)
            .withErrorCode("other-error")
            .withErrorMessage("error thrown")
            .throwError();

    // then
    final Record<IncidentRecordValue> incidentEvent =
        RecordingExporter.incidentRecords()
            .withIntent(IncidentIntent.CREATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    Assertions.assertThat(incidentEvent.getValue())
        .hasErrorType(ErrorType.JOB_NO_RETRIES)
        .hasErrorMessage("error thrown")
        .hasBpmnProcessId(jobEvent.getValue().getBpmnProcessId())
        .hasWorkflowKey(jobEvent.getValue().getWorkflowKey())
        .hasWorkflowInstanceKey(jobEvent.getValue().getWorkflowInstanceKey())
        .hasElementId(jobEvent.getValue().getElementId())
        .hasElementInstanceKey(jobEvent.getValue().getElementInstanceKey())
        .hasVariableScopeKey(jobEvent.getValue().getElementInstanceKey());
  }

  @Test
  public void shouldCreateIncidentWithDefaultErrorMessage() {
    // given
    final long workflowInstanceKey = ENGINE.workflowInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    ENGINE
        .job()
        .ofInstance(workflowInstanceKey)
        .withType(JOB_TYPE)
        .withErrorCode("other-error")
        .throwError();

    // then
    final Record<IncidentRecordValue> incidentEvent =
        RecordingExporter.incidentRecords()
            .withIntent(IncidentIntent.CREATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    Assertions.assertThat(incidentEvent.getValue())
        .hasErrorType(ErrorType.JOB_NO_RETRIES)
        .hasErrorMessage("An error was thrown with the code 'other-error' but not caught.");
  }

  @Test
  public void shouldResolveIncident() {
    // given
    final long workflowInstanceKey = ENGINE.workflowInstance().ofBpmnProcessId(PROCESS_ID).create();

    final var jobEvent =
        ENGINE
            .job()
            .ofInstance(workflowInstanceKey)
            .withType(JOB_TYPE)
            .withErrorCode("other-error")
            .throwError();

    final Record<IncidentRecordValue> incidentEvent =
        RecordingExporter.incidentRecords()
            .withIntent(IncidentIntent.CREATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    // when
    ENGINE.job().withKey(jobEvent.getKey()).withRetries(1).updateRetries();

    ENGINE.incident().ofInstance(workflowInstanceKey).withKey(incidentEvent.getKey()).resolve();

    // then
    assertThat(ENGINE.jobs().withType(JOB_TYPE).activate().getValue().getJobKeys())
        .contains(jobEvent.getKey());

    ENGINE.job().withKey(jobEvent.getKey()).withErrorCode(ERROR_CODE).throwError();

    assertThat(
            RecordingExporter.workflowInstanceRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limitToWorkflowInstanceCompleted()
                .withElementType(BpmnElementType.BOUNDARY_EVENT))
        .extracting(Record::getIntent)
        .contains(WorkflowInstanceIntent.ELEMENT_COMPLETED);
  }
}
