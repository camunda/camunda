/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import static io.camunda.zeebe.auth.Authorization.AUTHORIZED_USERNAME;
import static io.camunda.zeebe.test.util.record.RecordingExporter.jobBatchRecords;
import static io.camunda.zeebe.test.util.record.RecordingExporter.records;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordingJobStreamer;
import io.camunda.zeebe.engine.util.RecordingJobStreamer.RecordingJobStream;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.stream.job.ActivatedJob;
import io.camunda.zeebe.protocol.impl.stream.job.JobActivationPropertiesImpl;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.JobBatchIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.JobBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantFilter;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class MultiTenancyActivatableJobsPushTest {

  private static final String PROCESS_ID = "process";
  private static final Duration MAX_WAIT_TIME_FOR_ACTIVATED_JOBS =
      Duration.ofMillis(RecordingExporter.DEFAULT_MAX_WAIT_TIME);

  private static final RecordingJobStreamer JOB_STREAMER = new RecordingJobStreamer();

  @ClassRule
  public static final EngineRule ENGINE =
      EngineRule.singlePartition()
          .withJobStreamer(JOB_STREAMER)
          .withSecurityConfig(
              config -> {
                config.getMultiTenancy().setChecksEnabled(true);
              });

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Before
  public void setUp() {
    JOB_STREAMER.clearStreams();
  }

  @Test
  public void shouldPushWhenJobCreatedForAuthorizedTenant() {
    // given
    final String jobType = Strings.newRandomValidBpmnId();
    final DirectBuffer jobTypeBuffer = BufferUtil.wrapString(jobType);
    final DirectBuffer worker = BufferUtil.wrapString("test");
    final Map<String, Object> variables = Map.of("a", "valA", "b", "valB", "c", "valC");
    final long timeout = 30000L;
    final String username = Strings.newRandomValidIdentityId();
    final String tenantIdA = Strings.newRandomValidTenantId();
    final String tenantIdB = Strings.newRandomValidTenantId();

    ENGINE.tenant().newTenant().withTenantId(tenantIdA).create();
    ENGINE.tenant().newTenant().withTenantId(tenantIdB).create();
    ENGINE.user().newUser(username).create();
    ENGINE
        .tenant()
        .addEntity(tenantIdA)
        .withEntityId(username)
        .withEntityType(EntityType.USER)
        .add();
    final Map<String, Object> authorizationClaims = Map.of(AUTHORIZED_USERNAME, username);

    final JobActivationPropertiesImpl jobActivationPropertiesA =
        new JobActivationPropertiesImpl()
            .setWorker(worker, 0, worker.capacity())
            .setTimeout(timeout)
            .setFetchVariables(
                List.of(new StringValue("a"), new StringValue("b"), new StringValue("c")))
            .setClaims(authorizationClaims)
            .setTenantIds(List.of(tenantIdA));

    final JobActivationPropertiesImpl jobActivationPropertiesB =
        new JobActivationPropertiesImpl()
            .setWorker(worker, 0, worker.capacity())
            .setTimeout(timeout)
            .setFetchVariables(
                List.of(new StringValue("a"), new StringValue("b"), new StringValue("c")))
            .setClaims(authorizationClaims)
            .setTenantIds(List.of(tenantIdB));

    final var jobStreamA = JOB_STREAMER.addJobStream(jobTypeBuffer, jobActivationPropertiesA);
    final var jobStreamB = JOB_STREAMER.addJobStream(jobTypeBuffer, jobActivationPropertiesB);

    final int activationCount = 1;

    // when
    final long jobKey = createJob(jobType, PROCESS_ID, variables, tenantIdA);

    // then
    final Record<JobBatchRecordValue> batchRecord =
        jobBatchRecords(JobBatchIntent.ACTIVATED).withType(jobType).getFirst();

    // assert job batch record
    final JobBatchRecordValue batch = batchRecord.getValue();
    final List<JobRecordValue> jobs = batch.getJobs();
    assertThat(jobs).hasSize(1);
    assertThat(batch.getJobKeys()).contains(jobKey);

    // assert event order
    assertThat(records().limit(r -> r.getIntent() == JobBatchIntent.ACTIVATED))
        .extracting(Record::getIntent)
        .containsSequence(JobIntent.CREATED, JobBatchIntent.ACTIVATED);

    // assert job stream
    assertActivatedJob(jobStreamA, jobKey, worker, variables, activationCount, tenantIdA);
    assertNoActivatedJobs(jobStreamB);
  }

  @Test
  public void shouldPushToAssignedTenantStreamWhenUsingAssignedFilter() {
    // given
    final String jobType = Strings.newRandomValidBpmnId();
    final DirectBuffer jobTypeBuffer = BufferUtil.wrapString(jobType);
    final DirectBuffer worker = BufferUtil.wrapString("test");
    final Map<String, Object> variables = Map.of("a", "valA", "b", "valB", "c", "valC");
    final long timeout = 30000L;
    final String username = Strings.newRandomValidIdentityId();
    final String tenantIdA = Strings.newRandomValidTenantId();
    final String tenantIdB = Strings.newRandomValidTenantId();

    ENGINE.tenant().newTenant().withTenantId(tenantIdA).create();
    ENGINE.tenant().newTenant().withTenantId(tenantIdB).create();
    ENGINE.user().newUser(username).create();
    // User is assigned to tenantIdA only — not tenantIdB
    ENGINE
        .tenant()
        .addEntity(tenantIdA)
        .withEntityId(username)
        .withEntityType(EntityType.USER)
        .add();
    final Map<String, Object> authorizationClaims = Map.of(AUTHORIZED_USERNAME, username);

    // Stream using ASSIGNED filter — should receive jobs for tenantIdA (assigned tenant)
    final JobActivationPropertiesImpl assignedProperties =
        new JobActivationPropertiesImpl()
            .setWorker(worker, 0, worker.capacity())
            .setTimeout(timeout)
            .setFetchVariables(
                List.of(new StringValue("a"), new StringValue("b"), new StringValue("c")))
            .setClaims(authorizationClaims)
            .setTenantFilter(TenantFilter.ASSIGNED);

    // Stream using PROVIDED filter for tenantIdB — will consume tenantIdB jobs
    final JobActivationPropertiesImpl providedProperties =
        new JobActivationPropertiesImpl()
            .setWorker(worker, 0, worker.capacity())
            .setTimeout(timeout)
            .setFetchVariables(
                List.of(new StringValue("a"), new StringValue("b"), new StringValue("c")))
            .setClaims(authorizationClaims)
            .setTenantIds(List.of(tenantIdB));

    final var assignedStream = JOB_STREAMER.addJobStream(jobTypeBuffer, assignedProperties);
    final var providedStream = JOB_STREAMER.addJobStream(jobTypeBuffer, providedProperties);

    // when — create a job for tenantIdA (user's assigned tenant)
    final long jobKey = createJob(jobType, PROCESS_ID, variables, tenantIdA);

    // then
    final Record<JobBatchRecordValue> batchRecord =
        jobBatchRecords(JobBatchIntent.ACTIVATED).withType(jobType).getFirst();

    final JobBatchRecordValue batch = batchRecord.getValue();
    assertThat(batch.getJobs()).hasSize(1);
    assertThat(batch.getJobKeys()).contains(jobKey);

    // The ASSIGNED stream should receive the job because the user is assigned to tenantIdA
    assertActivatedJob(assignedStream, jobKey, worker, variables, 1, tenantIdA);
    // The PROVIDED stream for tenantIdB should NOT receive it
    assertNoActivatedJobs(providedStream);
  }

  @Test
  public void shouldPushToAssignedTenantStreamWhenUsingAssignedFilterIgnoringSetTenantIds() {
    // given
    final String jobType = Strings.newRandomValidBpmnId();
    final DirectBuffer jobTypeBuffer = BufferUtil.wrapString(jobType);
    final DirectBuffer worker = BufferUtil.wrapString("test");
    final Map<String, Object> variables = Map.of("a", "valA", "b", "valB", "c", "valC");
    final long timeout = 30000L;
    final String username = Strings.newRandomValidIdentityId();
    final String tenantIdA = Strings.newRandomValidTenantId();
    final String tenantIdB = Strings.newRandomValidTenantId();
    final String tenantIdC = Strings.newRandomValidTenantId();

    ENGINE.tenant().newTenant().withTenantId(tenantIdA).create();
    ENGINE.tenant().newTenant().withTenantId(tenantIdB).create();
    ENGINE.tenant().newTenant().withTenantId(tenantIdC).create();
    ENGINE.user().newUser(username).create();
    // User is assigned to tenantIdA and tenantC only — not tenantIdB
    ENGINE
        .tenant()
        .addEntity(tenantIdA)
        .withEntityId(username)
        .withEntityType(EntityType.USER)
        .add();
    ENGINE
        .tenant()
        .addEntity(tenantIdC)
        .withEntityId(username)
        .withEntityType(EntityType.USER)
        .add();
    final Map<String, Object> authorizationClaims = Map.of(AUTHORIZED_USERNAME, username);

    // Stream using ASSIGNED filter passing in tenantA— should receive jobs for tenantIdA and
    // tenantIdC (assigned tenant)
    final JobActivationPropertiesImpl assignedProperties =
        new JobActivationPropertiesImpl()
            .setWorker(worker, 0, worker.capacity())
            .setTimeout(timeout)
            .setFetchVariables(
                List.of(new StringValue("a"), new StringValue("b"), new StringValue("c")))
            .setClaims(authorizationClaims)
            .setTenantIds(List.of(tenantIdA))
            .setTenantFilter(TenantFilter.ASSIGNED);

    // Stream using PROVIDED filter for tenantIdB — will consume tenantIdB jobs
    final JobActivationPropertiesImpl providedProperties =
        new JobActivationPropertiesImpl()
            .setWorker(worker, 0, worker.capacity())
            .setTimeout(timeout)
            .setFetchVariables(
                List.of(new StringValue("a"), new StringValue("b"), new StringValue("c")))
            .setClaims(authorizationClaims)
            .setTenantIds(List.of(tenantIdB));

    final var assignedStream = JOB_STREAMER.addJobStream(jobTypeBuffer, assignedProperties);
    final var providedStream = JOB_STREAMER.addJobStream(jobTypeBuffer, providedProperties);

    // when — create a job for tenantIdA (user's assigned tenant)
    final long jobKeyTenantA = createJob(jobType, PROCESS_ID, variables, tenantIdA);

    // when — create a job for tenantIdC (user's assigned tenant)
    final long jobKeyTenantC = createJob(jobType, PROCESS_ID, variables, tenantIdC);

    // then — each push creates a separate ACTIVATED batch record
    final var batchRecords =
        jobBatchRecords(JobBatchIntent.ACTIVATED).withType(jobType).limit(2).toList();
    assertThat(batchRecords).hasSize(2);

    // The ASSIGNED stream should receive both jobs (tenantIdA and tenantIdC)
    await("waiting for both jobs to be pushed to the assigned stream")
        .atMost(MAX_WAIT_TIME_FOR_ACTIVATED_JOBS)
        .pollInterval(Duration.ofMillis(10))
        .untilAsserted(() -> assertThat(assignedStream.getActivatedJobs()).hasSize(2));

    final var activatedJobs = assignedStream.getActivatedJobs();
    assertThat(activatedJobs)
        .extracting(job -> job.jobRecord().getTenantId())
        .containsExactlyInAnyOrder(tenantIdA, tenantIdC);
    assertThat(activatedJobs)
        .extracting(ActivatedJob::jobKey)
        .containsExactlyInAnyOrder(jobKeyTenantA, jobKeyTenantC);

    // The PROVIDED stream for tenantIdB should NOT receive any jobs
    assertNoActivatedJobs(providedStream);
  }

  @Test
  public void shouldNotPushToAssignedStreamWhenClaimsAreAnonymous() {
    // given
    final String jobType = Strings.newRandomValidBpmnId();
    final DirectBuffer jobTypeBuffer = BufferUtil.wrapString(jobType);
    final DirectBuffer worker = BufferUtil.wrapString("test");
    final Map<String, Object> variables = Map.of("a", "valA");
    final long timeout = 30000L;
    final String username = Strings.newRandomValidIdentityId();
    final String tenantIdA = Strings.newRandomValidTenantId();

    ENGINE.tenant().newTenant().withTenantId(tenantIdA).create();
    ENGINE.user().newUser(username).create();
    ENGINE
        .tenant()
        .addEntity(tenantIdA)
        .withEntityId(username)
        .withEntityType(EntityType.USER)
        .add();

    // Stream using PROVIDED filter with real claims — will consume the job
    final Map<String, Object> authorizationClaims = Map.of(AUTHORIZED_USERNAME, username);
    final JobActivationPropertiesImpl providedProperties =
        new JobActivationPropertiesImpl()
            .setWorker(worker, 0, worker.capacity())
            .setTimeout(timeout)
            .setFetchVariables(List.of(new StringValue("a")))
            .setClaims(authorizationClaims)
            .setTenantIds(List.of(tenantIdA));

    // Stream using ASSIGNED filter with anonymous claims — should NOT receive jobs
    final JobActivationPropertiesImpl anonymousAssignedProperties =
        new JobActivationPropertiesImpl()
            .setWorker(worker, 0, worker.capacity())
            .setTimeout(timeout)
            .setFetchVariables(List.of(new StringValue("a")))
            .setClaims(Map.of(Authorization.AUTHORIZED_ANONYMOUS_USER, true))
            .setTenantFilter(TenantFilter.ASSIGNED);

    final var providedStream = JOB_STREAMER.addJobStream(jobTypeBuffer, providedProperties);
    final var anonymousStream =
        JOB_STREAMER.addJobStream(jobTypeBuffer, anonymousAssignedProperties);

    // when
    final long jobKey = createJob(jobType, PROCESS_ID, variables, tenantIdA);

    // then — wait for the job batch to be activated (ensures push side effect has completed)
    jobBatchRecords(JobBatchIntent.ACTIVATED).withType(jobType).getFirst();

    // the PROVIDED stream should receive the job
    assertActivatedJob(providedStream, jobKey, worker, variables, 1, tenantIdA);
    // The anonymous ASSIGNED stream should NOT receive the job
    assertNoActivatedJobs(anonymousStream);
  }

  private Long createJob(
      final String jobType,
      final String processId,
      final Map<String, Object> variables,
      final String tenantId) {
    final Record<JobRecordValue> jobRecord =
        ENGINE.createJob(jobType, processId, variables, tenantId);
    return jobRecord.getKey();
  }

  private void assertActivatedJob(
      final RecordingJobStream jobStream,
      final Long jobKey,
      final DirectBuffer worker,
      final Map variables,
      final int activationCount,
      final String tenantId) {
    await("waiting for the expected number of jobs to be activated")
        .atMost(MAX_WAIT_TIME_FOR_ACTIVATED_JOBS)
        .pollInterval(Duration.ofMillis(10))
        .untilAsserted(() -> assertThat(jobStream.getActivatedJobs()).hasSize(activationCount));

    jobStream
        .getActivatedJobs()
        .forEach(
            activatedJob -> {
              assertThat(activatedJob.jobKey()).isEqualTo(jobKey);

              final JobRecord jobRecord = activatedJob.jobRecord();
              assertThat(jobRecord.getWorkerBuffer()).isEqualTo(worker);
              assertThat(jobRecord.getVariables()).isEqualTo(variables);
              assertThat(jobRecord.getTenantId()).isEqualTo(tenantId);
            });
  }

  private void assertNoActivatedJobs(final RecordingJobStream jobStream) {
    assertThat(jobStream.getActivatedJobs()).isEmpty();
  }
}
