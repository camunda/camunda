/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.job;

import static io.camunda.zeebe.protocol.record.intent.IncidentIntent.CREATED;
import static io.camunda.zeebe.protocol.record.intent.JobIntent.TIMED_OUT;
import static io.camunda.zeebe.test.util.record.RecordingExporter.incidentRecords;
import static io.camunda.zeebe.test.util.record.RecordingExporter.jobBatchRecords;
import static io.camunda.zeebe.test.util.record.RecordingExporter.jobRecords;
import static io.camunda.zeebe.test.util.record.RecordingExporter.records;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.processing.streamprocessor.JobActivationProperties;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordingJobStreamer;
import io.camunda.zeebe.engine.util.RecordingJobStreamer.RecordingJobStream;
import io.camunda.zeebe.engine.util.RecordingJobStreamer.TestActivationProperties;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.JobBatchIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.JobBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

  private static final RecordingJobStreamer JOB_STREAMER = new RecordingJobStreamer();

  @ClassRule
  public static final EngineRule ENGINE =
      EngineRule.singlePartition().withJobStreamer(JOB_STREAMER);

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private RecordingJobStream jobStream;
  private JobActivationProperties jobActivationProperties;
  private String jobType;
  private DirectBuffer jobTypeBuffer;
  private DirectBuffer worker;
  private Long timeout;
  private Map<String, Object> variables;
  private final List<Long> activeProcessInstances = new ArrayList<>();

  @Before
  public void setUp() {
    jobType = Strings.newRandomValidBpmnId();
    jobTypeBuffer = BufferUtil.wrapString(jobType);
    worker = BufferUtil.wrapString("test");
    variables = Map.of("a", "valA", "b", "valB", "c", "valC");
    timeout = 30000L;

    jobActivationProperties =
        new TestActivationProperties(
            worker,
            timeout,
            List.of(
                BufferUtil.wrapString("a"),
                BufferUtil.wrapString("b"),
                BufferUtil.wrapString("c")));
    jobStream = JOB_STREAMER.addJobStream(jobTypeBuffer, jobActivationProperties);
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
    // givan
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

    // assert event order
    assertEventOrder(JobIntent.CREATED, JobBatchIntent.ACTIVATED);

    // assert job stream
    assertActivatedJob(jobKey, activationCount);
  }

  @Test
  public void shouldPushForMultipleJobsCreated() {
    // when
    final int numberOfJobs = 3;
    final List<Long> jobKeys = createJobs(numberOfJobs);

    // then
    jobRecords(JobIntent.CREATED).withType(jobType).await();
    final List<Record<JobBatchRecordValue>> batches =
        RecordingExporter.jobBatchRecords(JobBatchIntent.ACTIVATED).withType(jobType).asList();
    assertThat(batches).hasSize(3);
    final List<Long> batchJobKeys =
        batches.stream()
            .flatMap(record -> record.getValue().getJobKeys().stream())
            .collect(Collectors.toList());
    assertThat(batchJobKeys).containsAnyElementsOf(jobKeys);

    assertEventOrder(JobIntent.CREATED, JobBatchIntent.ACTIVATED);

    jobStream.getActivatedJobs().stream()
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
    ENGINE.increaseTime(JobTimeoutTrigger.TIME_OUT_POLLING_INTERVAL);

    // when
    // job times out
    jobRecords(TIMED_OUT).withType(jobType).await();

    // then
    assertJobActivations(activationCount);
    assertEventOrder(JobIntent.TIME_OUT, JobIntent.TIMED_OUT, JobBatchIntent.ACTIVATED);
    assertActivatedJob(jobKey, activationCount);
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
    assertJobActivations(activationCount);
    assertEventOrder(JobIntent.FAIL, JobIntent.FAILED, JobBatchIntent.ACTIVATED);
    assertActivatedJob(jobKey, activationCount);
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
    assertJobActivations(activationCount);
    assertEventOrder(
        JobIntent.RECUR_AFTER_BACKOFF, JobIntent.RECURRED_AFTER_BACKOFF, JobBatchIntent.ACTIVATED);
    assertActivatedJob(jobKey, activationCount);
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
    ENGINE.incident().ofInstance(incident.getValue().getProcessInstanceKey()).resolve();

    // then
    incidentRecords(IncidentIntent.RESOLVED).withJobKey(jobKey).await();
    assertJobActivations(activationCount);
    assertEventOrder(IncidentIntent.RESOLVE, IncidentIntent.RESOLVED, JobBatchIntent.ACTIVATED);
    assertActivatedJob(jobKey, activationCount);
  }

  private List<Long> createJobs(final int amount) {
    return IntStream.range(0, amount)
        .mapToObj(i -> createJob(jobType, PROCESS_ID, variables))
        .collect(Collectors.toList());
  }

  private Long createJob(
      final String jobType, final String processId, final Map<String, Object> variables) {
    final Record<JobRecordValue> jobRecord = ENGINE.createJob(jobType, processId, variables);
    activeProcessInstances.add(jobRecord.getValue().getProcessInstanceKey());
    return jobRecord.getKey();
  }

  private void assertEventOrder(final Intent... eventOrder) {
    for (final long piKey : activeProcessInstances) {
      final var processInstanceRecordStream = records().betweenProcessInstance(piKey);
      assertThat(processInstanceRecordStream)
          .extracting(Record::getIntent)
          .containsSequence(eventOrder);
    }
  }

  private void assertJobActivations(final int activationCount) {
    final List<Record<JobBatchRecordValue>> batchRecord =
        RecordingExporter.jobBatchRecords(JobBatchIntent.ACTIVATED).withType(jobType).asList();
    assertThat(batchRecord).hasSize(activationCount);
  }

  private void assertActivatedJob(final Long jobKey, final int activationCount) {
    final var activatedJobs = jobStream.getActivatedJobs();
    assertThat(activatedJobs).hasSize(activationCount);
    activatedJobs.stream()
        .forEach(
            activatedJob -> {
              assertThat(activatedJob.jobKey()).isEqualTo(jobKey);

              final JobRecord jobRecord = activatedJob.jobRecord();
              assertThat(jobRecord.getWorkerBuffer()).isEqualTo(worker);
              assertThat(jobRecord.getVariables()).isEqualTo(variables);
            });
  }
}
