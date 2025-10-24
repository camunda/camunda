/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import static io.camunda.zeebe.engine.EngineConfiguration.DEFAULT_MAX_ERROR_MESSAGE_SIZE;
import static io.camunda.zeebe.protocol.record.intent.IncidentIntent.CREATED;
import static io.camunda.zeebe.protocol.record.intent.JobIntent.ERROR_THROWN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class JobThrowErrorTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";
  private static String jobType;
  private static final String ERROR_CODE = "ERROR";
  private static String username;
  private static String tenantId;

  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @BeforeClass
  public static void setUp() {
    tenantId = UUID.randomUUID().toString();
    username = UUID.randomUUID().toString();
    ENGINE.user().newUser(username).create().getValue();
    ENGINE.tenant().newTenant().withTenantId(tenantId).create().getValue().getTenantKey();
    ENGINE
        .tenant()
        .addEntity(tenantId)
        .withEntityType(EntityType.USER)
        .withEntityId(username)
        .add();

    ENGINE
        .authorization()
        .newAuthorization()
        .withPermissions(PermissionType.UPDATE_PROCESS_INSTANCE)
        .withResourceType(AuthorizationResourceType.PROCESS_DEFINITION)
        .withResourceMatcher(AuthorizationResourceMatcher.ID)
        .withResourceId(PROCESS_ID)
        .withOwnerId(username)
        .withOwnerType(AuthorizationOwnerType.USER)
        .create();
  }

  @Before
  public void setup() {
    jobType = helper.getJobType();
  }

  @Test
  public void shouldThrowError() {
    // given
    final var job = ENGINE.createJob(jobType, PROCESS_ID);

    // when
    final Record<JobRecordValue> result =
        ENGINE
            .job()
            .withKey(job.getKey())
            .withErrorCode("error")
            .withErrorMessage("error-message")
            .throwError();

    // then
    Assertions.assertThat(result).hasRecordType(RecordType.EVENT).hasIntent(ERROR_THROWN);
    Assertions.assertThat(result.getValue()).hasErrorCode("error").hasErrorMessage("error-message");
  }

  @Test
  public void shouldRejectIfJobNotFound() {
    // given
    final int key = 123;

    // when
    final Record<JobRecordValue> result =
        ENGINE.job().withKey(key).withErrorCode("error").throwError();

    // then
    Assertions.assertThat(result).hasRejectionType(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldRejectIfJobIsFailed() {
    // given
    final var job = ENGINE.createJob(jobType, PROCESS_ID);

    ENGINE.jobs().withType(jobType).activate();
    ENGINE.job().withKey(job.getKey()).withRetries(0).fail();

    // when
    final Record<JobRecordValue> result =
        ENGINE.job().withKey(job.getKey()).withErrorCode("error").throwError();

    // then
    Assertions.assertThat(result).hasRejectionType(RejectionType.INVALID_STATE);
    assertThat(result.getRejectionReason()).contains("it is in state 'FAILED'");
  }

  @Test
  public void shouldRejectIfErrorIsThrown() {
    // given
    final var job = ENGINE.createJob(jobType, PROCESS_ID);

    ENGINE.job().withKey(job.getKey()).withErrorCode("error").throwError();

    // when
    final Record<JobRecordValue> result =
        ENGINE.job().withKey(job.getKey()).withErrorCode("error").throwError();

    // then
    Assertions.assertThat(result).hasRejectionType(RejectionType.INVALID_STATE);
    assertThat(result.getRejectionReason()).contains("it is in state 'ERROR_THROWN'");
  }

  @Test
  public void shouldThrowErrorWithVariables() {
    // given
    final var process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(jobType))
            .boundaryEvent("error-boundary-event", b -> b.error(ERROR_CODE))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final Record<JobRecordValue> error =
        ENGINE
            .job()
            .ofInstance(processInstanceKey)
            .withType(jobType)
            .withErrorCode(ERROR_CODE)
            .withErrorMessage("error-message")
            .withVariables("{'foo':'bar'}")
            .throwError();

    // then
    Assertions.assertThat(error).hasRecordType(RecordType.EVENT).hasIntent(ERROR_THROWN);
    Assertions.assertThat(error.getValue())
        .hasErrorCode(ERROR_CODE)
        .hasErrorMessage("error-message")
        .hasVariables(Map.of("foo", "bar"));

    final Record<ProcessInstanceRecordValue> errorEvent =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("error-boundary-event")
            .getFirst();

    assertThat(
            RecordingExporter.records()
                .limitToProcessInstance(processInstanceKey)
                .variableRecords()
                .collect(Collectors.toList()))
        .extracting(
            r -> r.getValue().getName(),
            r -> r.getValue().getValue(),
            r -> r.getValue().getScopeKey(),
            Record::getIntent)
        .describedAs("The variables are created at the error catch event.")
        .containsExactly(tuple("foo", "\"bar\"", errorEvent.getKey(), VariableIntent.CREATED));
  }

  @Test
  public void shouldThrowErrorWithVariablesAndOutputMapping() {
    // given
    final var process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(jobType))
            .boundaryEvent(
                "error-boundary-event",
                b -> b.error(ERROR_CODE).zeebeOutputExpression("foo", "output"))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final Record<JobRecordValue> error =
        ENGINE
            .job()
            .ofInstance(processInstanceKey)
            .withType(jobType)
            .withErrorCode(ERROR_CODE)
            .withErrorMessage("error-message")
            .withVariables("{'foo':'bar'}")
            .throwError();

    // then
    Assertions.assertThat(error).hasRecordType(RecordType.EVENT).hasIntent(ERROR_THROWN);
    Assertions.assertThat(error.getValue())
        .hasErrorCode(ERROR_CODE)
        .hasErrorMessage("error-message")
        .hasVariables(Map.of("foo", "bar"));

    final Record<ProcessInstanceRecordValue> errorEvent =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("error-boundary-event")
            .getFirst();

    assertThat(
            RecordingExporter.records()
                .limitToProcessInstance(processInstanceKey)
                .variableRecords()
                .collect(Collectors.toList()))
        .extracting(
            r -> r.getValue().getName(),
            r -> r.getValue().getValue(),
            r -> r.getValue().getScopeKey(),
            Record::getIntent)
        .describedAs(
            "The variables are created at the error catch event, and with an output mapping to created at the process instance.")
        .containsExactly(
            tuple("foo", "\"bar\"", errorEvent.getKey(), VariableIntent.CREATED),
            tuple("output", "\"bar\"", processInstanceKey, VariableIntent.CREATED));
  }

  @Test
  public void shouldThrowErrorWithSubProcessVariables() {
    // given
    final var process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .subProcess(
                "subprocess",
                s ->
                    s.embeddedSubProcess()
                        .startEvent()
                        .serviceTask("task", t -> t.zeebeJobType(jobType))
                        .endEvent())
            .boundaryEvent("error-boundary-event", b -> b.error(ERROR_CODE))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final Record<JobRecordValue> error =
        ENGINE
            .job()
            .ofInstance(processInstanceKey)
            .withType(jobType)
            .withErrorCode(ERROR_CODE)
            .withErrorMessage("error-message")
            .withVariables("{'foo':'bar'}")
            .throwError();

    // then
    Assertions.assertThat(error).hasRecordType(RecordType.EVENT).hasIntent(ERROR_THROWN);
    Assertions.assertThat(error.getValue())
        .hasErrorCode(ERROR_CODE)
        .hasErrorMessage("error-message")
        .hasVariables(Map.of("foo", "bar"));

    final Record<ProcessInstanceRecordValue> errorEvent =
        RecordingExporter.processInstanceRecords()
            .withBpmnProcessId(PROCESS_ID)
            .withElementId("error-boundary-event")
            .getFirst();

    assertThat(
            RecordingExporter.records()
                .limitToProcessInstance(processInstanceKey)
                .variableRecords()
                .collect(Collectors.toList()))
        .extracting(
            r -> r.getValue().getName(),
            r -> r.getValue().getValue(),
            r -> r.getValue().getScopeKey(),
            Record::getIntent)
        .describedAs("The variables are created at the error catch event.")
        .containsExactly(tuple("foo", "\"bar\"", errorEvent.getKey(), VariableIntent.CREATED));
  }

  @Test
  public void shouldThrowErrorWithSubProcessVariablesWithOutputMapping() {
    // given
    final var process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .subProcess(
                "subprocess",
                s ->
                    s.embeddedSubProcess()
                        .startEvent()
                        .serviceTask("task", t -> t.zeebeJobType(jobType))
                        .endEvent())
            .boundaryEvent(
                "error-boundary-event",
                b -> b.error(ERROR_CODE).zeebeOutputExpression("foo", "output"))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final Record<JobRecordValue> error =
        ENGINE
            .job()
            .ofInstance(processInstanceKey)
            .withType(jobType)
            .withErrorCode(ERROR_CODE)
            .withErrorMessage("error-message")
            .withVariables("{'foo':'bar'}")
            .throwError();

    // then
    Assertions.assertThat(error).hasRecordType(RecordType.EVENT).hasIntent(ERROR_THROWN);
    Assertions.assertThat(error.getValue())
        .hasErrorCode(ERROR_CODE)
        .hasErrorMessage("error-message")
        .hasVariables(Map.of("foo", "bar"));

    final Record<ProcessInstanceRecordValue> errorEvent =
        RecordingExporter.processInstanceRecords()
            .withBpmnProcessId(PROCESS_ID)
            .withElementId("error-boundary-event")
            .getFirst();

    assertThat(
            RecordingExporter.records()
                .limitToProcessInstance(processInstanceKey)
                .variableRecords()
                .collect(Collectors.toList()))
        .extracting(
            r -> r.getValue().getName(),
            r -> r.getValue().getValue(),
            r -> r.getValue().getScopeKey(),
            Record::getIntent)
        .describedAs(
            "The variables are created at the error catch event, and with an output mapping to created at the process instance.")
        .containsExactly(
            tuple("foo", "\"bar\"", errorEvent.getKey(), VariableIntent.CREATED),
            tuple("output", "\"bar\"", processInstanceKey, VariableIntent.CREATED));
  }

  @Test
  public void shouldThrowErrorWithVariablesWithEventSubProcess() {
    // given
    final var process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .eventSubProcess(
                "error-event-subprocess",
                s ->
                    s.startEvent("error-start-event")
                        .error(ERROR_CODE)
                        .interrupting(true)
                        .endEvent())
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(jobType))
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).deploy();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(jobType)
        .withErrorCode(ERROR_CODE)
        .withVariables("{'foo':'bar'}")
        .throwError();

    // then
    assertThat(
            RecordingExporter.records()
                .limitToProcessInstance(processInstanceKey)
                .variableRecords()
                .collect(Collectors.toList()))
        .filteredOn(r -> r.getValue().getName().equals("foo"))
        .extracting(
            r -> r.getValue().getName(),
            r -> r.getValue().getValue(),
            r -> r.getValue().getScopeKey(),
            Record::getIntent)
        .describedAs("With event sub process the variables are created at the process instance")
        .containsExactly(tuple("foo", "\"bar\"", processInstanceKey, VariableIntent.CREATED));
  }

  @Test
  public void shouldTruncateErrorMessage() {
    // given
    final var job = ENGINE.createJob(jobType, PROCESS_ID);
    final String exceedingErrorMessage = "*".repeat(DEFAULT_MAX_ERROR_MESSAGE_SIZE + 1);

    // when
    final Record<JobRecordValue> failedRecord =
        ENGINE.job().withKey(job.getKey()).withErrorMessage(exceedingErrorMessage).throwError();

    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(CREATED).getFirst();

    // then
    Assertions.assertThat(failedRecord).hasRecordType(RecordType.EVENT).hasIntent(ERROR_THROWN);

    final String expectedJobMessage = "*".repeat(DEFAULT_MAX_ERROR_MESSAGE_SIZE).concat("...");
    assertThat(failedRecord.getValue().getErrorMessage()).isEqualTo(expectedJobMessage);

    final String expectedIncidentMessage =
        "Expected to throw an error event with the code '' with message '"
            + expectedJobMessage
            + "', but it was not caught. No error events are available in the scope.";
    assertThat(incident.getValue().getErrorMessage()).isEqualTo(expectedIncidentMessage);
  }

  @Test
  public void shouldNotTruncateErrorMessage() {
    // given
    final var job = ENGINE.createJob(jobType, PROCESS_ID);
    final String errorMessage = "*".repeat(DEFAULT_MAX_ERROR_MESSAGE_SIZE);

    // when
    final Record<JobRecordValue> failedRecord =
        ENGINE.job().withKey(job.getKey()).withErrorMessage(errorMessage).throwError();

    final var incident = RecordingExporter.incidentRecords(CREATED).getFirst();

    // then
    Assertions.assertThat(failedRecord).hasRecordType(RecordType.EVENT).hasIntent(ERROR_THROWN);

    final String expectedJobMessage = "*".repeat(DEFAULT_MAX_ERROR_MESSAGE_SIZE);
    assertThat(failedRecord.getValue().getErrorMessage()).isEqualTo(expectedJobMessage);

    final String expectedIncidentMessage =
        "Expected to throw an error event with the code '' with message '"
            + expectedJobMessage
            + "', but it was not caught. No error events are available in the scope.";
    assertThat(incident.getValue().getErrorMessage()).isEqualTo(expectedIncidentMessage);
  }
}
