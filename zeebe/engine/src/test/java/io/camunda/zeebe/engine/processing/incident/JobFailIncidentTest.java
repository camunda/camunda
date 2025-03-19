/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.incident;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.client.JobClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobBatchIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.collection.Maps;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class JobFailIncidentTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  private static final String JOB_TYPE = "test";
  private static final BpmnModelInstance PROCESS_INPUT_MAPPING =
      Bpmn.createExecutableProcess("process")
          .startEvent()
          .serviceTask(
              "failingTask", t -> t.zeebeJobType(JOB_TYPE).zeebeInputExpression("foo", "foo"))
          .done();
  private static final Map<String, Object> VARIABLES = Maps.of(entry("foo", "bar"));
  private static long processDefinitionKey;

  @Rule
  public RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private long processInstanceKey;

  @BeforeClass
  public static void init() {
    processDefinitionKey =
        ENGINE
            .deployment()
            .withXmlResource(PROCESS_INPUT_MAPPING)
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .get(0)
            .getProcessDefinitionKey();
  }

  @Before
  public void beforeTest() {
    processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId("process").withVariables(VARIABLES).create();

    RecordingExporter.jobRecords()
        .withProcessInstanceKey(processInstanceKey)
        .withIntent(JobIntent.CREATED)
        .getFirst();

    ENGINE.jobs().withType(JOB_TYPE).withMaxJobsToActivate(1).activate();
  }

  // regression test for https://github.com/camunda/camunda/issues/6516
  @Test
  public void shouldCreateIncidentWithANewKey() {
    // given
    final Record<JobRecordValue> failedEvent =
        ENGINE.job().withType(JOB_TYPE).ofInstance(processInstanceKey).withRetries(0).fail();
    final Record<IncidentRecordValue> firstIncident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withJobKey(failedEvent.getKey())
            .getFirst();

    ENGINE.job().ofInstance(processInstanceKey).withType(JOB_TYPE).withRetries(1).updateRetries();
    ENGINE.incident().ofInstance(processInstanceKey).withKey(firstIncident.getKey()).resolve();

    // when
    ENGINE.job().withType(JOB_TYPE).ofInstance(processInstanceKey).withRetries(0).fail();
    final Record<IncidentRecordValue> nextIncident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .filter(r -> r.getPosition() > firstIncident.getPosition())
            .withJobKey(failedEvent.getKey())
            .getFirst();

    // then
    assertThat(nextIncident.getKey()).isGreaterThan(firstIncident.getKey());
  }

  @Test
  public void shouldCreateIncidentIfJobHasNoRetriesLeft() {
    // given

    // when
    final Record<JobRecordValue> failedEvent =
        ENGINE.job().withType(JOB_TYPE).ofInstance(processInstanceKey).fail();

    // then
    final Record activityEvent =
        RecordingExporter.processInstanceRecords()
            .withElementId("failingTask")
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .getFirst();

    final Record<IncidentRecordValue> incidentEvent =
        RecordingExporter.incidentRecords()
            .withIntent(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    assertThat(incidentEvent.getKey()).isGreaterThan(0);
    assertThat(incidentEvent.getSourceRecordPosition())
        .isEqualTo(failedEvent.getSourceRecordPosition());

    assertThat(incidentEvent.getValue())
        .hasErrorType(ErrorType.JOB_NO_RETRIES)
        .hasErrorMessage("No more retries left.")
        .hasBpmnProcessId("process")
        .hasProcessDefinitionKey(processDefinitionKey)
        .hasProcessInstanceKey(processInstanceKey)
        .hasElementId("failingTask")
        .hasElementInstanceKey(activityEvent.getKey())
        .hasVariableScopeKey(activityEvent.getKey());
  }

  @Test
  public void shouldCreateIncidentIfJobHasNoRetriesLeftWithCustomTenant() {
    // given
    final String processId = "test-process";
    final String tenantId = Strings.newRandomValidIdentityId();
    final String username = Strings.newRandomValidIdentityId();
    ENGINE.tenant().newTenant().withTenantId(tenantId).create();
    ENGINE.user().newUser(username).create();
    ENGINE
        .tenant()
        .addEntity(tenantId)
        .withEntityId(username)
        .withEntityType(EntityType.USER)
        .add();
    final Record<JobRecordValue> jobRecord =
        ENGINE.createJob(JOB_TYPE, processId, Collections.emptyMap(), tenantId);
    final long piKey = jobRecord.getValue().getProcessInstanceKey();

    // when
    ENGINE.job().withType(JOB_TYPE).withRetries(0).ofInstance(piKey).fail(username);

    // then
    final Record<IncidentRecordValue> incidentEvent =
        RecordingExporter.incidentRecords()
            .withIntent(IncidentIntent.CREATED)
            .withProcessInstanceKey(piKey)
            .getFirst();

    assertThat(incidentEvent.getValue()).hasTenantId(tenantId);
  }

  @Test
  public void shouldCreateIncidentWithJobErrorMessage() {
    // given

    // when
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(JOB_TYPE)
        .withErrorMessage("failed job")
        .fail();

    // then
    final Record activityEvent =
        RecordingExporter.processInstanceRecords()
            .withElementId("failingTask")
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    final Record failEvent =
        RecordingExporter.jobRecords()
            .withIntent(JobIntent.FAILED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final Record<IncidentRecordValue> incidentEvent =
        RecordingExporter.incidentRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withIntent(IncidentIntent.CREATED)
            .getFirst();

    assertThat(incidentEvent.getKey()).isGreaterThan(0);
    assertThat(incidentEvent.getSourceRecordPosition())
        .isEqualTo(failEvent.getSourceRecordPosition());

    assertThat(incidentEvent.getValue())
        .hasErrorType(ErrorType.JOB_NO_RETRIES)
        .hasErrorMessage("failed job")
        .hasBpmnProcessId("process")
        .hasProcessDefinitionKey(processDefinitionKey)
        .hasProcessInstanceKey(processInstanceKey)
        .hasElementId("failingTask")
        .hasElementInstanceKey(activityEvent.getKey())
        .hasVariableScopeKey(activityEvent.getKey());
  }

  @Test
  public void shouldIncidentContainLastFailedJobErrorMessage() {
    // given

    // when
    final JobClient jobClient = ENGINE.job().ofInstance(processInstanceKey).withType(JOB_TYPE);

    jobClient.withRetries(1).withErrorMessage("first message").fail();

    ENGINE.jobs().withType(JOB_TYPE).activate();
    jobClient.withRetries(0).withErrorMessage("second message").fail();

    // then
    final Record activityEvent =
        RecordingExporter.processInstanceRecords()
            .withElementId("failingTask")
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final Record<IncidentRecordValue> incidentEvent =
        RecordingExporter.incidentRecords()
            .withIntent(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    assertThat(incidentEvent.getValue())
        .hasErrorType(ErrorType.JOB_NO_RETRIES)
        .hasErrorMessage("second message")
        .hasBpmnProcessId("process")
        .hasProcessDefinitionKey(processDefinitionKey)
        .hasProcessInstanceKey(processInstanceKey)
        .hasElementId("failingTask")
        .hasElementInstanceKey(activityEvent.getKey())
        .hasVariableScopeKey(activityEvent.getKey());
  }

  @Test
  public void shouldResolveIncidentIfJobRetriesIncreased() {
    // given
    ENGINE.job().withType(JOB_TYPE).ofInstance(processInstanceKey).fail();
    final Record<IncidentRecordValue> incidentCreatedEvent =
        RecordingExporter.incidentRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withIntent(IncidentIntent.CREATED)
            .getFirst();

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType(JOB_TYPE).withRetries(1).updateRetries();
    final Record<IncidentRecordValue> resolvedIncident =
        ENGINE
            .incident()
            .ofInstance(processInstanceKey)
            .withKey(incidentCreatedEvent.getKey())
            .resolve();
    ENGINE.jobs().withType(JOB_TYPE).activate();

    // then
    final Record jobEvent =
        RecordingExporter.jobRecords()
            .withIntent(JobIntent.FAILED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final Record activityEvent =
        RecordingExporter.processInstanceRecords()
            .withElementId("failingTask")
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final Record incidentEvent =
        RecordingExporter.incidentRecords()
            .withIntent(IncidentIntent.RESOLVE)
            .withRecordKey(incidentCreatedEvent.getKey())
            .getFirst();

    final long lastPos = incidentEvent.getPosition();

    assertThat(resolvedIncident.getKey()).isGreaterThan(0);
    assertThat(resolvedIncident.getSourceRecordPosition()).isEqualTo(lastPos);

    assertThat(resolvedIncident.getValue())
        .hasErrorType(ErrorType.JOB_NO_RETRIES)
        .hasErrorMessage("No more retries left.")
        .hasBpmnProcessId("process")
        .hasProcessDefinitionKey(processDefinitionKey)
        .hasProcessInstanceKey(processInstanceKey)
        .hasElementId("failingTask")
        .hasElementInstanceKey(activityEvent.getKey())
        .hasVariableScopeKey(activityEvent.getKey());

    // and the job is activated again
    final var batchActivations =
        RecordingExporter.jobBatchRecords(JobBatchIntent.ACTIVATED)
            .filter(
                jobBatchRecordValueRecord ->
                    jobBatchRecordValueRecord.getValue().getJobs().stream()
                        .anyMatch(
                            jobRecordValue ->
                                jobRecordValue.getProcessInstanceKey() == processInstanceKey))
            .limit(2)
            .collect(Collectors.toList());
    assertThat(batchActivations).hasSize(2);
    final var secondActivationJobValue = batchActivations.get(1).getValue().getJobs().get(0);
    final var secondActivationJobKey = batchActivations.get(1).getValue().getJobKeys().get(0);

    assertThat(secondActivationJobKey).isEqualTo(jobEvent.getKey());
    assertThat(secondActivationJobValue).hasRetries(1);

    // and the job lifecycle is correct
    final List<Record> jobEvents =
        RecordingExporter.jobRecords()
            .filter(
                r ->
                    r.getKey() == jobEvent.getKey()
                        || r.getValue().getProcessInstanceKey() == processInstanceKey)
            .limit(5)
            .collect(Collectors.toList());

    assertThat(jobEvents)
        .extracting(Record::getRecordType, Record::getValueType, Record::getIntent)
        .containsExactly(
            tuple(RecordType.EVENT, ValueType.JOB, JobIntent.CREATED),
            tuple(RecordType.COMMAND, ValueType.JOB, JobIntent.FAIL),
            tuple(RecordType.EVENT, ValueType.JOB, JobIntent.FAILED),
            tuple(RecordType.COMMAND, ValueType.JOB, JobIntent.UPDATE_RETRIES),
            tuple(RecordType.EVENT, ValueType.JOB, JobIntent.RETRIES_UPDATED));
  }

  @Test
  public void shouldResolveIncidentWithCustomTenant() {
    // given
    final String processId = "test-process";
    final String tenantId = Strings.newRandomValidIdentityId();
    final String username = Strings.newRandomValidIdentityId();
    ENGINE.tenant().newTenant().withTenantId(tenantId).create();
    ENGINE.user().newUser(username).create();
    ENGINE
        .tenant()
        .addEntity(tenantId)
        .withEntityId(username)
        .withEntityType(EntityType.USER)
        .add();
    final Record<JobRecordValue> jobRecord =
        ENGINE.createJob(JOB_TYPE, processId, Collections.emptyMap(), tenantId);
    final long piKey = jobRecord.getValue().getProcessInstanceKey();
    ENGINE.job().withType(JOB_TYPE).withRetries(0).ofInstance(piKey).fail(username);

    // when
    ENGINE.job().ofInstance(piKey).withType(JOB_TYPE).withRetries(1).updateRetries(username);

    final Record<IncidentRecordValue> resolvedIncident =
        ENGINE.incident().ofInstance(piKey).resolve(username);

    // then
    assertThat(resolvedIncident.getValue()).hasTenantId(tenantId);
    assertThat(resolvedIncident).hasIntent(IncidentIntent.RESOLVED);
  }

  @Test
  public void shouldRejectResolvingIncidentWithUnauthorizedTenant() {
    // given
    final String processId = "test-process";
    final String tenantId = Strings.newRandomValidIdentityId();
    final String unauthorizedTenantId = Strings.newRandomValidIdentityId();
    final String authorizedUser = Strings.newRandomValidIdentityId();
    final String unauthorizedUser = Strings.newRandomValidIdentityId();
    ENGINE.tenant().newTenant().withTenantId(tenantId).create();
    ENGINE.tenant().newTenant().withTenantId(unauthorizedTenantId).create();
    ENGINE.user().newUser(authorizedUser).create();
    ENGINE
        .tenant()
        .addEntity(tenantId)
        .withEntityId(authorizedUser)
        .withEntityType(EntityType.USER)
        .add();
    ENGINE.user().newUser(unauthorizedUser).create();
    ENGINE
        .tenant()
        .addEntity(unauthorizedTenantId)
        .withEntityId(unauthorizedUser)
        .withEntityType(EntityType.USER)
        .add();
    final Record<JobRecordValue> jobRecord =
        ENGINE.createJob(JOB_TYPE, processId, Collections.emptyMap(), tenantId);
    final long piKey = jobRecord.getValue().getProcessInstanceKey();
    ENGINE.job().withType(JOB_TYPE).withRetries(0).ofInstance(piKey).fail(authorizedUser);

    // when
    final Record<IncidentRecordValue> resolvedIncident =
        ENGINE.incident().ofInstance(piKey).expectRejection().resolve(unauthorizedUser);

    // then
    assertThat(resolvedIncident).hasRejectionType(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldDeleteIncidentIfJobIsCanceled() {
    // given
    ENGINE.job().withType(JOB_TYPE).ofInstance(processInstanceKey).fail();

    final Record<IncidentRecordValue> incidentCreatedEvent =
        RecordingExporter.incidentRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withIntent(IncidentIntent.CREATED)
            .getFirst();

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).cancel();

    // then
    final Record<ProcessInstanceRecordValue> terminateTaskCommand =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("failingTask")
            .withIntent(ProcessInstanceIntent.TERMINATE_ELEMENT)
            .getFirst();

    final Record<JobRecordValue> jobCancelled =
        RecordingExporter.jobRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withIntent(JobIntent.CANCELED)
            .getFirst();

    final Record<IncidentRecordValue> resolvedIncidentEvent =
        RecordingExporter.incidentRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withIntent(IncidentIntent.RESOLVED)
            .getFirst();

    assertThat(resolvedIncidentEvent.getKey()).isEqualTo(incidentCreatedEvent.getKey());

    assertThat(resolvedIncidentEvent.getValue())
        .hasErrorType(ErrorType.JOB_NO_RETRIES)
        .hasErrorMessage("No more retries left.")
        .hasBpmnProcessId("process")
        .hasProcessDefinitionKey(processDefinitionKey)
        .hasProcessInstanceKey(processInstanceKey)
        .hasElementId("failingTask")
        .hasVariableScopeKey(terminateTaskCommand.getKey());
  }

  @Test
  public void shouldRejectResolveIncidentIfJobRetriesStillZero() {
    // given
    final Record<JobRecordValue> failedJob =
        ENGINE.job().withType(JOB_TYPE).ofInstance(processInstanceKey).withRetries(0).fail();

    final Record<IncidentRecordValue> incidentCreatedEvent =
        RecordingExporter.incidentRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withIntent(IncidentIntent.CREATED)
            .getFirst();

    // when
    final Record<IncidentRecordValue> resolvedIncidentRejection =
        ENGINE
            .incident()
            .ofInstance(processInstanceKey)
            .withKey(incidentCreatedEvent.getKey())
            .expectRejection()
            .resolve();

    // then
    assertThat(resolvedIncidentRejection)
        .hasRejectionType(RejectionType.INVALID_STATE)
        .hasRejectionReason(
            String.format(
                "Expected to resolve incident with key '%d', but job with key '%d' has no retries left. Please update the job retries and retry resolving the incident",
                incidentCreatedEvent.getKey(), failedJob.getKey()));

    final var reactivatedJobs = ENGINE.jobs().withType(JOB_TYPE).activate();
    assertThat(reactivatedJobs.getValue().getJobs())
        .describedAs("Expected job not activatable because no retries left")
        .isEmpty();
  }
}
