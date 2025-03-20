/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.incident;

import static io.camunda.zeebe.protocol.record.intent.JobIntent.ERROR_THROWN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.collection.Maps;
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
  private static final BpmnModelInstance BOUNDARY_EVENT_SUBPROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent("start")
          .subProcess(
              "subprocess",
              subprocess ->
                  subprocess
                      .embeddedSubProcess()
                      .startEvent("start_subprocess")
                      .serviceTask("task_in_subprocess", b -> b.zeebeJobType(JOB_TYPE))
                      .boundaryEvent(
                          "error_in_subprocess", event -> event.error("error_in_subprocess"))
                      .endEvent("end_boundary_in_subprocess")
                      .moveToActivity("task_in_subprocess")
                      .endEvent("end_subprocess")
                      .subProcessDone())
          .boundaryEvent("error", b -> b.error("error"))
          .endEvent("end_boundary")
          .moveToActivity("subprocess")
          .endEvent("end")
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
        .hasErrorMessage(
            "Expected to throw an error event with the code 'other-error' with message 'error thrown', but it was not caught. Available error events are [error]")
        .hasBpmnProcessId(jobEvent.getValue().getBpmnProcessId())
        .hasProcessDefinitionKey(jobEvent.getValue().getProcessDefinitionKey())
        .hasProcessInstanceKey(jobEvent.getValue().getProcessInstanceKey())
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
        .hasTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
        .hasErrorType(ErrorType.UNHANDLED_ERROR_EVENT)
        .hasErrorMessage(
            "Expected to throw an error event with the code 'other-error', but it was not caught. Available error events are [error]");
  }

  @Test
  public void shouldCreateIncidentWithCustomTenant() {
    // given
    final String tenantId = "acme";
    final String username = "username";
    ENGINE.tenant().newTenant().withTenantId(tenantId).create();
    ENGINE.user().newUser(username).create();
    ENGINE
        .tenant()
        .addEntity(tenantId)
        .withEntityId(username)
        .withEntityType(EntityType.USER)
        .add();
    ENGINE.deployment().withXmlResource(BOUNDARY_EVENT_PROCESS).withTenantId(tenantId).deploy();

    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withTenantId(tenantId).create();

    // when
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(JOB_TYPE)
        .withErrorCode("other-error")
        .withAuthorizedTenantIds(tenantId)
        .throwError(username);

    // then
    final Record<IncidentRecordValue> incidentEvent =
        RecordingExporter.incidentRecords()
            .withIntent(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(incidentEvent.getValue())
        .hasTenantId(tenantId)
        .hasErrorType(ErrorType.UNHANDLED_ERROR_EVENT)
        .hasErrorMessage(
            "Expected to throw an error event with the code 'other-error', but it was not caught. Available error events are [error]");
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

    final String elementIdThrowingIncident = "task-in-subprocess";
    final var jobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(elementIdThrowingIncident)
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
            String.format(
                "Expected to throw an error event with the code '%s', but it was not caught. No error events are available in the scope.",
                ERROR_CODE))
        .hasElementId(elementIdThrowingIncident);
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
                .betweenProcessInstance(processInstanceKey)
                .incidentRecords())
        .extracting(Record::getIntent)
        .contains(IncidentIntent.RESOLVED);

    assertThat(RecordingExporter.records().betweenProcessInstance(processInstanceKey).jobRecords())
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
            "Expected to throw an error event with the code 'error', but it was not caught. No error events are available in the scope.")
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

  @Test
  public void shouldResolveIncidentForJob() {
    ENGINE.deployment().withXmlResource(BOUNDARY_EVENT_PROCESS).deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // given an unhandled error event incident is raised for a job
    final var job =
        ENGINE
            .job()
            .ofInstance(processInstanceKey)
            .withType(JOB_TYPE)
            .withErrorCode("other-error")
            .throwError();

    final var incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withErrorType(ErrorType.UNHANDLED_ERROR_EVENT)
            .getFirst();

    // when that incident is resolved
    ENGINE.incident().ofInstance(processInstanceKey).withKey(incident.getKey()).resolve();

    assertThat(
            RecordingExporter.incidentRecords()
                .withProcessInstanceKey(processInstanceKey)
                .onlyEvents()
                .limit(2))
        .extracting(Record::getIntent)
        .describedAs("incident is created and resolved")
        .containsExactly(IncidentIntent.CREATED, IncidentIntent.RESOLVED);

    // then the job can now be completed again
    ENGINE.job().withKey(job.getKey()).complete();

    assertThat(RecordingExporter.jobRecords().withRecordKey(job.getKey()).onlyEvents().limit(3))
        .extracting(Record::getIntent)
        .describedAs("job that had error_thrown is completed")
        .containsExactly(JobIntent.CREATED, JobIntent.ERROR_THROWN, JobIntent.COMPLETED);
  }

  @Test
  public void shouldCreateIncidentWhenNoCatchEventFoundWithBoundaryEventsInMultipleScopes() {
    // given
    ENGINE.deployment().withXmlResource(BOUNDARY_EVENT_SUBPROCESS).deploy();

    final long instanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    final Record<JobRecordValue> result =
        ENGINE
            .job()
            .ofInstance(instanceKey)
            .withType(JOB_TYPE)
            .withErrorCode("unknown_error_code")
            .withErrorMessage("error message")
            .throwError();

    // then
    Assertions.assertThat(result).hasRecordType(RecordType.EVENT).hasIntent(ERROR_THROWN);
    Assertions.assertThat(result.getValue())
        .hasErrorCode("unknown_error_code")
        .hasErrorMessage("error message");
    Assertions.assertThat(
            RecordingExporter.incidentRecords()
                .withProcessInstanceKey(instanceKey)
                .withIntent(IncidentIntent.CREATED)
                .getFirst()
                .getValue())
        .hasErrorMessage(
            "Expected to throw an error event with the code 'unknown_error_code' with message 'error message', but it was not caught."
                + " Available error events are [error_in_subprocess, error]");
  }

  @Test
  public void shouldCreateIncidentIfErrorCodeExpressionForTheEndEventCannotBeEvaluated() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .endEvent("error", e -> e.errorExpression("unknown_error_code"))
                .done())
        .deploy();

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
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasErrorMessage(
            """
            Expected result of the expression 'unknown_error_code' to be 'STRING', but was 'NULL'. \
            The evaluation reported the following warnings:
            [NO_VARIABLE_FOUND] No variable found with name 'unknown_error_code'""")
        .hasBpmnProcessId(endEvent.getValue().getBpmnProcessId())
        .hasProcessDefinitionKey(endEvent.getValue().getProcessDefinitionKey())
        .hasProcessInstanceKey(endEvent.getValue().getProcessInstanceKey())
        .hasElementId(endEvent.getValue().getElementId())
        .hasElementInstanceKey(endEvent.getKey())
        .hasVariableScopeKey(endEvent.getKey())
        .hasJobKey(-1);
  }

  @Test
  public void shouldResolveIncidentIfErrorCodeCouldNotBeEvaluated() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .subProcess(
                    "sp",
                    sp ->
                        sp.embeddedSubProcess()
                            .startEvent()
                            .endEvent("error", e -> e.errorExpression("errorCodeLookup")))
                .boundaryEvent("boundary", b -> b.error("errorCode").endEvent())
                .done())
        .deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final Record<IncidentRecordValue> incidentCreatedRecord =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    ENGINE
        .variables()
        .ofScope(incidentCreatedRecord.getValue().getElementInstanceKey())
        .withDocument(Maps.of(entry("errorCodeLookup", "errorCode")))
        .update();

    // when
    final Record<IncidentRecordValue> incidentResolvedEvent =
        ENGINE
            .incident()
            .ofInstance(processInstanceKey)
            .withKey(incidentCreatedRecord.getKey())
            .resolve();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted()
                .onlyEvents())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.BOUNDARY_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.BOUNDARY_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));

    assertThat(incidentResolvedEvent.getKey()).isEqualTo(incidentCreatedRecord.getKey());
  }
}
