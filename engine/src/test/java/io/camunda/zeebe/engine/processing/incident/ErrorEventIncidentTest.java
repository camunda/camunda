/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.incident;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class ErrorEventIncidentTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "wf";
  private static final String JOB_TYPE = "test";
  private static final String ERROR_CODE = "error";

  private static final BpmnModelInstance BOUNDARY_EVENT_PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
          .boundaryEvent("error", b -> b.error(ERROR_CODE))
          .endEvent()
          .done();
  private static final BpmnModelInstance END_EVENT_PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .endEvent("error", e -> e.error(ERROR_CODE))
          .done();
  private static final BpmnModelInstance EVENT_SUB_PROCESS =
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

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldCreateIncidentWhenThrownErrorIsUncaught() {
    // given process with boundary error event
    ENGINE.deployment().withXmlResource(BOUNDARY_EVENT_PROCESS).deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when error thrown with different error code
    final var jobEvent =
        ENGINE
            .job()
            .ofInstance(processInstanceKey)
            .withType(JOB_TYPE)
            .withErrorCode("other-error")
            .withErrorMessage("error thrown")
            .throwError();

    // then
    Assertions.assertThat(
            RecordingExporter.incidentRecords(IncidentIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getValue())
        .describedAs("unhandled error event incident created")
        .hasErrorType(ErrorType.UNHANDLED_ERROR_EVENT)
        .hasErrorMessage("error thrown")
        .hasBpmnProcessId(jobEvent.getValue().getBpmnProcessId())
        .hasProcessDefinitionKey(jobEvent.getValue().getProcessDefinitionKey())
        .hasProcessInstanceKey(jobEvent.getValue().getProcessInstanceKey())
        .hasElementId(jobEvent.getValue().getElementId())
        .hasElementInstanceKey(jobEvent.getValue().getElementInstanceKey())
        .hasVariableScopeKey(jobEvent.getValue().getElementInstanceKey())
        .hasJobKey(jobEvent.getKey());
  }

  @Test
  public void shouldCreateIncidentWithDefaultErrorMessage() {
    // given
    ENGINE.deployment().withXmlResource(BOUNDARY_EVENT_PROCESS).deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(JOB_TYPE)
        .withErrorCode("other-error")
        .throwError();

    // then
    final Record<IncidentRecordValue> incidentEvent =
        RecordingExporter.incidentRecords()
            .withIntent(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(incidentEvent.getValue())
        .hasErrorType(ErrorType.UNHANDLED_ERROR_EVENT)
        .hasErrorMessage("An error was thrown with the code 'other-error' but not caught.");
  }

  @Test
  public void shouldCreateIncidentIfErrorIsThrownFromInterruptingEventSubprocess() {
    // given
    ENGINE.deployment().withXmlResource(EVENT_SUB_PROCESS).deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // trigger interrupting event subprocess
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(JOB_TYPE)
        .withErrorCode(ERROR_CODE)
        .throwError();

    final var jobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("task-in-subprocess")
            .getFirst()
            .getKey();

    // when
    ENGINE.job().withKey(jobKey).withType(JOB_TYPE).withErrorCode(ERROR_CODE).throwError();

    // then
    Assertions.assertThat(
            RecordingExporter.incidentRecords(IncidentIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getValue())
        .hasErrorType(ErrorType.UNHANDLED_ERROR_EVENT)
        .hasErrorMessage(
            String.format("An error was thrown with the code '%s' but not caught.", ERROR_CODE))
        .hasElementId("NO_CATCH_EVENT_FOUND");
  }

  @Test
  public void shouldResolveIncidentWhenTerminatingScope() {
    // given
    ENGINE.deployment().withXmlResource(BOUNDARY_EVENT_PROCESS).deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(JOB_TYPE)
        .withErrorCode("other-error")
        .throwError();

    RecordingExporter.incidentRecords()
        .withIntent(IncidentIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).cancel();

    // then
    assertThat(
            RecordingExporter.records()
                .limitToProcessInstance(processInstanceKey)
                .incidentRecords())
        .extracting(Record::getIntent)
        .contains(IncidentIntent.RESOLVED);

    assertThat(RecordingExporter.records().limitToProcessInstance(processInstanceKey).jobRecords())
        .extracting(Record::getIntent)
        .doesNotContain(JobIntent.CANCEL);
  }

  @Test
  public void shouldCreateIncidentOnErrorEndEvent() {
    // given
    ENGINE.deployment().withXmlResource(END_EVENT_PROCESS).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final Record<IncidentRecordValue> incidentEvent =
        RecordingExporter.incidentRecords()
            .withIntent(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final var endEvent =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.END_EVENT)
            .getFirst();

    Assertions.assertThat(incidentEvent.getValue())
        .hasErrorType(ErrorType.UNHANDLED_ERROR_EVENT)
        .hasErrorMessage(
            "Expected to throw an error event with the code 'error', but it was not caught.")
        .hasBpmnProcessId(endEvent.getValue().getBpmnProcessId())
        .hasProcessDefinitionKey(endEvent.getValue().getProcessDefinitionKey())
        .hasProcessInstanceKey(endEvent.getValue().getProcessInstanceKey())
        .hasElementId(endEvent.getValue().getElementId())
        .hasElementInstanceKey(endEvent.getKey())
        .hasVariableScopeKey(endEvent.getKey())
        .hasJobKey(-1);
  }

  @Test
  public void shouldNotResolveIncidentOnEndEvent() {
    // given
    ENGINE.deployment().withXmlResource(END_EVENT_PROCESS).deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords()
            .withIntent(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // when
    ENGINE.incident().ofInstance(processInstanceKey).withKey(incident.getKey()).resolve();

    // then
    assertThat(
            RecordingExporter.incidentRecords()
                .withProcessInstanceKey(processInstanceKey)
                .onlyEvents()
                .limit(3))
        .extracting(Record::getIntent)
        .describedAs("incident is created, resolved and recreated")
        .containsExactly(IncidentIntent.CREATED, IncidentIntent.RESOLVED, IncidentIntent.CREATED);

    assertThat(
            RecordingExporter.incidentRecords(IncidentIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .onlyEvents()
                .filter(r -> r.getKey() != incident.getKey())
                .findFirst())
        .describedAs("incident is recreated as new incident")
        .isPresent()
        .hasValueSatisfying(
            newIncident -> assertThat(newIncident.getValue()).isEqualTo(incident.getValue()));
  }

  /** regression test for https://github.com/camunda-cloud/zeebe/issues/7160 */
  @Test
  public void shouldNotResolveIncidentForJob() {
    ENGINE.deployment().withXmlResource(BOUNDARY_EVENT_PROCESS).deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(JOB_TYPE)
        .withErrorCode("other-error")
        .throwError();

    // given an unhandled error event incident
    final var incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withErrorType(ErrorType.UNHANDLED_ERROR_EVENT)
            .getFirst();

    // when problem is not fixed, but incident is resolved
    ENGINE.incident().ofInstance(processInstanceKey).withKey(incident.getKey()).resolve();

    // then
    assertThat(
            RecordingExporter.incidentRecords()
                .withProcessInstanceKey(processInstanceKey)
                .onlyEvents()
                .limit(3))
        .extracting(Record::getIntent)
        .describedAs("incident is created, resolved and recreated")
        .containsExactly(IncidentIntent.CREATED, IncidentIntent.RESOLVED, IncidentIntent.CREATED);

    assertThat(
            RecordingExporter.incidentRecords(IncidentIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .onlyEvents()
                .filter(r -> r.getKey() != incident.getKey())
                .findFirst())
        .describedAs("incident is recreated as new incident")
        .isPresent()
        .hasValueSatisfying(
            newIncident -> assertThat(newIncident.getValue()).isEqualTo(incident.getValue()));
  }
}
