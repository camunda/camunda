/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.secretstore.NoopSecretStore;
import io.camunda.secretstore.SecretCache;
import io.camunda.secretstore.SecretStoreRegistry;
import io.camunda.zeebe.engine.processing.job.behaviour.JobUpdateBehaviour;
import io.camunda.zeebe.engine.state.immutable.JobState.State;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.SecretReferenceIntent;
import io.camunda.zeebe.protocol.record.value.JobBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.Rule;
import org.junit.Test;

/**
 * Covers a job parked in {@code WAITING_FOR_SECRET_RESOLUTION}: which commands it accepts while it
 * waits, and that it stays parked until the resolution reactivates it.
 */
public final class JobWaitingForSecretResolutionTest {

  private static final String PROCESS_ID = "process";
  private static final String TASK_ID = "task";
  private static final String JOB_TYPE = "task-type";
  private static final String SECRET_NAME = "token";

  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition()
          .withSecretStoreRegistry(
              new SecretStoreRegistry(
                  Map.of("default", new NoopSecretStore()),
                  Map.of("default", new TestSecretCache())));

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private final Map<String, String> cachedSecrets = new HashMap<>();

  @Test
  public void shouldParkJobInWaitingState() {
    // given
    deploy();
    final long jobKey = parkedJob();

    // then - the creation-time warmup also requests this reference (carrying no job key of its
    // own, see SecretResolutionWarmupTest), so the request that actually parked this job is
    // whichever one names its key rather than necessarily the first
    assertThat(
            RecordingExporter.secretReferenceRecords(SecretReferenceIntent.RESOLUTION_REQUESTED)
                .filter(record -> record.getValue().getJobKeys().contains(jobKey))
                .getFirst()
                .getValue()
                .getJobKeys())
        .contains(jobKey);
  }

  @Test
  public void shouldRejectCompleteOfWaitingJob() {
    // given
    deploy();
    final long jobKey = parkedJob();

    // when
    final Record<JobRecordValue> rejection =
        engine.job().withKey(jobKey).expectRejection().complete();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason())
        .isEqualTo(
            "Expected to complete job with key '%d', but it is in state '%s'"
                .formatted(jobKey, State.WAITING_FOR_SECRET_RESOLUTION));
  }

  @Test
  public void shouldRejectFailOfWaitingJob() {
    // given
    deploy();
    final long jobKey = parkedJob();

    // when
    final Record<JobRecordValue> rejection =
        engine.job().withKey(jobKey).withRetries(3).expectRejection().fail();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason()).contains(State.WAITING_FOR_SECRET_RESOLUTION.name());
  }

  @Test
  public void shouldRejectThrowErrorOfWaitingJob() {
    // given
    deploy();
    final long jobKey = parkedJob();

    // when
    final Record<JobRecordValue> rejection =
        engine.job().withKey(jobKey).withErrorCode("error").expectRejection().throwError();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason()).contains(State.WAITING_FOR_SECRET_RESOLUTION.name());
  }

  @Test
  public void shouldRejectYieldOfWaitingJob() {
    // given
    deploy();
    final long jobKey = parkedJob();

    // when
    final Record<JobRecordValue> rejection = engine.job().withKey(jobKey).expectRejection().yield();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason()).contains(State.WAITING_FOR_SECRET_RESOLUTION.name());
  }

  @Test
  public void shouldRejectTimeoutUpdateOfWaitingJob() {
    // given - a parked job has no deadline, because it was never handed to a worker
    deploy();
    final long jobKey = parkedJob();

    // when
    final Record<JobRecordValue> rejection =
        engine.job().withKey(jobKey).withTimeout(1000L).expectRejection().updateTimeout();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason())
        .isEqualTo(JobUpdateBehaviour.NO_DEADLINE_FOUND_MESSAGE.formatted(jobKey));
  }

  @Test
  public void shouldKeepJobParkedOnPriorityUpdate() {
    // given - a parked job whose secret is cached by now, so only the parking keeps it from being
    // activated
    deploy();
    final long jobKey = parkedJob();
    cachedSecrets.put(SECRET_NAME, "resolved-secret");

    // when
    final Record<JobRecordValue> updated =
        engine.job().withKey(jobKey).withPriority(42).withChangeset(Set.of("priority")).update();

    // then - the new priority is applied to the job
    assertThat(updated.getIntent()).isEqualTo(JobIntent.PRIORITY_UPDATED);
    assertThat(updated.getValue().getPriority()).isEqualTo(42);

    // and - the job is still parked: it is not handed out to the next activation. The parked state
    // itself is asserted in JobStateTest, where the read cannot race the engine's own transaction.
    final Record<JobBatchRecordValue> activated =
        engine.jobs().withType(JOB_TYPE).withRequestStreamId(2).withRequestId(2L).activate();
    assertThat(activated.getValue().getJobs()).isEmpty();
  }

  @Test
  public void shouldUpdateRetriesOfWaitingJob() {
    // given
    deploy();
    final long jobKey = parkedJob();

    // when
    final Record<JobRecordValue> updated =
        engine.job().withKey(jobKey).withRetries(5).updateRetries();

    // then
    assertThat(updated.getIntent()).isEqualTo(JobIntent.RETRIES_UPDATED);
    assertThat(updated.getValue().getRetries()).isEqualTo(5);

    // and - the job is still parked: a subsequent activation does not hand it out
    final Record<JobBatchRecordValue> activated =
        engine.jobs().withType(JOB_TYPE).withRequestStreamId(2).withRequestId(2L).activate();
    assertThat(activated.getValue().getJobs()).isEmpty();
  }

  @Test
  public void shouldCancelWaitingJobOnProcessInstanceTermination() {
    // given
    deploy();
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final long jobKey = parkJobOf(processInstanceKey);

    // when
    engine.processInstance().withInstanceKey(processInstanceKey).cancel();

    // then - the parked job is canceled, leaving nothing behind
    assertThat(RecordingExporter.jobRecords(JobIntent.CANCELED).withRecordKey(jobKey).exists())
        .isTrue();
  }

  /** Creates an instance and activates once, which parks its job on the uncached secret. */
  private long parkedJob() {
    return parkJobOf(engine.processInstance().ofBpmnProcessId(PROCESS_ID).create());
  }

  private long parkJobOf(final long processInstanceKey) {
    final long jobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getKey();
    final Record<JobBatchRecordValue> activated =
        engine.jobs().withType(JOB_TYPE).withRequestStreamId(1).withRequestId(1L).activate();
    // the job is withheld from the batch and parked instead, because its secret is not cached
    assertThat(activated.getValue().getJobs()).isEmpty();
    return jobKey;
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
                            "\"Bearer \" + camunda.secrets." + SECRET_NAME, "authorization"))
            .endEvent()
            .done();
    engine.deployment().withXmlResource(process).deploy();
  }

  /** Serves the test's cached secrets; empty until a test puts a value in. */
  private final class TestSecretCache implements SecretCache {
    @Override
    public Optional<String> get(final String name) {
      return Optional.ofNullable(cachedSecrets.get(name));
    }

    @Override
    public void put(final String name, final String value) {
      cachedSecrets.put(name, value);
    }

    @Override
    public void remove(final String name) {
      cachedSecrets.remove(name);
    }
  }
}
