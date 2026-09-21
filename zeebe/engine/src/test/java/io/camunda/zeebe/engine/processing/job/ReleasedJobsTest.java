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

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordingJobStreamer;
import io.camunda.zeebe.engine.util.RecordingJobStreamer.RecordingJobStream;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.stream.job.JobActivationPropertiesImpl;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.Duration;
import java.util.List;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * Releasing a reserved job hands it back to the job workers, so the worker that would have served
 * it — a connector runtime, for one — runs it for real. This is what lets a recording tool decide
 * per job whether to stand in for the work or let it happen.
 */
public final class ReleasedJobsTest {

  private static final String RESERVATION_TOKEN = "recorder-session-1";
  private static final long TIMEOUT_MS = 30_000L;
  private static final RecordingJobStreamer JOB_STREAMER = new RecordingJobStreamer();

  @ClassRule
  public static final EngineRule ENGINE =
      EngineRule.singlePartition().withJobStreamer(JOB_STREAMER);

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private String processId;
  private String jobType;

  @Before
  public void setup() {
    processId = Strings.newRandomValidBpmnId();
    jobType = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(jobType))
                .endEvent()
                .done())
        .deploy();
  }

  @Test
  public void shouldClearReservationTokenOnRelease() {
    // given
    final long jobKey = reservedJobKey();

    // when
    final Record<JobRecordValue> released =
        ENGINE.job().withKey(jobKey).withJobReservationToken(RESERVATION_TOKEN).release();

    // then
    Assertions.assertThat(released).hasIntent(JobIntent.RELEASED);
    assertThat(released.getValue().getJobReservationToken())
        .describedAs("a released job is no longer reserved")
        .isEmpty();
  }

  @Test
  public void shouldActivateReleasedJobOnPoll() {
    // given
    final long jobKey = reservedJobKey();
    assertThat(ENGINE.jobs().withType(jobType).activate().getValue().getJobs())
        .describedAs("the job is hidden while reserved")
        .isEmpty();

    // when
    ENGINE.job().withKey(jobKey).withJobReservationToken(RESERVATION_TOKEN).release();

    // then
    assertThat(ENGINE.jobs().withType(jobType).activate().getValue().getJobKeys())
        .describedAs("a released job is served to a polling worker")
        .containsExactly(jobKey);
  }

  @Test
  public void shouldPushReleasedJobToStream() {
    // given a stream that was waiting for the job type while the job was reserved
    final RecordingJobStream jobStream = registerStream();
    final long jobKey = reservedJobKey();
    assertThat(jobStream.getActivatedJobs()).isEmpty();

    // when
    ENGINE.job().withKey(jobKey).withJobReservationToken(RESERVATION_TOKEN).release();

    // then
    assertThat(jobStream.getActivatedJobs())
        .describedAs("a released job is pushed to a waiting stream")
        .hasSize(1);
  }

  @Test
  public void shouldCompleteReleasedJobWithoutToken() {
    // given
    final long jobKey = reservedJobKey();
    ENGINE.job().withKey(jobKey).withJobReservationToken(RESERVATION_TOKEN).release();
    ENGINE.jobs().withType(jobType).activate();

    // when the worker that took the job completes it the ordinary way
    final Record<JobRecordValue> completed = ENGINE.job().withKey(jobKey).complete();

    // then
    Assertions.assertThat(completed)
        .describedAs("a released job is no longer fenced against the workers")
        .hasIntent(JobIntent.COMPLETED);
  }

  @Test
  public void shouldRejectReleaseWithoutToken() {
    // given
    final long jobKey = reservedJobKey();

    // when
    final Record<JobRecordValue> rejection =
        ENGINE.job().withKey(jobKey).expectRejection().release();

    // then
    Assertions.assertThat(rejection)
        .describedAs("releasing a reserved job without its token is rejected")
        .hasRejectionType(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason())
        .describedAs("the reservation fence is what rejects it")
        .contains("reservation token must be provided");
    assertThat(ENGINE.jobs().withType(jobType).activate().getValue().getJobs())
        .describedAs("the rejected release leaves the job reserved")
        .isEmpty();
  }

  @Test
  public void shouldRejectReleaseWithWrongToken() {
    // given
    final long jobKey = reservedJobKey();

    // when
    final Record<JobRecordValue> rejection =
        ENGINE
            .job()
            .withKey(jobKey)
            .withJobReservationToken("someone-elses-token")
            .expectRejection()
            .release();

    // then
    Assertions.assertThat(rejection)
        .describedAs("a reserved job is fenced against a caller with the wrong token")
        .hasRejectionType(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason())
        .describedAs("the reservation fence is what rejects it")
        .contains("does not match");
  }

  @Test
  public void shouldRejectReleaseOfUnreservedJob() {
    // given
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    final long jobKey =
        jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(jobType)
            .getFirst()
            .getKey();

    // when
    final Record<JobRecordValue> rejection =
        ENGINE.job().withKey(jobKey).expectRejection().release();

    // then
    Assertions.assertThat(rejection)
        .describedAs("a job the workers can already see has nothing to release")
        .hasRejectionType(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason())
        .describedAs("the rejection names the missing reservation, not a lease or a job state")
        .contains("it is not reserved");
  }

  @Test
  public void shouldRejectReleaseOfCompletedJob() {
    // given
    final long jobKey = reservedJobKey();
    ENGINE.job().withKey(jobKey).withJobReservationToken(RESERVATION_TOKEN).complete();

    // when
    final Record<JobRecordValue> rejection =
        ENGINE
            .job()
            .withKey(jobKey)
            .withJobReservationToken(RESERVATION_TOKEN)
            .expectRejection()
            .release();

    // then
    Assertions.assertThat(rejection)
        .describedAs("a job that is gone cannot be released")
        .hasRejectionType(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldKeepJobReleasedAfterRetryBackoff() {
    // given a released job that the worker which took it failed with a retry backoff
    final long jobKey = reservedJobKey();
    ENGINE.job().withKey(jobKey).withJobReservationToken(RESERVATION_TOKEN).release();
    ENGINE.jobs().withType(jobType).activate();
    ENGINE.job().withKey(jobKey).withRetries(2).withBackOff(Duration.ofMinutes(1)).fail();
    jobRecords(JobIntent.FAILED).withRecordKey(jobKey).await();

    // when the backoff elapses
    ENGINE.increaseTime(Duration.ofMinutes(2));
    jobRecords(JobIntent.RECURRED_AFTER_BACKOFF).withRecordKey(jobKey).await();

    // then
    assertThat(ENGINE.jobs().withType(jobType).activate().getValue().getJobKeys())
        .describedAs("the release is one-way: a re-dispatched job stays visible to the workers")
        .containsExactly(jobKey);
  }

  @Test
  public void shouldStayReleasedAfterReplay() {
    // given
    final long jobKey = reservedJobKey();
    ENGINE.job().withKey(jobKey).withJobReservationToken(RESERVATION_TOKEN).release();

    // when
    ENGINE.replay();

    // then
    assertThat(ENGINE.jobs().withType(jobType).activate().getValue().getJobKeys())
        .describedAs("the release survives log replay")
        .containsExactly(jobKey);
  }

  private long reservedJobKey() {
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withJobReservationToken(RESERVATION_TOKEN)
            .create();
    return jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withType(jobType)
        .getFirst()
        .getKey();
  }

  private RecordingJobStream registerStream() {
    final var worker = BufferUtil.wrapString("test");
    final var properties =
        new JobActivationPropertiesImpl()
            .setWorker(worker, 0, worker.capacity())
            .setTimeout(TIMEOUT_MS)
            .setTenantIds(List.of(TenantOwned.DEFAULT_TENANT_IDENTIFIER));
    return JOB_STREAMER.addJobStream(BufferUtil.wrapString(jobType), properties);
  }
}
