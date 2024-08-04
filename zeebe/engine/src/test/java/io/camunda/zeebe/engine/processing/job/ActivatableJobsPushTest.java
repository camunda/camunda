/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import static io.camunda.zeebe.protocol.record.intent.IncidentIntent.CREATED;
import static io.camunda.zeebe.protocol.record.intent.JobIntent.TIMED_OUT;
import static io.camunda.zeebe.test.util.record.RecordingExporter.incidentRecords;
import static io.camunda.zeebe.test.util.record.RecordingExporter.jobBatchRecords;
import static io.camunda.zeebe.test.util.record.RecordingExporter.jobRecords;
import static io.camunda.zeebe.test.util.record.RecordingExporter.records;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordingJobStreamer;
import io.camunda.zeebe.engine.util.RecordingJobStreamer.RecordingJobStream;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.stream.job.JobActivationPropertiesImpl;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.JobBatchIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.JobBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.agrona.DirectBuffer;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class ActivatableJobsPushTest {

  private static final String PROCESS_ID = "process";
  private static final Set<ValueType> JOB_AND_JOB_BATCH_TYPES =
      Set.of(ValueType.JOB, ValueType.JOB_BATCH);
  private static final Duration MAX_WAIT_TIME_FOR_ACTIVATED_JOBS =
      Duration.ofMillis(RecordingExporter.DEFAULT_MAX_WAIT_TIME);

  private static final RecordingJobStreamer JOB_STREAMER = new RecordingJobStreamer();

  @ClassRule
  public static final EngineRule ENGINE =
      EngineRule.singlePartition().withJobStreamer(JOB_STREAMER);

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private RecordingJobStream jobStream;
  private String jobType;
  private DirectBuffer worker;
  private Long timeout;
  private Map<String, Object> variables;
  private final List<Long> activeProcessInstances = new ArrayList<>();

  @Before
  public void setUp() {
    jobType = Strings.newRandomValidBpmnId();
    worker = BufferUtil.wrapString("test");
    variables = Map.of("a", "valA", "b", "valB", "c", "valC");
    timeout = 30_000L;
    final var jobActivationProperties =
        new JobActivationPropertiesImpl()
            .setWorker(worker, 0, worker.capacity())
            .setTimeout(timeout)
            .setTenantIds(List.of(TenantOwned.DEFAULT_TENANT_IDENTIFIER))
            .setFetchVariables(
                List.of(new StringValue("a"), new StringValue("b"), new StringValue("c")));
    jobStream = JOB_STREAMER.addJobStream(BufferUtil.wrapString(jobType), jobActivationProperties);
  }

  @After
  public void tearDown() {
    for (final Long processInstanceKey : activeProcessInstances) {
      ENGINE.processInstance().withInstanceKey(processInstanceKey).cancel();
    }
    activeProcessInstances.clear();
  }

  @Test
  public void shouldPushWhenJobCreated() {
    // given
    final int activationCount = 1;

    // when
    final long jobKey = createJob(jobType, PROCESS_ID, variables);

    // then
    final Record<JobBatchRecordValue> batchRecord =
        jobBatchRecords(JobBatchIntent.ACTIVATED).withType(jobType).getFirst();

    // assert job batch record
    final JobBatchRecordValue batch = batchRecord.getValue();
    final List<JobRecordValue> jobs = batch.getJobs();
    assertThat(jobs).hasSize(1);
    assertThat(batch.getJobKeys()).contains(jobKey);

    assertEventOrder(
        "Expect that the job was activated",
        JOB_AND_JOB_BATCH_TYPES,
        JobIntent.CREATED,
        JobBatchIntent.ACTIVATED);
    assertActivatedJobsPushed(jobKey, activationCount);
  }

  @Test
  public void shouldPushForMultipleJobsCreated() {
    // when
    final int numberOfJobs = 3;
    final List<Long> jobKeys = createJobs(numberOfJobs);

    // then
    jobRecords(JobIntent.CREATED).withType(jobType).await();
    final List<Long> batchJobKeys =
        jobBatchRecords(JobBatchIntent.ACTIVATED)
            .withType(jobType)
            .limit(numberOfJobs)
            .flatMap(record -> record.getValue().getJobKeys().stream())
            .collect(Collectors.toList());
    assertThat(batchJobKeys).isEqualTo(jobKeys);
    assertEventOrder(
        "Expect that 3 jobs were activated",
        JOB_AND_JOB_BATCH_TYPES,
        // 1-st job
        JobIntent.CREATED,
        JobBatchIntent.ACTIVATED,
        // 2-nd job
        JobIntent.CREATED,
        JobBatchIntent.ACTIVATED,
        // 3-d job
        JobIntent.CREATED,
        JobBatchIntent.ACTIVATED);

    await("waiting for the expected number of jobs to be activated")
        .atMost(MAX_WAIT_TIME_FOR_ACTIVATED_JOBS)
        .pollInterval(Duration.ofMillis(10))
        .untilAsserted(() -> assertThat(jobStream.getActivatedJobs()).hasSize(numberOfJobs));

    // assert job stream
    jobStream
        .getActivatedJobs()
        .forEach(
            activatedJob -> {
              final JobRecord jobRecord = activatedJob.jobRecord();
              assertThat(jobRecord.getWorkerBuffer()).isEqualTo(worker);
              assertThat(jobRecord.getVariables()).isEqualTo(variables);
              assertThat(activatedJob.jobKey()).isIn(batchJobKeys);
            });
  }

  @Test
  public void shouldPushWhenJobTimesOut() {
    // given
    final int activationCount = 2;
    final long jobKey = createJob(jobType, PROCESS_ID, variables);
    ENGINE.increaseTime(
        Duration.ofMillis(timeout).plus(EngineConfiguration.DEFAULT_JOBS_TIMEOUT_POLLING_INTERVAL));

    // when
    // job times out
    jobRecords(TIMED_OUT).withType(jobType).await();

    // then
    assertEventOrder(
        "Expect that the job is re-activated after job was timed out",
        JOB_AND_JOB_BATCH_TYPES,
        JobIntent.TIMED_OUT,
        JobBatchIntent.ACTIVATED);
    assertActivatedJobsPushed(jobKey, activationCount);
  }

  @Test
  public void shouldPushAfterJobFailed() {
    // given
    final int activationCount = 2;
    final long jobKey = createJob(jobType, PROCESS_ID, variables);

    // when
    // job is failed with no backoff or incident
    ENGINE.job().withKey(jobKey).withRetries(5).fail();

    // then
    jobRecords(JobIntent.FAILED).withType(jobType).await();
    assertEventOrder(
        "Expect that the job is re-activated after job was failed",
        JOB_AND_JOB_BATCH_TYPES,
        JobIntent.FAILED,
        JobBatchIntent.ACTIVATED);
    assertActivatedJobsPushed(jobKey, activationCount);
  }

  @Test
  public void shouldPushAfterJobBackoff() {
    // given
    // a failed job with a backoff
    final int activationCount = 2;
    final long jobKey = createJob(jobType, PROCESS_ID, variables);
    ENGINE.job().withKey(jobKey).withRetries(5).withBackOff(Duration.ofMillis(10L)).fail();

    // when job recurs
    ENGINE.increaseTime(Duration.ofMillis(JobBackoffChecker.BACKOFF_RESOLUTION));

    // then
    jobRecords(JobIntent.RECURRED_AFTER_BACKOFF).withType(jobType).await();
    assertEventOrder(
        "Expect that the job is re-activated after job was failed with backoff",
        JOB_AND_JOB_BATCH_TYPES,
        JobIntent.RECURRED_AFTER_BACKOFF,
        JobBatchIntent.ACTIVATED);
    assertActivatedJobsPushed(jobKey, activationCount);
  }

  @Test
  public void shouldPushWhenJobIncidentResolves() {
    // given
    final int activationCount = 2;
    // a failed job with no retries, and a raised incident
    final long jobKey = createJob(jobType, PROCESS_ID, variables);
    ENGINE.job().withKey(jobKey).withRetries(0).withErrorMessage("raise incident").fail();
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(CREATED).getFirst();

    // when an incident is resolved
    ENGINE.job().withKey(jobKey).withType(jobType).withRetries(1).updateRetries();
    ENGINE.incident().ofInstance(incident.getValue().getProcessInstanceKey()).resolve();

    // then
    incidentRecords(IncidentIntent.RESOLVED).withJobKey(jobKey).await();
    assertEventOrder(
        "Expect that the job is re-activated after incident was resolved",
        Set.of(ValueType.INCIDENT, ValueType.JOB_BATCH),
        IncidentIntent.RESOLVED,
        JobBatchIntent.ACTIVATED);
    assertActivatedJobsPushed(jobKey, activationCount);
  }

  private List<Long> createJobs(final int amount) {
    return IntStream.range(0, amount)
        .mapToObj(i -> createJob(jobType, PROCESS_ID, variables))
        .collect(Collectors.toList());
  }

  private Long createJob(
      final String jobType, final String processId, final Map<String, Object> variables) {
    final Record<JobRecordValue> jobRecord =
        ENGINE.createJob(jobType, processId, variables, TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    activeProcessInstances.add(jobRecord.getValue().getProcessInstanceKey());
    return jobRecord.getKey();
  }

  private void assertEventOrder(
      final String description,
      final Set<ValueType> targetValueTypes,
      final Intent... expectedIntents) {
    assertThat(
            records()
                .onlyEvents()
                .filter(r -> targetValueTypes.contains(r.getValueType()))
                .skipUntil(r -> r.getIntent() == expectedIntents[0])
                .limit(expectedIntents.length))
        .extracting(Record::getIntent)
        .describedAs(description)
        .containsExactly(expectedIntents);
  }

  private void assertActivatedJobsPushed(final Long jobKey, final int activationCount) {
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
              assertThat(jobRecord.getTenantId()).isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
            });
  }
}
