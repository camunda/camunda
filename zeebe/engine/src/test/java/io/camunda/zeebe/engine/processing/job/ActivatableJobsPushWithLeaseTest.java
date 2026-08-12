/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import static io.camunda.zeebe.protocol.record.intent.JobBatchIntent.ACTIVATED;
import static io.camunda.zeebe.protocol.record.intent.JobIntent.TIMED_OUT;
import static io.camunda.zeebe.test.util.record.RecordingExporter.jobBatchRecords;
import static io.camunda.zeebe.test.util.record.RecordingExporter.jobRecords;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordingJobStreamer;
import io.camunda.zeebe.engine.util.RecordingJobStreamer.RecordingJobStream;
import io.camunda.zeebe.protocol.impl.stream.job.JobActivationPropertiesImpl;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.Duration;
import java.util.List;
import org.agrona.DirectBuffer;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * The job streaming/push path's equivalent of the poll path's {@link ActivateJobsWithLeaseTest}.
 */
public final class ActivatableJobsPushWithLeaseTest {

  private static final String PROCESS_ID = "process";
  private static final long TIMEOUT_MS = 30_000L;
  private static final RecordingJobStreamer JOB_STREAMER = new RecordingJobStreamer();

  @ClassRule
  public static final EngineRule ENGINE =
      EngineRule.singlePartition().withJobStreamer(JOB_STREAMER);

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private String jobType;
  private DirectBuffer worker;

  @Before
  public void setup() {
    jobType = Strings.newRandomValidBpmnId();
    worker = BufferUtil.wrapString("test");
  }

  @Test
  public void shouldPushJobWithNonEmptyLeaseTokenWhenStreamIsLeasing() {
    // given
    final RecordingJobStream jobStream = registerStream(true);

    // when
    ENGINE.createJob(jobType, PROCESS_ID);

    // then
    await().untilAsserted(() -> assertThat(jobStream.getActivatedJobs()).hasSize(1));
    assertThat(jobStream.getActivatedJobs().getFirst().jobRecord().getLeaseToken())
        .describedAs("a job pushed to a leasing stream carries a non-empty lease token")
        .isNotEmpty();
  }

  @Test
  public void shouldPushDistinctLeaseTokensForEachJobOnLeasingStream() {
    // given
    final RecordingJobStream jobStream = registerStream(true);

    // when
    ENGINE.createJob(jobType, PROCESS_ID);
    ENGINE.createJob(jobType, PROCESS_ID);

    // then
    await().untilAsserted(() -> assertThat(jobStream.getActivatedJobs()).hasSize(2));
    assertThat(jobStream.getActivatedJobs())
        .extracting(activatedJob -> activatedJob.jobRecord().getLeaseToken())
        .describedAs("each job pushed to a leasing stream carries a distinct lease token")
        .allSatisfy(token -> assertThat(token).isNotEmpty())
        .doesNotHaveDuplicates();
  }

  @Test
  public void shouldPersistLeaseTokenMatchingPushedToken() {
    // given
    final RecordingJobStream jobStream = registerStream(true);

    // when
    ENGINE.createJob(jobType, PROCESS_ID);
    await().untilAsserted(() -> assertThat(jobStream.getActivatedJobs()).hasSize(1));

    // then
    final String wireToken = jobStream.getActivatedJobs().getFirst().jobRecord().getLeaseToken();
    assertThat(wireToken)
        .describedAs("the job pushed on the wire carries a non-empty lease token")
        .isNotEmpty();

    // non-empty asserted before equality: wire and state derive from separate code paths
    final String stateToken = persistedLeaseToken();
    assertThat(stateToken)
        .describedAs("the token pushed on the wire equals the token persisted in state")
        .isEqualTo(wireToken);
  }

  @Test
  public void shouldRetainLeaseTokenAfterReplay() {
    // given
    registerStream(true);
    ENGINE.createJob(jobType, PROCESS_ID);
    jobBatchRecords(ACTIVATED).withType(jobType).await();
    final String leaseToken = persistedLeaseToken();

    // when
    ENGINE.replay();

    // then
    // a fresh event triggered post-replay, not a re-read of re-exported log bytes
    ENGINE.increaseTime(
        Duration.ofMillis(TIMEOUT_MS)
            .plus(EngineConfiguration.DEFAULT_JOBS_TIMEOUT_POLLING_INTERVAL));
    final JobRecordValue timedOut = jobRecords(TIMED_OUT).withType(jobType).getFirst().getValue();
    assertThat(leaseToken).describedAs("the job was leased on push").isNotEmpty();
    assertThat(timedOut.getLeaseToken())
        .describedAs("the lease token survives an engine restart and log replay")
        .isEqualTo(leaseToken);
  }

  @Test
  public void shouldRejectCompleteWithLeaseTokenSupersededByPushReactivation() {
    // given a job pushed with a lease, then failed with retries so it is immediately re-pushed
    final RecordingJobStream jobStream = registerStream(true);
    final long jobKey = ENGINE.createJob(jobType, PROCESS_ID).getKey();
    await().untilAsserted(() -> assertThat(jobStream.getActivatedJobs()).hasSize(1));
    final String firstToken = jobStream.getActivatedJobs().getFirst().jobRecord().getLeaseToken();

    ENGINE.job().withKey(jobKey).withLeaseToken(firstToken).withRetries(1).fail();
    await().untilAsserted(() -> assertThat(jobStream.getActivatedJobs()).hasSize(2));
    final String secondToken = jobStream.getActivatedJobs().get(1).jobRecord().getLeaseToken();
    assertThat(secondToken)
        .describedAs("failing with retries re-pushes the job with a new, distinct lease token")
        .isNotEmpty()
        .isNotEqualTo(firstToken);

    // when completing with the token from the superseded first push
    final Record<JobRecordValue> rejection =
        ENGINE.job().withKey(jobKey).withLeaseToken(firstToken).expectRejection().complete();

    // then
    Assertions.assertThat(rejection)
        .describedAs("the lease from the first push no longer fences the job once it is re-pushed")
        .hasRejectionType(RejectionType.INVALID_STATE);
  }

  @Test
  public void shouldNotSetLeaseTokenWhenStreamIsNotLeasing() {
    // given
    final RecordingJobStream jobStream = registerStream(false);

    // when
    ENGINE.createJob(jobType, PROCESS_ID);

    // then
    await().untilAsserted(() -> assertThat(jobStream.getActivatedJobs()).hasSize(1));
    assertThat(jobStream.getActivatedJobs().getFirst().jobRecord().getLeaseToken())
        .describedAs("a freshly created job pushed to a non-leasing stream carries no lease token")
        .isEmpty();
  }

  private RecordingJobStream registerStream(final boolean withLease) {
    final var properties =
        new JobActivationPropertiesImpl()
            .setWorker(worker, 0, worker.capacity())
            .setTimeout(TIMEOUT_MS)
            .setTenantIds(List.of(TenantOwned.DEFAULT_TENANT_IDENTIFIER))
            .setWithLease(withLease);
    return JOB_STREAMER.addJobStream(BufferUtil.wrapString(jobType), properties);
  }

  private String persistedLeaseToken() {
    return jobBatchRecords(ACTIVATED)
        .withType(jobType)
        .getFirst()
        .getValue()
        .getJobs()
        .getFirst()
        .getLeaseToken();
  }
}
