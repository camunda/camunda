/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.secretreference;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.SecretStoreRegistries;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.SecretReferenceIntent;
import io.camunda.zeebe.protocol.record.value.JobBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.ResolutionState;
import io.camunda.zeebe.protocol.record.value.SecretReferenceRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;

/**
 * Covers the warmup this behavior adds on top of {@link SecretResolutionLifecycleTest}'s park/drain
 * loop: a job with a non-cached secret reference requests its resolution as soon as the job is
 * created, without parking the job, so a long poll that arrives after the resolution completes
 * needs no round trip of its own. A long poll that arrives before the resolution completes still
 * parks the job and drains it exactly as {@link SecretResolutionLifecycleTest} covers - this warmup
 * only gives that resolution a head start, it never replaces the poll-driven path.
 */
public final class SecretResolutionWarmupTest {

  private static final String PROCESS_ID = "process";
  private static final String TASK_ID = "task";
  private static final String JOB_TYPE = "task-type";
  private static final String SECRET_VALUE = "resolved-secret";

  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition()
          .withSecretStoreRegistry(
              SecretStoreRegistries.resolvingFromStore(Map.of("token", SECRET_VALUE)))
          // the default interval is 5s, which would make every assertion below wait on it
          .withEngineConfig(config -> config.setSecretResolutionInterval(Duration.ofMillis(100)));

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldRequestResolutionAtJobCreationWithoutParkingTheJob() {
    // given - a job whose secret the store holds but the cache does not
    deploy();

    // when - the job is created; no activation has been requested yet
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final long jobKey = awaitJob(processInstanceKey);

    // then - the resolution is requested right away, addressing no particular job
    final Record<SecretReferenceRecordValue> requested =
        RecordingExporter.secretReferenceRecords(SecretReferenceIntent.RESOLUTION_REQUESTED)
            .withSecretReference("token")
            .getFirst();
    assertThat(requested.getValue().getJobKeys()).isEmpty();

    // and - the background resolution succeeds before any poll ever reaches this job
    final Record<SecretReferenceRecordValue> resolved =
        RecordingExporter.secretReferenceRecords(SecretReferenceIntent.RESOLUTION_COMPLETED)
            .withSecretReference("token")
            .getFirst();
    assertThat(resolved.getValue().getResolutionState()).isEqualTo(ResolutionState.SUCCESS);

    // and - the job was never parked: nothing ever drains a waiting entry for this reference
    assertThat(RecordingExporter.getRecords())
        .filteredOn(record -> record.getIntent() == SecretReferenceIntent.BATCH_JOBS_REACTIVATED)
        .isEmpty();

    // when - the first ever poll for this job arrives
    final Record<JobBatchRecordValue> activated =
        engine.jobs().withType(JOB_TYPE).withRequestStreamId(1).withRequestId(1L).activate();

    // then - a single poll is enough: the value was already cached by the warmup
    assertThat(activated.getValue().getJobKeys()).containsExactly(jobKey);

    // and - the job completes and the instance reaches its end, so nothing further will be
    // processed: only then does the absence of an incident below mean the run never raised one
    engine.job().withKey(jobKey).complete();
    RecordingExporter.jobRecords(JobIntent.COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .getFirst();
    assertThat(RecordingExporter.getRecords())
        .filteredOn(record -> record.getIntent() == IncidentIntent.CREATED)
        .isEmpty();
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
