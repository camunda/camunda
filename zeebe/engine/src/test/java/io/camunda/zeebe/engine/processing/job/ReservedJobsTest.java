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
import java.util.Set;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * A process instance created with a job reservation token drives its own jobs: the engine hands
 * them to no job worker, and only a caller supplying the token can act on them. This is what lets a
 * recording tool step through an instance on a cluster whose workers keep serving every other
 * instance.
 */
public final class ReservedJobsTest {

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
  public void shouldStampReservationTokenOnCreatedJob() {
    // when
    final long processInstanceKey = createReservedInstance();

    // then
    assertThat(createdJob(processInstanceKey).getValue().getJobReservationToken())
        .describedAs("a job of a reserved instance carries its reservation token")
        .isEqualTo(RESERVATION_TOKEN);
  }

  @Test
  public void shouldNotStampReservationTokenWithoutOne() {
    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // then
    assertThat(createdJob(processInstanceKey).getValue().getJobReservationToken())
        .describedAs("an ordinary instance's jobs are not reserved")
        .isEmpty();
  }

  @Test
  public void shouldReserveJobOfChildProcessInstance() {
    // given a reserved parent instance that calls a child process holding the service task
    final String childProcessId = Strings.newRandomValidBpmnId();
    final String childJobType = Strings.newRandomValidBpmnId();
    final String parentProcessId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            "child.bpmn",
            Bpmn.createExecutableProcess(childProcessId)
                .startEvent()
                .serviceTask("child-task", t -> t.zeebeJobType(childJobType))
                .endEvent()
                .done())
        .withXmlResource(
            "parent.bpmn",
            Bpmn.createExecutableProcess(parentProcessId)
                .startEvent()
                .callActivity("call", c -> c.zeebeProcessId(childProcessId))
                .endEvent()
                .done())
        .deploy();

    // when
    ENGINE
        .processInstance()
        .ofBpmnProcessId(parentProcessId)
        .withJobReservationToken(RESERVATION_TOKEN)
        .create();

