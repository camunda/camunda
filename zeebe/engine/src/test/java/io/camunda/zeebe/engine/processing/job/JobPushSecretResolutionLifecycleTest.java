/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordingJobStreamer;
import io.camunda.zeebe.engine.util.RecordingJobStreamer.RecordingJobStream;
import io.camunda.zeebe.engine.util.SecretStoreRegistries;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.impl.stream.job.JobActivationPropertiesImpl;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.SecretReferenceIntent;
import io.camunda.zeebe.protocol.record.value.ResolutionState;
import io.camunda.zeebe.protocol.record.value.SecretReferenceRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;

/**
 * Covers the push activation path parking a job on a non-cached secret reference and the real
 * {@link io.camunda.zeebe.engine.processing.secretreference.SecretResolutionScheduler} resolving
 * it, the push-path counterpart to {@code SecretResolutionLifecycleTest} (long poll). {@code
 * JobSecretPushInjectionTest} covers the push path's own injection logic against hand-written
 * resolution outcomes instead, precisely to stay independent of the scheduler's timing; this test
 * is what exercises the two together.
 */
public final class JobPushSecretResolutionLifecycleTest {

  private static final String PROCESS_ID = "process";
  private static final String TASK_ID = "task";
  private static final String JOB_TYPE = "task-type";
  private static final String SECRET_VALUE = "resolved-secret";
  private static final RecordingJobStreamer JOB_STREAMER = new RecordingJobStreamer();

  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition()
          .withJobStreamer(JOB_STREAMER)
          .withSecretStoreRegistry(
              SecretStoreRegistries.resolvingFromStore(Map.of("token", SECRET_VALUE)))
          // the default interval is 5s, which would make the assertion below wait on it
          .withEngineConfig(config -> config.setSecretResolutionInterval(Duration.ofMillis(100)));

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldResolveParkedSecretAndPushTheJobOnceBackgroundResolutionCompletes() {
    // given - a registered stream, so job creation takes the push path rather than the long poll
    registerJobStream();
    deploy();

    // when - the push finds no cached value for the reference and parks the job
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final long jobKey = awaitJob(processInstanceKey);

    // then - the background resolution succeeds
    final Record<SecretReferenceRecordValue> resolved =
        RecordingExporter.secretReferenceRecords(SecretReferenceIntent.RESOLUTION_COMPLETED)
            .withSecretReference("token")
            .getFirst();
    assertThat(resolved.getValue().getResolutionState()).isEqualTo(ResolutionState.SUCCESS);

    // and - the parked job is reactivated, which is what makes the push path retry it
    final Record<SecretReferenceRecordValue> reactivated =
        RecordingExporter.secretReferenceRecords(SecretReferenceIntent.BATCH_JOBS_REACTIVATED)
            .withSecretReference("token")
            .getFirst();
    assertThat(reactivated.getValue().getJobKeys()).containsExactly(jobKey);
  }

  private RecordingJobStream registerJobStream() {
    final var worker = BufferUtil.wrapString("test");
    final var properties =
        new JobActivationPropertiesImpl()
            .setWorker(worker, 0, worker.capacity())
            .setTimeout(30_000L)
            .setTenantIds(List.of(TenantOwned.DEFAULT_TENANT_IDENTIFIER));
    return JOB_STREAMER.addJobStream(BufferUtil.wrapString(JOB_TYPE), properties);
  }

  /** Returns the key of the job the given instance waits on, once that job exists. */
  private long awaitJob(final long processInstanceKey) {
    return RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .getFirst()
        .getKey();
  }

  private void deploy() {
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask(
                TASK_ID,
                t ->
                    t.zeebeJobType(JOB_TYPE)
                        .zeebeInputExpression(
                            "\"Bearer \" + camunda.secrets.token", "authorization"))
            .endEvent()
            .done();
    engine.deployment().withXmlResource(process).deploy();
  }
}
