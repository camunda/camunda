/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;
// seed-data-demo: test PR for /seed-data sibling walkthrough — safe to delete

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.JobBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.util.Set;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class JobUpdateTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  private static final String PROCESS_ID = "process";
  private static String jobType;

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Before
  public void setup() {
    jobType = Strings.newRandomValidBpmnId();
  }

  @Test
  public void shouldUpdateJob() {
    // given
    ENGINE.createJob(jobType, PROCESS_ID);

    final var batchRecord = ENGINE.jobs().withType(jobType).activate();
    final long jobKey = batchRecord.getValue().getJobKeys().get(0);

    // when
    ENGINE
        .job()
        .withKey(jobKey)
        .withRetries(5)
        .withTimeout(Duration.ofMinutes(5).toMillis())
        .withChangeset(Set.of("retries", "timeout"))
        .update();

    // then
    assertThat(RecordingExporter.jobRecords().limit(4))
        .extracting(
            Record::getIntent,
            record -> record.getValue().getRetries(),
            record -> record.getValue().getTimeout())
        .containsSubsequence(
            tuple(JobIntent.CREATED, 3, -1L),
            tuple(JobIntent.UPDATE, 5, 300000L),
            tuple(JobIntent.RETRIES_UPDATED, 5, -1L),
            tuple(JobIntent.TIMEOUT_UPDATED, 5, -1L));
  }

  @Test
  public void shouldUpdateJobWithNoChanges() {
    // given
    ENGINE.createJob(jobType, PROCESS_ID);

    final var batchRecord = ENGINE.jobs().withType(jobType).activate();
    final long jobKey = batchRecord.getValue().getJobKeys().get(0);

    // when
    ENGINE.job().withKey(jobKey).update();

    // then
    assertThat(RecordingExporter.jobRecords().limit(3))
        .extracting(Record::getIntent)
        .containsSubsequence(JobIntent.CREATED, JobIntent.UPDATE, JobIntent.UPDATED);
  }

  @Test
  public void shouldUpdateJobWithOnlyRetries() {
    // given
    ENGINE.createJob(jobType, PROCESS_ID);

    final var batchRecord = ENGINE.jobs().withType(jobType).activate();
    final long jobKey = batchRecord.getValue().getJobKeys().get(0);

    // when
    ENGINE.job().withKey(jobKey).withRetries(5).withChangeset(Set.of("retries")).update();

    // then
    assertThat(RecordingExporter.jobRecords().limit(3))
        .extracting(Record::getIntent, record -> record.getValue().getRetries())
        .containsSubsequence(
            tuple(JobIntent.CREATED, 3),
            tuple(JobIntent.UPDATE, 5),
            tuple(JobIntent.RETRIES_UPDATED, 5));
  }

  @Test
  public void shouldUpdateJobWithOnlyTimeout() {
    // given
    ENGINE.createJob(jobType, PROCESS_ID);

    final var batchRecord = ENGINE.jobs().withType(jobType).activate();
    final long jobKey = batchRecord.getValue().getJobKeys().get(0);

    // when
    ENGINE
        .job()
        .withKey(jobKey)
        .withTimeout(Duration.ofMinutes(5).toMillis())
        .withChangeset(Set.of("timeout"))
        .update();

    // then
    assertThat(RecordingExporter.jobRecords().limit(3))
        .extracting(Record::getIntent, record -> record.getValue().getTimeout())
        .containsSubsequence(
            tuple(JobIntent.CREATED, -1L),
            tuple(JobIntent.UPDATE, 300000L),
            tuple(JobIntent.TIMEOUT_UPDATED, -1L));
  }

  @Test
  public void shouldRejectUpdateTimoutIfJobNotFound() {
    // given
    final long jobKey = 123L;

    // when
    final var jobRecord =
        ENGINE
            .job()
            .withKey(jobKey)
            .withRetries(5)
            .withTimeout(Duration.ofMinutes(5).toMillis())
            .withChangeset(Set.of("retries", "timeout"))
            .expectRejection()
            .update();

    // then
    Assertions.assertThat(jobRecord)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to update job with key '%d', but no such job was found".formatted(jobKey));
  }

  @Test
  public void shouldUpdateJobWithOnlyPriority() {
    // given
    final Record<JobRecordValue> jobCreated = ENGINE.createJob(jobType, PROCESS_ID);
    final long jobKey = jobCreated.getKey();

    // when
    ENGINE.job().withKey(jobKey).withPriority(10).withChangeset(Set.of("priority")).update();

    // then
    assertThat(RecordingExporter.jobRecords().withIntent(JobIntent.PRIORITY_UPDATED).getFirst())
        .extracting(record -> record.getValue().getPriority())
        .isEqualTo(10);
  }

  @Test
  public void shouldUpdatePriorityAndRetriesForFailedJob() {
    // given - a FAILED job; both priority and retries updates are valid for any non-terminal state
    ENGINE.createJob(jobType, PROCESS_ID);
    final var batchRecord = ENGINE.jobs().withType(jobType).activate();
    final long jobKey = batchRecord.getValue().getJobKeys().get(0);
    ENGINE.job().withKey(jobKey).withRetries(0).fail();

    // when
    ENGINE
        .job()
        .withKey(jobKey)
        .withPriority(10)
        .withRetries(5)
        .withChangeset(Set.of("priority", "retries"))
        .update();

    // then - PRIORITY_UPDATED and RETRIES_UPDATED both emitted; job record reflects new values
    assertThat(
            RecordingExporter.jobRecords()
                .filter(
                    r ->
                        r.getIntent() == JobIntent.PRIORITY_UPDATED
                            || r.getIntent() == JobIntent.RETRIES_UPDATED
                            || r.getIntent() == JobIntent.UPDATED)
                .limit(3))
        .extracting(Record::getIntent)
        .containsSubsequence(
            JobIntent.RETRIES_UPDATED, JobIntent.PRIORITY_UPDATED, JobIntent.UPDATED);

    assertThat(
            RecordingExporter.jobRecords()
                .withIntent(JobIntent.UPDATED)
                .withRecordKey(jobKey)
                .getFirst()
                .getValue())
        .satisfies(
            v -> {
              assertThat(v.getPriority()).isEqualTo(10);
              assertThat(v.getRetries()).isEqualTo(5);
            });
  }

  @Test
  public void shouldUpdatePriorityForActivatedJob() {
    // given - an ACTIVATED job
    ENGINE.createJob(jobType, PROCESS_ID);
    final var batchRecord = ENGINE.jobs().withType(jobType).activate();
    final long jobKey = batchRecord.getValue().getJobKeys().get(0);

    // when
    ENGINE.job().withKey(jobKey).withPriority(20).withChangeset(Set.of("priority")).update();

    // then - PRIORITY_UPDATED emitted; job record stores new priority (CF update deferred to
    // re-entry)
    assertThat(
            RecordingExporter.jobRecords()
                .withIntent(JobIntent.PRIORITY_UPDATED)
                .withRecordKey(jobKey)
                .getFirst()
                .getValue()
                .getPriority())
        .isEqualTo(20);
  }

  @Test
  public void shouldApplyUpdatedPriorityWhenActivatedJobTimesOut() {
    // given - activate a job, then update its priority while it is ACTIVATED
    final long shortTimeout = Duration.ofMillis(10).toMillis();
    ENGINE.createJob(jobType, PROCESS_ID);
    final var batchRecord = ENGINE.jobs().withType(jobType).withTimeout(shortTimeout).activate();
    final long jobKey = batchRecord.getValue().getJobKeys().get(0);

    ENGINE.job().withKey(jobKey).withPriority(99).withChangeset(Set.of("priority")).update();

    // when - advance time so the job times out and re-enters ACTIVATABLE
    ENGINE.increaseTime(EngineConfiguration.DEFAULT_JOBS_TIMEOUT_POLLING_INTERVAL);
    RecordingExporter.jobRecords().withIntent(JobIntent.TIMED_OUT).withRecordKey(jobKey).await();

    // then - re-activating the job returns it at the updated priority (99)
    final var reactivated = ENGINE.jobs().withType(jobType).activate();
    assertThat(reactivated.getValue().getJobs())
        .hasSize(1)
        .first()
        .extracting(JobRecordValue::getPriority)
        .isEqualTo(99);
  }

  @Test
  public void shouldRejectJobUpdateForBannedProcessInstance() {
    // given
    final Record<JobRecordValue> jobCreated = ENGINE.createJob(jobType, PROCESS_ID);
    final long processInstanceKey = jobCreated.getValue().getProcessInstanceKey();
    final Record<JobBatchRecordValue> batchRecord = ENGINE.jobs().withType(jobType).activate();
    final Long jobKey = batchRecord.getValue().getJobKeys().get(0);

    // ban the process instance
    ENGINE.banInstanceInNewTransaction(1, processInstanceKey);
    RecordingExporter.errorRecords().withRecordKey(processInstanceKey).await();

    // when
    final Record<JobRecordValue> jobRecord =
        ENGINE
            .job()
            .withKey(jobKey)
            .withRetries(5)
            .withChangeset(Set.of("retries"))
            .expectRejection()
            .update();

    // then
    Assertions.assertThat(jobRecord)
        .hasRejectionType(RejectionType.INVALID_STATE)
        .hasRejectionReason(
            "Expected to process command for process instance with key '%d', but the process instance is banned due to previous errors. The process instance can't be recovered, but it can be cancelled."
                .formatted(processInstanceKey));
  }
}
