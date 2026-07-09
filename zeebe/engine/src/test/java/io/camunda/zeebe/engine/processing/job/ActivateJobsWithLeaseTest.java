/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import static io.camunda.zeebe.test.util.record.RecordingExporter.jobRecords;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.JobBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.util.List;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class ActivateJobsWithLeaseTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  private static final String PROCESS_ID = "process";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private String jobType;

  @Before
  public void setup() {
    jobType = Strings.newRandomValidBpmnId();
  }

  @Test
  public void shouldActivateJobsWithDistinctLeaseTokens() {
    // given two activatable jobs of the same type
    ENGINE.createJob(jobType, PROCESS_ID);
    ENGINE.createJob(jobType, PROCESS_ID);

    // when
    final Record<JobBatchRecordValue> batch =
        ENGINE.jobs().withType(jobType).withLease().withMaxJobsToActivate(2).activate();

    // then
    final List<JobRecordValue> jobs = batch.getValue().getJobs();
    assertThat(jobs).hasSize(2);
    assertThat(jobs)
        .extracting(JobRecordValue::getLeaseToken)
        .describedAs("each activated job carries a distinct, non-empty lease token")
        .allSatisfy(token -> assertThat(token).isNotEmpty())
        .doesNotHaveDuplicates();
  }

  @Test
  public void shouldNotSetLeaseTokenWhenActivatingWithoutLease() {
    // given
    ENGINE.createJob(jobType, PROCESS_ID);

    // when activating without a lease (the default)
    final Record<JobBatchRecordValue> batch = ENGINE.jobs().withType(jobType).activate();

    // then
    assertThat(batch.getValue().getJobs())
        .extracting(JobRecordValue::getLeaseToken)
        .describedAs("activating without a lease leaves the token empty")
        .containsOnly("");
  }

  @Test
  public void shouldGenerateNewLeaseTokenOnReactivation() {
    // given a job that was leased, then failed back to activatable
    final long jobKey = ENGINE.createJob(jobType, PROCESS_ID).getKey();
    final String firstToken =
        ENGINE
            .jobs()
            .withType(jobType)
            .withLease()
            .activate()
            .getValue()
            .getJobs()
            .get(0)
            .getLeaseToken();
    ENGINE.job().withKey(jobKey).withLeaseToken(firstToken).withRetries(1).fail();
    jobRecords(JobIntent.FAILED).withRecordKey(jobKey).await();

    // when it is leased again
    final String secondToken =
        ENGINE
            .jobs()
            .withType(jobType)
            .withLease()
            .activate()
            .getValue()
            .getJobs()
            .get(0)
            .getLeaseToken();

    // then
    assertThat(firstToken).describedAs("the first activation generated a lease token").isNotEmpty();
    assertThat(secondToken)
        .describedAs("re-activation generates a new, distinct lease token")
        .isNotEmpty()
        .isNotEqualTo(firstToken);
  }

  @Test
  public void shouldRetainLeaseTokenAfterFailure() {
    // given a leased job
    final long jobKey = ENGINE.createJob(jobType, PROCESS_ID).getKey();
    final String leaseToken =
        ENGINE
            .jobs()
            .withType(jobType)
            .withLease()
            .activate()
            .getValue()
            .getJobs()
            .get(0)
            .getLeaseToken();

    // when it fails back to activatable
    ENGINE.job().withKey(jobKey).withLeaseToken(leaseToken).withRetries(1).fail();

    // then
    final Record<JobRecordValue> failed =
        jobRecords(JobIntent.FAILED).withRecordKey(jobKey).getFirst();
    assertThat(leaseToken).describedAs("the job was leased on activation").isNotEmpty();
    assertThat(failed.getValue().getLeaseToken())
        .describedAs("the failed job retains its lease token")
        .isEqualTo(leaseToken);
  }

  @Test
  public void shouldRetainLeaseTokenAfterTimeout() {
    // given a leased job
    final long jobKey = ENGINE.createJob(jobType, PROCESS_ID).getKey();
    final Duration timeout = Duration.ofSeconds(10);
    final String leaseToken =
        ENGINE
            .jobs()
            .withType(jobType)
            .withLease()
            .withTimeout(timeout.toMillis())
            .activate()
            .getValue()
            .getJobs()
            .get(0)
            .getLeaseToken();

    // when the lease times out
    ENGINE.increaseTime(timeout.plus(EngineConfiguration.DEFAULT_JOBS_TIMEOUT_POLLING_INTERVAL));

    // then
    final Record<JobRecordValue> timedOut =
        jobRecords(JobIntent.TIMED_OUT).withRecordKey(jobKey).getFirst();
    assertThat(leaseToken).describedAs("the job was leased on activation").isNotEmpty();
    assertThat(timedOut.getValue().getLeaseToken())
        .describedAs("the timed-out job retains its lease token")
        .isEqualTo(leaseToken);
  }

  @Test
  public void shouldRetainLeaseTokenAfterReplay() {
    // given a leased job with a timeout
    final long jobKey = ENGINE.createJob(jobType, PROCESS_ID).getKey();
    final Duration timeout = Duration.ofSeconds(10);
    final String leaseToken =
        ENGINE
            .jobs()
            .withType(jobType)
            .withLease()
            .withTimeout(timeout.toMillis())
            .activate()
            .getValue()
            .getJobs()
            .get(0)
            .getLeaseToken();

    // when the engine restarts and replays the log
    ENGINE.replay();

    // then the lease token survives, observable on a fresh event generated from the replayed state
    ENGINE.increaseTime(timeout.plus(EngineConfiguration.DEFAULT_JOBS_TIMEOUT_POLLING_INTERVAL));
    final Record<JobRecordValue> timedOut =
        jobRecords(JobIntent.TIMED_OUT).withRecordKey(jobKey).getFirst();
    assertThat(leaseToken).describedAs("the job was leased on activation").isNotEmpty();
    assertThat(timedOut.getValue().getLeaseToken())
        .describedAs("the lease token survives an engine restart and log replay")
        .isEqualTo(leaseToken);
  }

  @Test
  public void shouldSkipLeasedJobWhenActivatingWithoutLease() {
    // given a leased job that failed back to activatable, alongside two unleased jobs
    final long leasedJobKey = ENGINE.createJob(jobType, PROCESS_ID).getKey();
    ENGINE.jobs().withType(jobType).withLease().activate();
    ENGINE.job().withKey(leasedJobKey).withRetries(1).fail();
    jobRecords(JobIntent.FAILED).withRecordKey(leasedJobKey).await();
    final long unleasedJobKey = ENGINE.createJob(jobType, PROCESS_ID).getKey();
    final long otherUnleasedJobKey = ENGINE.createJob(jobType, PROCESS_ID).getKey();

    // when activating without a lease, with room for all three jobs
    final Record<JobBatchRecordValue> batch =
        ENGINE.jobs().withType(jobType).withMaxJobsToActivate(3).activate();

    // then the leased job is skipped, the rest activate (no batch rejection)
    assertThat(batch.getValue().getJobKeys())
        .describedAs("activating without a lease skips the leased job and activates the rest")
        .containsExactlyInAnyOrder(unleasedJobKey, otherUnleasedJobKey)
        .doesNotContain(leasedJobKey);
  }

  @Test
  public void shouldNotConsumeActivationSlotForSkippedLeasedJob() {
    // given a leased job that failed back to activatable, alongside two unleased jobs.
    // The leased job is created first (lowest key) so it is visited before the batch fills.
    final long leasedJobKey = ENGINE.createJob(jobType, PROCESS_ID).getKey();
    ENGINE.jobs().withType(jobType).withLease().activate();
    ENGINE.job().withKey(leasedJobKey).withRetries(1).fail();
    jobRecords(JobIntent.FAILED).withRecordKey(leasedJobKey).await();
    final long unleasedJobKey = ENGINE.createJob(jobType, PROCESS_ID).getKey();
    final long otherUnleasedJobKey = ENGINE.createJob(jobType, PROCESS_ID).getKey();

    // when activating without a lease, limited to two jobs
    final Record<JobBatchRecordValue> batch =
        ENGINE.jobs().withType(jobType).withMaxJobsToActivate(2).activate();

    // then the skipped leased job does not consume an activation slot: both unleased jobs activate
    assertThat(batch.getValue().getJobKeys())
        .describedAs("the skipped leased job does not consume an activation slot")
        .containsExactlyInAnyOrder(unleasedJobKey, otherUnleasedJobKey);
  }
}
