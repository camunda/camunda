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
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.stream.job.JobActivationPropertiesImpl;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.Duration;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class JobPriorityStreamingActivationTest {

  private static final String PROCESS_ID = "priority-streaming-process";
  private static final long TIMEOUT_MS = 500L;
  private static final RecordingJobStreamer JOB_STREAMER = new RecordingJobStreamer();

  @ClassRule
  public static final EngineRule ENGINE =
      EngineRule.singlePartition().withJobStreamer(JOB_STREAMER);

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

  private RecordingJobStream jobStream;
  private String jobType;

  @Before
  public void setUp() {
    jobType = Strings.newRandomValidBpmnId();
    final var worker = BufferUtil.wrapString("test");
    final var properties =
        new JobActivationPropertiesImpl()
            .setWorker(worker, 0, worker.capacity())
            .setTimeout(TIMEOUT_MS)
            .setTenantIds(List.of(TenantOwned.DEFAULT_TENANT_IDENTIFIER));
    jobStream = JOB_STREAMER.addJobStream(BufferUtil.wrapString(jobType), properties);
  }

  @After
  public void tearDown() {
    JOB_STREAMER.clearStreams();
  }

  @Test
  public void shouldLeaveNoStaleCfEntryAfterStreamingActivationOfNonZeroPriorityJob() {
    // given
    deployPriorityProcess(50);
    ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when — streaming path activates the job
    await().untilAsserted(() -> assertThat(jobStream.getActivatedJobs()).hasSize(1));

    // then — streaming batch event carries the correct priority
    final var streamedBatch = jobBatchRecords(ACTIVATED).withType(jobType).getFirst();
    assertThat(streamedBatch.getValue().getJobs().get(0).getPriority()).isEqualTo(50);

    // and — a subsequent pull returns no jobs (no stale entry in JOB_ACTIVATABLE_BY_PRIORITY).
    // This is the primary no-double-activation assertion: if a stale CF entry existed the pull
    // would find the already-activated job and activate it a second time.
    final var pullResponse = ENGINE.jobs().withType(jobType).activate();
    assertThat(pullResponse.getValue().getJobKeys()).isEmpty();
  }

  @Test
  public void shouldLeaveNoStaleCfEntryAfterStreamingActivationOfDefaultPriorityJob() {
    // given
    deployDefaultPriorityProcess();
    ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    await().untilAsserted(() -> assertThat(jobStream.getActivatedJobs()).hasSize(1));

    // then
    final var streamedBatch = jobBatchRecords(ACTIVATED).withType(jobType).getFirst();
    assertThat(streamedBatch.getValue().getJobs().get(0).getPriority()).isZero();

    final var pullResponse = ENGINE.jobs().withType(jobType).activate();
    assertThat(pullResponse.getValue().getJobKeys()).isEmpty();
  }

  @Test
  public void shouldPreserveOriginalPriorityAfterStreamedJobTimesOut() {
    // given
    deployPriorityProcess(50);
    ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    await().untilAsserted(() -> assertThat(jobStream.getActivatedJobs()).hasSize(1));

    // when — advance clock past the job timeout
    ENGINE.increaseTime(
        Duration.ofMillis(TIMEOUT_MS)
            .plus(EngineConfiguration.DEFAULT_JOBS_TIMEOUT_POLLING_INTERVAL));
    jobRecords(TIMED_OUT).withType(jobType).await();

    // then — pull worker activates the timed-out job at the original priority
    final var pullResponse = ENGINE.jobs().withType(jobType).activate();
    assertThat(pullResponse.getValue().getJobKeys()).hasSize(1);
    assertThat(pullResponse.getValue().getJobs().get(0).getPriority()).isEqualTo(50);

    // and — exactly two ACTIVATED events: one streaming, one pull (no duplicate)
    // exactly two non-empty ACTIVATED events: one streaming, one pull (no duplicate)
    assertThat(
            jobBatchRecords(ACTIVATED)
                .withType(jobType)
                .filter(r -> !r.getValue().getJobKeys().isEmpty())
                .limit(2)
                .count())
        .isEqualTo(2);
  }

  @Test
  public void shouldPreserveOriginalPriorityAfterStreamedJobFailsAndSubscriberDisconnects() {
    // given
    deployPriorityProcess(50);
    ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    await().untilAsserted(() -> assertThat(jobStream.getActivatedJobs()).hasSize(1));
    final long jobKey = jobStream.getActivatedJobs().get(0).jobKey();

    // when — disconnect the streaming subscriber FIRST, then fail the job
    // IMPORTANT: clearStreams() must be called BEFORE fail(). If the subscriber is still
    // registered when fail() is called, JobFailProcessor calls publishWork() which re-streams
    // the job immediately, bypassing JOB_ACTIVATABLE_BY_PRIORITY entirely.
    JOB_STREAMER.clearStreams();
    ENGINE.job().withKey(jobKey).withRetries(3).fail();

    // then — pull worker activates the failed job at the original priority
    final var pullResponse = ENGINE.jobs().withType(jobType).activate();
    assertThat(pullResponse.getValue().getJobKeys()).hasSize(1);
    assertThat(pullResponse.getValue().getJobs().get(0).getPriority()).isEqualTo(50);
  }

  private void deployPriorityProcess(final int priority) {
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask(
                    "task", b -> b.zeebeJobType(jobType).zeebeJobPriority(String.valueOf(priority)))
                .endEvent()
                .done())
        .deploy();
  }

  private void deployDefaultPriorityProcess() {
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask("task", b -> b.zeebeJobType(jobType))
                .endEvent()
                .done())
        .deploy();
  }
}
