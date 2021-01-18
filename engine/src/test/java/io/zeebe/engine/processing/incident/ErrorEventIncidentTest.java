/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.incident;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.engine.util.EngineRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.record.Assertions;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.intent.IncidentIntent;
import io.zeebe.protocol.record.intent.JobIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import io.zeebe.protocol.record.value.ErrorType;
import io.zeebe.protocol.record.value.IncidentRecordValue;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class ErrorEventIncidentTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "wf";
  private static final String JOB_TYPE = "test";
  private static final String ERROR_CODE = "error";

  private static final BpmnModelInstance BOUNDARY_EVENT_WORKFLOW =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
          .boundaryEvent("error", b -> b.error(ERROR_CODE))
          .endEvent()
          .done();

  private static final BpmnModelInstance END_EVENT_WORKFLOW =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .endEvent("error", e -> e.error(ERROR_CODE))
          .done();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldCreateIncident() {
    // given
    ENGINE.deployment().withXmlResource(BOUNDARY_EVENT_WORKFLOW).deploy();

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
        .hasErrorType(ErrorType.UNHANDLED_ERROR_EVENT)
        .hasErrorMessage("error thrown")
        .hasBpmnProcessId(jobEvent.getValue().getBpmnProcessId())
        .hasWorkflowKey(jobEvent.getValue().getWorkflowKey())
        .hasWorkflowInstanceKey(jobEvent.getValue().getWorkflowInstanceKey())
        .hasElementId(jobEvent.getValue().getElementId())
        .hasElementInstanceKey(jobEvent.getValue().getElementInstanceKey())
        .hasVariableScopeKey(jobEvent.getValue().getElementInstanceKey())
        .hasJobKey(jobEvent.getKey());
  }

  @Test
  public void shouldCreateIncidentWithDefaultErrorMessage() {
    // given
    ENGINE.deployment().withXmlResource(BOUNDARY_EVENT_WORKFLOW).deploy();

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
        .hasErrorType(ErrorType.UNHANDLED_ERROR_EVENT)
        .hasErrorMessage("An error was thrown with the code 'other-error' but not caught.");
  }

  @Test
  public void shouldCreateIncidentIfErrorIsThrownFromInterruptingEventSubprocess() {
    // given
    final var workflow =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .eventSubProcess(
                "error",
                subprocess ->
                    subprocess
                        .startEvent("error-start", s -> s.error(ERROR_CODE).interrupting(true))
                        .serviceTask("task-in-subprocess", t -> t.zeebeJobType(JOB_TYPE))
                        .endEvent())
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(workflow).deploy();

    final long workflowInstanceKey = ENGINE.workflowInstance().ofBpmnProcessId(PROCESS_ID).create();

    // trigger interrupting event subprocess
    ENGINE
        .job()
        .ofInstance(workflowInstanceKey)
        .withType(JOB_TYPE)
        .withErrorCode(ERROR_CODE)
        .throwError();

    final var jobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withElementId("task-in-subprocess")
            .getFirst()
            .getKey();

    // when
    ENGINE.job().withKey(jobKey).withType(JOB_TYPE).withErrorCode(ERROR_CODE).throwError();

    // then
    final Record<IncidentRecordValue> incidentEvent =
        RecordingExporter.incidentRecords()
            .withIntent(IncidentIntent.CREATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    Assertions.assertThat(incidentEvent.getValue())
        .hasErrorType(ErrorType.UNHANDLED_ERROR_EVENT)
        .hasErrorMessage(
            String.format("An error was thrown with the code '%s' but not caught.", ERROR_CODE))
        .hasElementId("task-in-subprocess");
  }

  @Test
  public void shouldResolveIncident() {
    // given
    ENGINE.deployment().withXmlResource(BOUNDARY_EVENT_WORKFLOW).deploy();

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
    ENGINE.incident().ofInstance(workflowInstanceKey).withKey(incidentEvent.getKey()).resolve();

    // then
    assertThat(ENGINE.jobs().withType(JOB_TYPE).activate().getValue().getJobKeys())
        .doesNotContain(jobEvent.getKey());

    assertThat(
            RecordingExporter.incidentRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limit(5))
        .extracting(Record::getIntent)
        .containsExactly(
            IncidentIntent.CREATE,
            IncidentIntent.CREATED,
            IncidentIntent.RESOLVED,
            IncidentIntent.CREATE,
            IncidentIntent.CREATED);
  }

  @Test
  public void shouldResolveIncidentWhenTerminatingScope() {
    // given
    ENGINE.deployment().withXmlResource(BOUNDARY_EVENT_WORKFLOW).deploy();

    final long workflowInstanceKey = ENGINE.workflowInstance().ofBpmnProcessId(PROCESS_ID).create();

    ENGINE
        .job()
        .ofInstance(workflowInstanceKey)
        .withType(JOB_TYPE)
        .withErrorCode("other-error")
        .throwError();

    RecordingExporter.incidentRecords()
        .withIntent(IncidentIntent.CREATED)
        .withWorkflowInstanceKey(workflowInstanceKey)
        .await();

    // when
    ENGINE.workflowInstance().withInstanceKey(workflowInstanceKey).cancel();

    // then
    assertThat(
            RecordingExporter.records()
                .limitToWorkflowInstance(workflowInstanceKey)
                .incidentRecords())
        .extracting(Record::getIntent)
        .contains(IncidentIntent.RESOLVED);

    assertThat(
            RecordingExporter.records().limitToWorkflowInstance(workflowInstanceKey).jobRecords())
        .extracting(Record::getIntent)
        .doesNotContain(JobIntent.CANCEL);
  }

  @Test
  public void shouldCreateIncidentOnErrorEndEvent() {
    // given
    ENGINE.deployment().withXmlResource(END_EVENT_WORKFLOW).deploy();

    // when
    final long workflowInstanceKey = ENGINE.workflowInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final Record<IncidentRecordValue> incidentEvent =
        RecordingExporter.incidentRecords()
            .withIntent(IncidentIntent.CREATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    final var endEvent =
        RecordingExporter.workflowInstanceRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withElementType(BpmnElementType.END_EVENT)
            .getFirst();

    Assertions.assertThat(incidentEvent.getValue())
        .hasErrorType(ErrorType.UNHANDLED_ERROR_EVENT)
        .hasErrorMessage(
            "Expected to throw an error event with the code 'error', but it was not caught.")
        .hasBpmnProcessId(endEvent.getValue().getBpmnProcessId())
        .hasWorkflowKey(endEvent.getValue().getWorkflowKey())
        .hasWorkflowInstanceKey(endEvent.getValue().getWorkflowInstanceKey())
        .hasElementId(endEvent.getValue().getElementId())
        .hasElementInstanceKey(endEvent.getKey())
        .hasVariableScopeKey(endEvent.getKey())
        .hasJobKey(-1);
  }

  @Test
  public void shouldNotResolveIncidentOnEndEvent() {
    // given
    ENGINE.deployment().withXmlResource(END_EVENT_WORKFLOW).deploy();

    final long workflowInstanceKey = ENGINE.workflowInstance().ofBpmnProcessId(PROCESS_ID).create();

    final Record<IncidentRecordValue> incidentEvent =
        RecordingExporter.incidentRecords()
            .withIntent(IncidentIntent.CREATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    // when
    ENGINE.incident().ofInstance(workflowInstanceKey).withKey(incidentEvent.getKey()).resolve();

    // then
    assertThat(
            RecordingExporter.incidentRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limit(3))
        .extracting(Record::getIntent)
        .containsExactly(IncidentIntent.CREATED, IncidentIntent.RESOLVED, IncidentIntent.CREATED);
  }
}
