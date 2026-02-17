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

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordingJobStreamer;
import io.camunda.zeebe.engine.util.RecordingJobStreamer.RecordingJobStream;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.stream.job.JobActivationPropertiesImpl;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.JobBatchIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.JobBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class MultiTenancyActivatableJobsPushTest {

  private static final String PROCESS_ID = "process";

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

  @Test
  public void shouldPushWhenJobCreatedForAuthorizedTenant() {
    // given
    final String jobType = Strings.newRandomValidBpmnId();
    final DirectBuffer jobTypeBuffer = BufferUtil.wrapString(jobType);
    final DirectBuffer worker = BufferUtil.wrapString("test");
    final Map<String, Object> variables = Map.of("a", "valA", "b", "valB", "c", "valC");
    final long timeout = 30000L;
    final String username = Strings.newRandomValidIdentityId();
    final String tenantIdA = "tenant-a";
    final String tenantIdB = "tenant-b";

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
    final var activatedJobs = jobStream.getActivatedJobs();
    assertThat(activatedJobs).hasSize(activationCount);
    activatedJobs.stream()
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
