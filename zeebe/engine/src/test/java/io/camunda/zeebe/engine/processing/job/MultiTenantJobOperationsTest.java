/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import static io.camunda.zeebe.protocol.record.intent.JobIntent.ERROR_THROWN;
import static io.camunda.zeebe.protocol.record.intent.JobIntent.FAILED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.JobBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class MultiTenantJobOperationsTest {

  @ClassRule
  public static final EngineRule ENGINE =
      EngineRule.singlePartition()
          .withSecurityConfig(
              config -> {
                config.getAuthorizations().setEnabled(true);
                config.getMultiTenancy().setChecksEnabled(true);
              });

  private static final int NEW_RETRIES = 20;
  private static final String PROCESS_ID = "process";
  private static final String BOUNDARY_PROCESS_ID = "boundary_process";
  private static final String BOUNDARY_JOB_TYPE = "test";
  private static final String ERROR_CODE = "error";

  private static String jobType;
  private static String username;
  private static String tenantId;

  private static final BpmnModelInstance BOUNDARY_EVENT_PROCESS =
      Bpmn.createExecutableProcess(BOUNDARY_PROCESS_ID)
          .startEvent()
          .serviceTask("task", t -> t.zeebeJobType(BOUNDARY_JOB_TYPE))
          .boundaryEvent("error", b -> b.error(ERROR_CODE))
          .endEvent()
          .done();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @BeforeClass
  public static void setUp() {
    tenantId = UUID.randomUUID().toString();
    username = UUID.randomUUID().toString();
    final var user = ENGINE.user().newUser(username).create().getValue();
    final var username = user.getUsername();
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
        .withResourceMatcher(AuthorizationResourceMatcher.ID)
        .withResourceId(PROCESS_ID)
        .withResourceType(AuthorizationResourceType.PROCESS_DEFINITION)
        .withOwnerId(username)
        .withOwnerType(AuthorizationOwnerType.USER)
        .create();

    ENGINE
        .authorization()
        .newAuthorization()
        .withPermissions(PermissionType.UPDATE_PROCESS_INSTANCE)
        .withResourceMatcher(AuthorizationResourceMatcher.ID)
        .withResourceId(BOUNDARY_PROCESS_ID)
        .withResourceType(AuthorizationResourceType.PROCESS_DEFINITION)
        .withOwnerId(username)
        .withOwnerType(AuthorizationOwnerType.USER)
        .create();
  }

  @Before
  public void setup() {
    jobType = Strings.newRandomValidBpmnId();
  }

  @Test
  public void shouldCompleteJobForCustomTenant() {
    // given
    ENGINE.createJob(jobType, PROCESS_ID, Collections.emptyMap(), tenantId);

    final io.camunda.zeebe.protocol.record.Record<JobBatchRecordValue> batchRecord =
        ENGINE.jobs().withType(jobType).withTenantId(tenantId).activate(username);

    // when
    final io.camunda.zeebe.protocol.record.Record<JobRecordValue> jobCompletedRecord =
        ENGINE.job().withKey(batchRecord.getValue().getJobKeys().get(0)).complete(username);

    // then
    final JobRecordValue recordValue = jobCompletedRecord.getValue();

    Assertions.assertThat(jobCompletedRecord)
        .hasRecordType(RecordType.EVENT)
        .hasIntent(JobIntent.COMPLETED);

    Assertions.assertThat(recordValue).hasTenantId(tenantId);
  }

  @Test
  public void shouldRejectCompletionIfTenantIsUnauthorized() {
    // given
    final String falseTenantId = UUID.randomUUID().toString();
    ENGINE.createJob(jobType, PROCESS_ID, Collections.emptyMap(), tenantId);

    final io.camunda.zeebe.protocol.record.Record<JobBatchRecordValue> batchRecord =
        ENGINE.jobs().withType(jobType).withTenantId(tenantId).activate(username);

    // when
    final Record<JobRecordValue> jobRecord =
        ENGINE
            .job()
            .withKey(batchRecord.getValue().getJobKeys().get(0))
            .withAuthorizedTenantIds(falseTenantId)
            .expectRejection()
            .complete();

    // then
    Assertions.assertThat(jobRecord).hasRejectionType(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldRejectThrowErrorIfTenantIsUnauthorized() {
    // given
    final String falseTenantId = "foo";
    final var job = ENGINE.createJob(jobType, PROCESS_ID, Collections.emptyMap(), tenantId);
    ENGINE.jobs().withType(jobType).withTenantId(tenantId).activate(username);

    // when
    final Record<JobRecordValue> result =
        ENGINE
            .job()
            .withKey(job.getKey())
            .withErrorCode("error")
            .withAuthorizedTenantIds(falseTenantId)
            .throwError();

    // then
    Assertions.assertThat(result).hasRejectionType(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldThrowErrorForCustomTenant() {
    // given
    final var job = ENGINE.createJob(jobType, PROCESS_ID, Collections.emptyMap(), tenantId);

    // when
    final Record<JobRecordValue> result =
        ENGINE
            .job()
            .withKey(job.getKey())
            .withErrorCode("error")
            .withErrorMessage("error-message")
            .throwError(username);

    // then
    Assertions.assertThat(result).hasRecordType(RecordType.EVENT).hasIntent(ERROR_THROWN);
    Assertions.assertThat(result.getValue()).hasErrorCode("error").hasErrorMessage("error-message");
    Assertions.assertThat(result.getValue()).hasTenantId(tenantId);
  }

  @Test
  public void shouldUpdateJobTimeoutForCustomTenant() {
    // given
    ENGINE.createJob(jobType, PROCESS_ID, Collections.emptyMap(), tenantId);

    final var batchRecord =
        ENGINE
            .jobs()
            .withType(jobType)
            .withTimeout(Duration.ofMinutes(5).toMillis())
            .withTenantId(tenantId)
            .activate(username);

    final JobRecordValue job = batchRecord.getValue().getJobs().get(0);
    final long jobKey = batchRecord.getValue().getJobKeys().get(0);
    final long timeout = Duration.ofMinutes(10).toMillis();

    // when
    final Record<JobRecordValue> updatedRecord =
        ENGINE.job().withKey(jobKey).withTimeout(timeout).updateTimeout(username);

    // then
    Assertions.assertThat(updatedRecord.getValue()).hasTenantId(tenantId);
    assertJobDeadline(updatedRecord, jobKey, job, timeout);
  }

  @Test
  public void shouldUpdateRetriesForCustomTenant() {
    // given
    ENGINE.createJob(jobType, PROCESS_ID, Collections.emptyMap(), tenantId);
    final Record<JobBatchRecordValue> batchRecord =
        ENGINE.jobs().withType(jobType).withTenantId(tenantId).activate(username);
    final long jobKey = batchRecord.getValue().getJobKeys().get(0);

    // when
    final Record<JobRecordValue> updatedRecord =
        ENGINE.job().withKey(jobKey).withRetries(NEW_RETRIES).updateRetries(username);

    // then
    Assertions.assertThat(updatedRecord.getValue()).hasTenantId(tenantId);
  }

  @Test
  public void shouldRejectUpdateRetriesIfTenantIsUnauthorized() {
    // given
    final String falseTenantId = "foo";
    ENGINE.createJob(jobType, PROCESS_ID, Collections.emptyMap(), tenantId);
    final Record<JobBatchRecordValue> batchRecord =
        ENGINE.jobs().withType(jobType).withTenantId(tenantId).activate(username);
    final long jobKey = batchRecord.getValue().getJobKeys().get(0);

    // when
    final Record<JobRecordValue> jobRecord =
        ENGINE
            .job()
            .withKey(jobKey)
            .withRetries(NEW_RETRIES)
            .withAuthorizedTenantIds(falseTenantId)
            .expectRejection()
            .updateRetries();

    // then
    Assertions.assertThat(jobRecord).hasRejectionType(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldRejectFailIfTenantIsUnauthorized() {
    // given
    final String falseTenantId = UUID.randomUUID().toString();
    ENGINE.createJob(jobType, PROCESS_ID, Collections.emptyMap(), tenantId);

    final Record<JobBatchRecordValue> batchRecord =
        ENGINE.jobs().withType(jobType).withTenantId(tenantId).activate(username);
    final long jobKey = batchRecord.getValue().getJobKeys().get(0);

    // when
    final Record<JobRecordValue> jobRecord =
        ENGINE
            .job()
            .withKey(jobKey)
            .withRetries(3)
            .withAuthorizedTenantIds(falseTenantId)
            .expectRejection()
            .fail();

    // then
    Assertions.assertThat(jobRecord).hasRejectionType(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldFailForCustomTenant() {
    // given
    ENGINE.createJob(jobType, PROCESS_ID, Collections.emptyMap(), tenantId);

    final Record<JobBatchRecordValue> batchRecord =
        ENGINE.jobs().withType(jobType).withTenantId(tenantId).activate(username);

    final JobRecordValue job = batchRecord.getValue().getJobs().get(0);
    final long jobKey = batchRecord.getValue().getJobKeys().get(0);
    final int retries = 23;

    // when
    final Record<JobRecordValue> failRecord =
        ENGINE
            .job()
            .withKey(jobKey)
            .ofInstance(job.getProcessInstanceKey())
            .withRetries(retries)
            .fail(username);

    // then
    Assertions.assertThat(failRecord).hasRecordType(RecordType.EVENT).hasIntent(FAILED);
    Assertions.assertThat(failRecord.getValue()).hasTenantId(tenantId);
  }

  @Test
  public void shouldCreateIncidentWithCustomTenant() {
    // given
    ENGINE.deployment().withXmlResource(BOUNDARY_EVENT_PROCESS).withTenantId(tenantId).deploy();
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(BOUNDARY_PROCESS_ID)
            .withTenantId(tenantId)
            .create();

    // when
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(BOUNDARY_JOB_TYPE)
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
  public void shouldRejectResolvingIncidentWithUnauthorizedTenant() {
    // given
    final String unauthorizedTenantId = Strings.newRandomValidIdentityId();
    final String unauthorizedUser = Strings.newRandomValidIdentityId();
    ENGINE.tenant().newTenant().withTenantId(unauthorizedTenantId).create();
    ENGINE.user().newUser(unauthorizedUser).create();
    ENGINE
        .tenant()
        .addEntity(unauthorizedTenantId)
        .withEntityId(unauthorizedUser)
        .withEntityType(EntityType.USER)
        .add();
    final Record<JobRecordValue> jobRecord =
        ENGINE.createJob(jobType, PROCESS_ID, Collections.emptyMap(), tenantId);
    final long piKey = jobRecord.getValue().getProcessInstanceKey();
    ENGINE.job().withType(jobType).withRetries(0).ofInstance(piKey).fail(username);

    // when
    final Record<IncidentRecordValue> resolvedIncident =
        ENGINE.incident().ofInstance(piKey).expectRejection().resolve(unauthorizedUser);

    // then
    Assertions.assertThat(resolvedIncident).hasRejectionType(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldCreateIncidentIfJobHasNoRetriesLeftWithCustomTenant() {
    // given
    final Record<JobRecordValue> jobRecord =
        ENGINE.createJob(jobType, PROCESS_ID, Collections.emptyMap(), tenantId);
    final long piKey = jobRecord.getValue().getProcessInstanceKey();

    // when
    ENGINE.job().withType(jobType).withRetries(0).ofInstance(piKey).fail(username);

    // then
    final Record<IncidentRecordValue> incidentEvent =
        RecordingExporter.incidentRecords()
            .withIntent(IncidentIntent.CREATED)
            .withProcessInstanceKey(piKey)
            .getFirst();

    Assertions.assertThat(incidentEvent.getValue()).hasTenantId(tenantId);
  }

  @Test
  public void shouldResolveIncidentWithCustomTenant() {
    // given
    final Record<JobRecordValue> jobRecord =
        ENGINE.createJob(jobType, PROCESS_ID, Collections.emptyMap(), tenantId);
    final long piKey = jobRecord.getValue().getProcessInstanceKey();
    ENGINE.job().withType(jobType).withRetries(0).ofInstance(piKey).fail(username);

    // when
    ENGINE.job().ofInstance(piKey).withType(jobType).withRetries(1).updateRetries(username);

    final Record<IncidentRecordValue> resolvedIncident =
        ENGINE.incident().ofInstance(piKey).resolve(username);

    // then
    Assertions.assertThat(resolvedIncident.getValue()).hasTenantId(tenantId);
    Assertions.assertThat(resolvedIncident).hasIntent(IncidentIntent.RESOLVED);
  }

  private static void assertJobDeadline(
      final Record<JobRecordValue> updatedRecord,
      final long jobKey,
      final JobRecordValue job,
      final long timeout) {
    Assertions.assertThat(updatedRecord)
        .hasRecordType(RecordType.EVENT)
        .hasIntent(JobIntent.TIMEOUT_UPDATED);
    assertThat(updatedRecord.getKey()).isEqualTo(jobKey);

    assertThat(updatedRecord.getValue().getDeadline()).isNotEqualTo(job.getDeadline());

    assertThat(updatedRecord.getValue().getDeadline())
        .isCloseTo(
            ENGINE.getClock().getCurrentTimeInMillis() + timeout,
            within(Duration.ofMillis(100).toMillis()));
  }
}
