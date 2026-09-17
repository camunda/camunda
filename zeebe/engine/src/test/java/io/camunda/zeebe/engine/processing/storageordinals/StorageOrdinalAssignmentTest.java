/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.storageordinals;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.record.value.secretreference.SecretReferenceRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessEventIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceBatchIntent;
import io.camunda.zeebe.protocol.record.intent.SecretReferenceIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.ProcessEventRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;

/**
 * Pins the storage ordinal assignment end to end: with archiverless mode enabled the engine selects
 * the fixed provider, stamps the configured ordinal on the root process instance at creation, and
 * all follow-up records of the instance inherit that ordinal.
 */
public final class StorageOrdinalAssignmentTest {

  private static final int FIXED_ORDINAL = 1234;

  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition()
          .withEngineConfig(
              c -> c.setArchiverlessEnabled(true).setFixedStorageOrdinal(FIXED_ORDINAL));

  @Test
  public void shouldAssignConfiguredOrdinalToProcessInstanceRecords() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("pi-records-process").startEvent().endEvent().done())
        .deploy();

    // when
    final long processInstanceKey =
        engine.processInstance().ofBpmnProcessId("pi-records-process").create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .isNotEmpty()
        .extracting(record -> record.getValue().getStorageOrdinal())
        .containsOnly(FIXED_ORDINAL);
  }

  @Test
  public void shouldAssignConfiguredOrdinalToVariableRecords() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("variable-records-process").startEvent().endEvent().done())
        .deploy();

    // when
    final long processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId("variable-records-process")
            .withVariable("ordinalVariable", 1)
            .create();

    // then
    final var variableCreated =
        RecordingExporter.variableRecords(VariableIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withName("ordinalVariable")
            .getFirst();
    assertThat(variableCreated.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);
  }

  @Test
  public void shouldAssignConfiguredOrdinalToJobRecords() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("job-records-process")
                .startEvent()
                .serviceTask("service-task", t -> t.zeebeJobType("ordinal-job"))
                .endEvent()
                .done())
        .deploy();

    // when
    final long processInstanceKey =
        engine.processInstance().ofBpmnProcessId("job-records-process").create();

    // then
    final var jobCreated =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(jobCreated.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);
  }

  @Test
  public void shouldAssignConfiguredOrdinalToUserTaskRecords() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("user-task-records-process")
                .startEvent()
                .userTask("user-task")
                .zeebeUserTask()
                .endEvent()
                .done())
        .deploy();

    // when
    final long processInstanceKey =
        engine.processInstance().ofBpmnProcessId("user-task-records-process").create();

    // then
    final var userTaskCreated =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(userTaskCreated.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);
  }

  @Test
  public void shouldAssignConfiguredOrdinalToProcessEventRecords() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("process-event-records-process")
                .startEvent()
                .intermediateCatchEvent("catch-event")
                .message(m -> m.name("ordinal-message").zeebeCorrelationKeyExpression("key"))
                .endEvent()
                .done())
        .deploy();
    engine
        .processInstance()
        .ofBpmnProcessId("process-event-records-process")
        .withVariable("key", "correlation-key-1")
        .create();

    // when
    engine.message().withName("ordinal-message").withCorrelationKey("correlation-key-1").publish();

    // then
    final var records =
        RecordingExporter.records()
            .limit(
                r ->
                    r.getValueType() == ValueType.PROCESS_EVENT
                        && r.getIntent() == ProcessEventIntent.TRIGGERING)
            .asList();
    final var processEventTriggering = records.get(records.size() - 1);
    assertThat(((ProcessEventRecordValue) processEventTriggering.getValue()).getStorageOrdinal())
        .isEqualTo(FIXED_ORDINAL);
  }

  @Test
  public void shouldAssignConfiguredOrdinalToProcessInstanceBatchRecords() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("batch-records-process")
                .startEvent()
                .serviceTask(
                    "multi-instance-task",
                    t ->
                        t.zeebeJobType("ordinal-multi-instance")
                            .multiInstance(
                                m ->
                                    m.parallel()
                                        .zeebeInputCollectionExpression("items")
                                        .zeebeInputElement("item")))
                .endEvent()
                .done())
        .deploy();

    // when
    final long processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId("batch-records-process")
            .withVariable("items", List.of(1, 2, 3))
            .create();

    // then
    final var batchActivate =
        RecordingExporter.processInstanceBatchRecords(ProcessInstanceBatchIntent.ACTIVATE)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(batchActivate.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);
  }

  @Test
  public void shouldAssignConfiguredOrdinalToDecisionEvaluationRecords() {
    // given
    engine
        .deployment()
        .withXmlClasspathResource("/dmn/drg-force-user.dmn")
        .withXmlResource(
            Bpmn.createExecutableProcess("decision-records-process")
                .startEvent()
                .businessRuleTask(
                    "business-rule-task",
                    t -> t.zeebeCalledDecisionId("jedi_or_sith").zeebeResultVariable("result"))
                .endEvent()
                .done())
        .deploy();

    // when
    final long processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId("decision-records-process")
            .withVariable("lightsaberColor", "blue")
            .create();

    // then
    final var decisionEvaluated =
        RecordingExporter.decisionEvaluationRecords()
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(decisionEvaluated.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);
  }

  @Test
  public void shouldAssignConfiguredOrdinalToIncidentRecordsOfAJobWithoutRetries() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("job-fail-incident-process")
                .startEvent()
                .serviceTask("service-task", t -> t.zeebeJobType("ordinal-failing-job"))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey =
        engine.processInstance().ofBpmnProcessId("job-fail-incident-process").create();
    engine.jobs().withType("ordinal-failing-job").withMaxJobsToActivate(1).activate();

    // when
    engine
        .job()
        .ofInstance(processInstanceKey)
        .withType("ordinal-failing-job")
        .withRetries(0)
        .fail();

    // then
    final var incidentCreated =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(incidentCreated.getValue().getErrorType()).isEqualTo(ErrorType.JOB_NO_RETRIES);
    assertThat(incidentCreated.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);
  }

  @Test
  public void shouldAssignConfiguredOrdinalToIncidentRecordsOfAnUncaughtJobError() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("job-error-incident-process")
                .startEvent()
                .serviceTask("service-task", t -> t.zeebeJobType("ordinal-error-job"))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey =
        engine.processInstance().ofBpmnProcessId("job-error-incident-process").create();

    // when - the thrown error has no matching catch event
    engine
        .job()
        .ofInstance(processInstanceKey)
        .withType("ordinal-error-job")
        .withErrorCode("uncaught-error")
        .throwError();

    // then
    final var incidentCreated =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(incidentCreated.getValue().getErrorType())
        .isEqualTo(ErrorType.UNHANDLED_ERROR_EVENT);
    assertThat(incidentCreated.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);
  }

  @Test
  public void shouldAssignConfiguredOrdinalToIncidentRecordsOfAFailedSecretResolution() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("secret-incident-process")
                .startEvent()
                .serviceTask("service-task", t -> t.zeebeJobType("ordinal-secret-job"))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey =
        engine.processInstance().ofBpmnProcessId("secret-incident-process").create();
    final long jobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getKey();

    final var resolutionRequested =
        new SecretReferenceRecord()
            .setStoreId("ordinal-store")
            .setSecretReference("ordinal-secret");
    resolutionRequested.addJobKey(jobKey);
    final var batchCreateIncidents =
        new SecretReferenceRecord()
            .setStoreId("ordinal-store")
            .setSecretReference("ordinal-secret");
    batchCreateIncidents.addJobKey(jobKey);

    // when - the job is seeded as waiting on the secret reference and the drain is processed
    engine.stop();
    engine.writeRecords(
        RecordToWrite.event()
            .secretReference(SecretReferenceIntent.RESOLUTION_REQUESTED, resolutionRequested),
        RecordToWrite.command()
            .secretReference(SecretReferenceIntent.BATCH_CREATE_INCIDENTS, batchCreateIncidents));
    // starting the engine re-exports the whole partition log, so clear the previously seen records
    RecordingExporter.reset();
    engine.start();

    // then
    final var incidentCreated =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withErrorType(ErrorType.SECRET_RESOLUTION_ERROR)
            .withJobKey(jobKey)
            .getFirst();
    assertThat(incidentCreated.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);
  }
}
