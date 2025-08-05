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
import static io.camunda.zeebe.protocol.record.intent.JobIntent.FAIL;
import static io.camunda.zeebe.protocol.record.intent.JobIntent.FAILED;
import static io.camunda.zeebe.test.util.record.RecordingExporter.jobRecords;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobBatchIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.JobBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class FailJobTest {

  @ClassRule
  public static final EngineRule ENGINE =
      EngineRule.singlePartition()
          .withSecurityConfig(config -> config.getAuthorizations().setEnabled(true));

  private static final String PROCESS_ID = "process";
  private static String jobType;
  private static String username;
  private static String tenantId;

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

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
        .withResourceMatcher(AuthorizationResourceMatcher.ID)
        .withResourceId(PROCESS_ID)
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
  public void shouldFail() {
    // given
    ENGINE.createJob(jobType, PROCESS_ID);
    final Record<JobBatchRecordValue> batchRecord =
        ENGINE.jobs().withType(jobType).activate(username);
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
            .fail();

    // then
    Assertions.assertThat(failRecord).hasRecordType(RecordType.EVENT).hasIntent(FAILED);
    Assertions.assertThat(failRecord.getValue())
        .hasWorker(job.getWorker())
        .hasType(job.getType())
        .hasRetries(retries)
        .hasDeadline(job.getDeadline());
  }

  @Test
  public void shouldFailWithMessage() {
    // given
    ENGINE.createJob(jobType, PROCESS_ID);
    final Record<JobBatchRecordValue> batchRecord =
        ENGINE.jobs().withType(jobType).activate(username);

    final long jobKey = batchRecord.getValue().getJobKeys().get(0);
    final JobRecordValue job = batchRecord.getValue().getJobs().get(0);
    final int retries = 23;

    // when
    final Record<JobRecordValue> failedRecord =
        ENGINE
            .job()
            .withKey(jobKey)
            .ofInstance(job.getProcessInstanceKey())
            .withRetries(retries)
            .withErrorMessage("failed job")
            .fail();

    // then
    Assertions.assertThat(failedRecord).hasRecordType(RecordType.EVENT).hasIntent(FAILED);
    Assertions.assertThat(failedRecord.getValue())
        .hasWorker(job.getWorker())
        .hasType(job.getType())
        .hasRetries(retries)
        .hasDeadline(job.getDeadline())
        .hasErrorMessage(failedRecord.getValue().getErrorMessage());
  }

  @Test
  public void shouldFailJobAndRetry() {
    // given
    final Record<JobRecordValue> job = ENGINE.createJob(jobType, PROCESS_ID);

    final Record<JobBatchRecordValue> batchRecord =
        ENGINE.jobs().withType(jobType).activate(username);
    final long jobKey = batchRecord.getValue().getJobKeys().get(0);

    // when
    final Record<JobRecordValue> failRecord =
        ENGINE
            .job()
            .withKey(jobKey)
            .ofInstance(job.getValue().getProcessInstanceKey())
            .withRetries(3)
            .fail();
    ENGINE.jobs().withType(jobType).activate(username);

    // then
    Assertions.assertThat(failRecord).hasRecordType(RecordType.EVENT).hasIntent(FAILED);

    // and the job is published again
    final var jobBatchActivations =
        RecordingExporter.jobBatchRecords(JobBatchIntent.ACTIVATED)
            .withType(jobType)
            .limit(2)
            .collect(Collectors.toList());

    assertThat(jobBatchActivations).hasSize(2);
    assertThat(jobBatchActivations.get(0)).isEqualTo(batchRecord);
    assertThat(jobBatchActivations.get(1).getPosition())
        .isGreaterThan(jobBatchActivations.get(0).getPosition());
    assertThat(jobBatchActivations.get(1).getValue().getJobKeys().get(0)).isEqualTo(jobKey);

    // and the job lifecycle is correct
    final List<Record> jobEvents = jobRecords().limit(3).collect(Collectors.toList());
    assertThat(jobEvents)
        .extracting(Record::getRecordType, Record::getValueType, Record::getIntent)
        .containsExactly(
            tuple(RecordType.EVENT, ValueType.JOB, JobIntent.CREATED),
            tuple(RecordType.COMMAND, ValueType.JOB, FAIL),
            tuple(RecordType.EVENT, ValueType.JOB, FAILED));

    final List<Record<JobBatchRecordValue>> jobActivateCommands =
        RecordingExporter.jobBatchRecords().limit(4).collect(Collectors.toList());

    assertThat(jobActivateCommands)
        .extracting(Record::getRecordType, Record::getValueType, Record::getIntent)
        .containsExactly(
            tuple(RecordType.COMMAND, ValueType.JOB_BATCH, JobBatchIntent.ACTIVATE),
            tuple(RecordType.EVENT, ValueType.JOB_BATCH, JobBatchIntent.ACTIVATED),
            tuple(RecordType.COMMAND, ValueType.JOB_BATCH, JobBatchIntent.ACTIVATE),
            tuple(RecordType.EVENT, ValueType.JOB_BATCH, JobBatchIntent.ACTIVATED));
  }

  @Test
  public void shouldFailJobAndRetryWithBackOff() {
    // given
    final Record<JobRecordValue> job = ENGINE.createJob(jobType, PROCESS_ID);

    final Record<JobBatchRecordValue> batchRecord =
        ENGINE.jobs().withType(jobType).activate(username);
    final long jobKey = batchRecord.getValue().getJobKeys().get(0);

    // when
    final Duration backOff = Duration.ofDays(1);
    final Record<JobRecordValue> failRecord =
        ENGINE
            .job()
            .withKey(jobKey)
            .ofInstance(job.getValue().getProcessInstanceKey())
            .withRetries(3)
            .withBackOff(backOff)
            .fail();

    // then
    Assertions.assertThat(failRecord).hasRecordType(RecordType.EVENT).hasIntent(FAILED);

    // explicitly wait for polling
    ENGINE.increaseTime(Duration.ofMillis(JobBackoffChecker.BACKOFF_RESOLUTION));

    // verify that our job didn't recur after backoff
    final var reactivatedJobs = ENGINE.jobs().withType(jobType).activate();
    assertThat(reactivatedJobs.getValue().getJobs()).isEmpty();

    ENGINE.increaseTime(backOff.plus(Duration.ofMillis(JobBackoffChecker.BACKOFF_RESOLUTION)));

    // verify that our job recurred after backoff
    assertThat(jobRecords(JobIntent.RECURRED_AFTER_BACKOFF).withType(jobType).getFirst().getKey())
        .isEqualTo(jobKey);
  }

  @Test
  public void shouldFailJobWithBackOffAndRemainFailed() {
    // given
    final Record<JobRecordValue> job = ENGINE.createJob(jobType, PROCESS_ID);
    final long jobKey = job.getKey();
    final Duration backOff = Duration.ofDays(1);
    final Record<JobRecordValue> failRecord =
        ENGINE
            .job()
            .withKey(jobKey)
            .ofInstance(job.getValue().getProcessInstanceKey())
            .withRetries(3)
            .withBackOff(backOff)
            .fail();
    Assertions.assertThat(failRecord)
        .hasRecordType(RecordType.EVENT)
        .hasIntent(FAILED)
        .hasKey(jobKey);

    // when
    final var reactivatedJobs = ENGINE.jobs().withType(jobType).activate();

    // then
    assertThat(reactivatedJobs.getValue().getJobKeys()).doesNotContain(jobKey).isEmpty();
  }

  @Test
  public void shouldFailIfJobCreated() {
    // given
    final Record<JobRecordValue> job = ENGINE.createJob(jobType, PROCESS_ID);

    // when
    final Record<JobRecordValue> jobRecord = ENGINE.job().withKey(job.getKey()).fail();

    // then
    Assertions.assertThat(jobRecord).hasRecordType(RecordType.EVENT).hasIntent(FAILED);
  }

  @Test
  public void shouldRejectFailIfJobNotFound() {
    // given
    final int key = 123;

    // when
    final Record<JobRecordValue> jobRecord =
        ENGINE.job().withKey(key).withRetries(3).expectRejection().fail();

    // then
    Assertions.assertThat(jobRecord).hasRejectionType(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldRejectFailIfJobAlreadyFailed() {
    // given
    ENGINE.createJob(jobType, PROCESS_ID);
    final Record<JobBatchRecordValue> batchRecord =
        ENGINE.jobs().withType(jobType).activate(username);
    final long jobKey = batchRecord.getValue().getJobKeys().get(0);
    ENGINE.job().withKey(jobKey).withRetries(0).fail();

    // when
    final Record<JobRecordValue> jobRecord =
        ENGINE.job().withKey(jobKey).withRetries(3).expectRejection().fail();

    // then
    Assertions.assertThat(jobRecord).hasRejectionType(RejectionType.INVALID_STATE);
    assertThat(jobRecord.getRejectionReason()).contains("it is in state 'FAILED'");
  }

  @Test
  public void shouldRejectFailIfJobCompleted() {
    // given
    ENGINE.createJob(jobType, PROCESS_ID);
    final Record<JobBatchRecordValue> batchRecord =
        ENGINE.jobs().withType(jobType).activate(username);
    final long jobKey = batchRecord.getValue().getJobKeys().get(0);

    ENGINE.job().withKey(jobKey).complete();

    // when
    final Record<JobRecordValue> jobRecord =
        ENGINE.job().withKey(jobKey).withRetries(3).expectRejection().fail();

    // then
    Assertions.assertThat(jobRecord).hasRejectionType(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldRejectFailIfErrorThrown() {
    // given
    final var job = ENGINE.createJob(jobType, PROCESS_ID);

    ENGINE.job().withKey(job.getKey()).withErrorCode("error").throwError();

    // when
    final Record<JobRecordValue> jobRecord =
        ENGINE.job().withKey(job.getKey()).withRetries(3).expectRejection().fail();

    // then
    Assertions.assertThat(jobRecord).hasRejectionType(RejectionType.INVALID_STATE);
    assertThat(jobRecord.getRejectionReason()).contains("is in state 'ERROR_THROWN'");
  }

  @Test
  public void shouldFailWithVariables() {
    // given
    ENGINE.createJob(jobType, PROCESS_ID);
    final Record<JobBatchRecordValue> batchRecord =
        ENGINE.jobs().withType(jobType).activate(username);
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
            .withVariables("{'foo':'bar'}")
            .fail();

    // then
    Assertions.assertThat(failRecord).hasRecordType(RecordType.EVENT).hasIntent(FAILED);
    Assertions.assertThat(failRecord.getValue())
        .hasWorker(job.getWorker())
        .hasType(job.getType())
        .hasRetries(retries)
        .hasDeadline(job.getDeadline());

    assertThat(failRecord.getValue().getVariables()).containsExactly(entry("foo", "bar"));

    final Record<VariableRecordValue> variableRecord =
        RecordingExporter.variableRecords()
            .withProcessInstanceKey(job.getProcessInstanceKey())
            .getFirst();

    Assertions.assertThat(variableRecord.getValue())
        .describedAs("check set failing job variables locally")
        .hasScopeKey(failRecord.getValue().getElementInstanceKey())
        .hasName("foo")
        .hasValue("\"bar\"");
  }

  @Test
  public void shouldTruncateErrorMessage() {
    // given
    final Record<JobRecordValue> job = ENGINE.createJob(jobType, PROCESS_ID);
    final String exceedingErrorMessage = "*".repeat(DEFAULT_MAX_ERROR_MESSAGE_SIZE + 1);

    // when
    final Record<JobRecordValue> failedRecord =
        ENGINE.job().withKey(job.getKey()).withErrorMessage(exceedingErrorMessage).fail();

    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(CREATED).getFirst();

    // then
    Assertions.assertThat(failedRecord).hasRecordType(RecordType.EVENT).hasIntent(FAILED);

    final String expectedErrorMessage = "*".repeat(DEFAULT_MAX_ERROR_MESSAGE_SIZE).concat("...");
    assertThat(failedRecord.getValue().getErrorMessage()).isEqualTo(expectedErrorMessage);
    assertThat(incident.getValue().getErrorMessage()).isEqualTo(expectedErrorMessage);
  }

  @Test
  public void shouldNotTruncateErrorMessage() {
    // given
    final Record<JobRecordValue> job = ENGINE.createJob(jobType, PROCESS_ID);
    final String errorMessage = "*".repeat(DEFAULT_MAX_ERROR_MESSAGE_SIZE);

    // when
    final Record<JobRecordValue> failedRecord =
        ENGINE.job().withKey(job.getKey()).withErrorMessage(errorMessage).fail();

    final var incident = RecordingExporter.incidentRecords(CREATED).getFirst();

    // then
    Assertions.assertThat(failedRecord).hasRecordType(RecordType.EVENT).hasIntent(FAILED);

    final String expectedErrorMessage = "*".repeat(DEFAULT_MAX_ERROR_MESSAGE_SIZE);
    assertThat(failedRecord.getValue().getErrorMessage()).isEqualTo(expectedErrorMessage);
    assertThat(incident.getValue().getErrorMessage()).isEqualTo(expectedErrorMessage);
  }
}