    // then
    final var childJob = jobRecords(JobIntent.CREATED).withType(childJobType).getFirst();
    assertThat(childJob.getValue().getJobReservationToken())
        .describedAs("a job of a called child instance inherits the root's reservation token")
        .isEqualTo(RESERVATION_TOKEN);
    assertThat(ENGINE.jobs().withType(childJobType).activate().getValue().getJobs())
        .describedAs("a poll must not be served a reserved child instance's job")
        .isEmpty();
  }

  @Test
  public void shouldKeepReservedJobFromWorkersAfterRetryBackoff() {
    // given a reserved job the holder failed with a retry backoff, so the backoff checker
    // re-dispatches it later through the push path
    final long processInstanceKey = createReservedInstance();
    final long jobKey = createdJob(processInstanceKey).getKey();
    final RecordingJobStream jobStream = registerStream();
    ENGINE
        .job()
        .withKey(jobKey)
        .withJobReservationToken(RESERVATION_TOKEN)
        .withRetries(2)
        .withBackOff(Duration.ofMinutes(1))
        .fail();
    jobRecords(JobIntent.FAILED).withRecordKey(jobKey).await();

    // when the backoff elapses
    ENGINE.increaseTime(Duration.ofMinutes(2));
    jobRecords(JobIntent.RECURRED_AFTER_BACKOFF).withRecordKey(jobKey).await();

    // then
    assertThat(jobStream.getActivatedJobs())
        .describedAs("recurring after a backoff does not push a reserved job to a stream")
        .isEmpty();
    assertThat(ENGINE.jobs().withType(jobType).activate().getValue().getJobs())
        .describedAs("recurring after a backoff does not expose a reserved job to a poll")
        .isEmpty();
  }

  @Test
  public void shouldRetainReservationTokenAfterReplay() {
    // given
    final long processInstanceKey = createReservedInstance();
    final long jobKey = createdJob(processInstanceKey).getKey();

    // when
    ENGINE.replay();

    // then a command processed after replay still sees the reservation
    final Record<JobRecordValue> rejection =
        ENGINE.job().withKey(jobKey).expectRejection().complete();
    Assertions.assertThat(rejection)
        .describedAs("the reservation token survives log replay")
        .hasRejectionType(RejectionType.INVALID_STATE);
  }

  @Test
  public void shouldNotActivateReservedJob() {
    // given
    createReservedInstance();

    // when
    final var batch = ENGINE.jobs().withType(jobType).activate();

    // then
    assertThat(batch.getValue().getJobs())
        .describedAs("a poll must not be served a reserved job")
        .isEmpty();
  }

  @Test
  public void shouldNotActivateReservedJobWithLease() {
    // given
    createReservedInstance();

    // when a lease-aware worker polls, the way an agent worker does
    final var batch = ENGINE.jobs().withType(jobType).withLease().activate();

    // then
    assertThat(batch.getValue().getJobs())
        .describedAs("opting into leases does not unlock a reserved job")
        .isEmpty();
  }

  @Test
  public void shouldNotPushReservedJobToStream() {
    // given a stream is waiting for the job type before the instance exists
    final RecordingJobStream jobStream = registerStream();

    // when
    final long processInstanceKey = createReservedInstance();
    createdJob(processInstanceKey);

    // then
    assertThat(jobStream.getActivatedJobs())
        .describedAs("a reserved job is never pushed to a stream")
        .isEmpty();
  }

  @Test
  public void shouldCompleteReservedJobWithMatchingToken() {
    // given
    final long processInstanceKey = createReservedInstance();
    final long jobKey = createdJob(processInstanceKey).getKey();

    // when
    final Record<JobRecordValue> completed =
        ENGINE.job().withKey(jobKey).withJobReservationToken(RESERVATION_TOKEN).complete();

    // then
    Assertions.assertThat(completed)
        .describedAs("the holder of the reservation token completes the job itself")
        .hasIntent(JobIntent.COMPLETED);
  }

  @Test
  public void shouldRejectCompleteReservedJobWithoutToken() {
    // given
    final long processInstanceKey = createReservedInstance();
    final long jobKey = createdJob(processInstanceKey).getKey();

    // when
    final Record<JobRecordValue> rejection =
        ENGINE.job().withKey(jobKey).expectRejection().complete();

    // then
    Assertions.assertThat(rejection)
        .describedAs("completing a reserved job without its token is rejected")
        .hasRejectionType(RejectionType.INVALID_STATE);
  }

  @Test
  public void shouldRejectCompleteReservedJobWithWrongToken() {
    // given
    final long processInstanceKey = createReservedInstance();
    final long jobKey = createdJob(processInstanceKey).getKey();

    // when
    final Record<JobRecordValue> rejection =
        ENGINE
            .job()
            .withKey(jobKey)
            .withJobReservationToken("someone-elses-token")
            .expectRejection()
            .complete();

    // then
    Assertions.assertThat(rejection)
        .describedAs("a reserved job is fenced against a caller with the wrong token")
        .hasRejectionType(RejectionType.INVALID_STATE);
  }

  @Test
  public void shouldRejectCompleteReservedJobWithTokenInLeaseField() {
    // given
    final long processInstanceKey = createReservedInstance();
    final long jobKey = createdJob(processInstanceKey).getKey();

    // when the caller sends the reservation token in the lease field instead
    final Record<JobRecordValue> rejection =
        ENGINE
            .job()
            .withKey(jobKey)
            .withJobLeaseToken(RESERVATION_TOKEN)
            .expectRejection()
            .complete();

    // then
    Assertions.assertThat(rejection)
        .describedAs("the reservation token is its own field, not the engine-minted lease token")
        .hasRejectionType(RejectionType.INVALID_STATE);
  }

  @Test
  public void shouldRejectUpdateOfReservedJobWithoutToken() {
    // given a lease lets an operator update a leased job without a token, but a reservation is
    // exclusivity rather than staleness fencing, so it holds for updates too
    final long processInstanceKey = createReservedInstance();
    final long jobKey = createdJob(processInstanceKey).getKey();

    // when update-retries carries no reservation token on any client, so a reserved job's
    // retries cannot be updated through it at all
    final Record<JobRecordValue> rejection =
        ENGINE.job().withKey(jobKey).withRetries(5).expectRejection().updateRetries();

    // then
    Assertions.assertThat(rejection)
        .describedAs("only the caller that reserved the instance may update its jobs")
        .hasRejectionType(RejectionType.INVALID_STATE);
  }

  @Test
  public void shouldUpdateReservedJobWithMatchingToken() {
    // given
    final long processInstanceKey = createReservedInstance();
    final long jobKey = createdJob(processInstanceKey).getKey();

    // when
    final Record<JobRecordValue> updated =
        ENGINE
            .job()
            .withKey(jobKey)
            .withJobReservationToken(RESERVATION_TOKEN)
            .withRetries(5)
            .withChangeset(Set.of("retries"))
            .update();

    // then
    Assertions.assertThat(updated)
        .describedAs("the holder of the reservation token can still update the job")
        .hasIntent(JobIntent.RETRIES_UPDATED);
  }

  @Test
  public void shouldKeepReservedJobFromWorkersAfterFailure() {
    // given a reserved job that the holder failed with retries left
    final long processInstanceKey = createReservedInstance();
    final long jobKey = createdJob(processInstanceKey).getKey();
    ENGINE.job().withKey(jobKey).withJobReservationToken(RESERVATION_TOKEN).withRetries(2).fail();
    jobRecords(JobIntent.FAILED).withRecordKey(jobKey).await();

    // when
    final var batch = ENGINE.jobs().withType(jobType).activate();

    // then
    assertThat(batch.getValue().getJobs())
        .describedAs("becoming activatable again does not expose a reserved job to workers")
        .isEmpty();
  }

  @Test
  public void shouldReserveJobsAlongsideTerminateInstruction() {
    // given a reserve-jobs instruction carries no element id, so it must not be checked against
    // the process definition the way a terminate instruction is

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withRuntimeTerminateInstruction("task")
            .withJobReservationToken(RESERVATION_TOKEN)
            .create();

    // then
    assertThat(createdJob(processInstanceKey).getValue().getJobReservationToken())
        .describedAs("both instructions are accepted and the jobs are still reserved")
        .isEqualTo(RESERVATION_TOKEN);
  }

  private long createReservedInstance() {
    return ENGINE
        .processInstance()
        .ofBpmnProcessId(processId)
        .withJobReservationToken(RESERVATION_TOKEN)
        .create();
  }

  private Record<JobRecordValue> createdJob(final long processInstanceKey) {
    return jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withType(jobType)
        .getFirst();
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
