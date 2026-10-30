/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.secretstore.SecretErrorCode;
import io.camunda.secretstore.SecretResolutionResult;
import io.camunda.secretstore.SecretResolutionResult.Failed;
import io.camunda.secretstore.SecretResolutionResult.Resolved;
import io.camunda.secretstore.SecretStore;
import io.camunda.secretstore.SecretStoreRegistry;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordingJobStreamer;
import io.camunda.zeebe.engine.util.RecordingJobStreamer.RecordingJobStream;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.stream.job.JobActivationPropertiesImpl;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.SecretReferenceIntent;
import io.camunda.zeebe.protocol.record.value.JobBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

/**
 * Covers resume of a job that was suspended while waiting for secret resolution. Resume must mirror
 * long-poll activation: restart resolution when references are uncached and withhold hand-out;
 * publish work immediately when secrets are already cached.
 */
public final class JobSuspensionSecretResumeTest {

  private static final String SECRET_NAME = "token";
  private static final String SECRET_VALUE = "resolved-secret";

  @Rule public final EngineRule engine;
  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();

  private final CountDownLatch allowResolve = new CountDownLatch(1);
  private final RecordingJobStreamer jobStreamer = new RecordingJobStreamer();

  public JobSuspensionSecretResumeTest() {
    engine =
        EngineRule.singlePartition()
            .withJobStreamer(jobStreamer)
            .withSecretStoreRegistry(
                new SecretStoreRegistry(
                    Map.of(
                        SecretStoreRegistry.DEFAULT_STORE_ID,
                        new GatedSecretStore(allowResolve, Map.of(SECRET_NAME, SECRET_VALUE)))))
            .withEngineConfig(config -> config.setSecretResolutionInterval(Duration.ofMillis(100)));
  }

  @After
  public void openGate() {
    // unblock any store call still waiting if the test failed before opening the gate
    allowResolve.countDown();
  }

  @Test
  public void shouldNotHandOutSecretWaitingJobOnResumeBeforeResolution() {
    // given - a job parked for an uncached secret, suspended while resolution is still gated
    final String jobType = Strings.newRandomValidBpmnId();
    final long processInstanceKey = createSecretJobInstance(jobType);
    final long jobKey = jobKeyOf(processInstanceKey);

    parkForSecret(jobType, jobKey);
    engine.processInstance().withInstanceKey(processInstanceKey).suspend();
    RecordingExporter.jobRecords(JobIntent.SUSPENDED).withRecordKey(jobKey).await();

    final RecordingJobStream jobStream = addJobStream(jobType);

    // when - resume while the secret store is still blocked
    engine.processInstance().withInstanceKey(processInstanceKey).resume();
    final var resumed =
        RecordingExporter.jobRecords(JobIntent.RESUMED).withRecordKey(jobKey).getFirst();

    // then - the RESUME_JOBS cycle for this job restarts resolution; neither stream push nor poll
    // hands the job out
    final var reRequested =
        RecordingExporter.secretReferenceRecords(SecretReferenceIntent.RESOLUTION_REQUESTED)
            .withSecretReference(SECRET_NAME)
            .skipUntil(r -> r.getPosition() > resumed.getPosition())
            .getFirst();
    assertThat(reRequested.getValue().getJobKeys()).contains(jobKey);
    assertThat(jobStream.getActivatedJobs()).isEmpty();

    final Record<JobBatchRecordValue> whileWaiting =
        engine.jobs().withType(jobType).withRequestStreamId(2).withRequestId(2L).activate();
    assertThat(whileWaiting.getValue().getJobKeys()).isEmpty();

    // and - once the store answers, reactivation hands the job to the registered stream: the same
    // publishWork call that resume itself would have made, now made by the resolution's own
    // reactivation cycle (see SecretReferenceBatchReactivateJobsProcessor)
    allowResolve.countDown();
    await("push once the secret resolves")
        .untilAsserted(() -> assertThat(jobStream.getActivatedJobs()).hasSize(1));
    assertThat(jobStream.getActivatedJobs().get(0).jobKey()).isEqualTo(jobKey);
  }

  @Test
  public void shouldPushJobOnResumeWhenSecretsAlreadyCached() {
    // given - secrets resolve while the instance is still suspended, so the cache is warm at resume
    final String jobType = Strings.newRandomValidBpmnId();
    final long processInstanceKey = createSecretJobInstance(jobType);
    final long jobKey = jobKeyOf(processInstanceKey);

    parkForSecret(jobType, jobKey);
    engine.processInstance().withInstanceKey(processInstanceKey).suspend();
    RecordingExporter.jobRecords(JobIntent.SUSPENDED).withRecordKey(jobKey).await();

    allowResolve.countDown();
    RecordingExporter.secretReferenceRecords(SecretReferenceIntent.RESOLUTION_COMPLETED)
        .withSecretReference(SECRET_NAME)
        .getFirst();
    RecordingExporter.secretReferenceRecords(SecretReferenceIntent.BATCH_JOBS_REACTIVATED)
        .withSecretReference(SECRET_NAME)
        .getFirst();

    // job stays out of the hand-out index while suspended (see JobSuspensionSecretWaitingTest)
    final Record<JobBatchRecordValue> whileSuspended =
        engine.jobs().withType(jobType).withRequestStreamId(2).withRequestId(2L).activate();
    assertThat(whileSuspended.getValue().getJobKeys()).isEmpty();

    final RecordingJobStream jobStream = addJobStream(jobType);

    // when - resume with secrets already cached
    engine.processInstance().withInstanceKey(processInstanceKey).resume();
    RecordingExporter.jobRecords(JobIntent.RESUMED).withRecordKey(jobKey).await();

    // then - the RESUME_JOBS cycle publishes work immediately (stream push), without restarting
    // resolution
    await("push after resume with cached secrets")
        .untilAsserted(() -> assertThat(jobStream.getActivatedJobs()).hasSize(1));
    assertThat(jobStream.getActivatedJobs().get(0).jobKey()).isEqualTo(jobKey);
  }

  private RecordingJobStream addJobStream(final String jobType) {
    final DirectBuffer worker = BufferUtil.wrapString("test");
    final var jobActivationProperties =
        new JobActivationPropertiesImpl()
            .setWorker(worker, 0, worker.capacity())
            .setTimeout(30_000L)
            .setTenantIds(List.of(TenantOwned.DEFAULT_TENANT_IDENTIFIER))
            .setFetchVariables(Set.of(new StringValue("authorization")));
    return jobStreamer.addJobStream(BufferUtil.wrapString(jobType), jobActivationProperties);
  }

  private long createSecretJobInstance(final String jobType) {
    final String processId = Strings.newRandomValidBpmnId();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask(
                    "task",
                    t ->
                        t.zeebeJobType(jobType)
                            .zeebeInputExpression(
                                "\"Bearer \"+camunda.secrets." + SECRET_NAME, "authorization"))
                .done())
        .deploy();
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();
    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();
    return processInstanceKey;
  }

  private long jobKeyOf(final long processInstanceKey) {
    return RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .getFirst()
        .getKey();
  }

  private void parkForSecret(final String jobType, final long jobKey) {
    final Record<JobBatchRecordValue> parked =
        engine.jobs().withType(jobType).withRequestStreamId(1).withRequestId(1L).activate();
    assertThat(parked.getValue().getJobs()).isEmpty();
    final var requested =
        RecordingExporter.secretReferenceRecords(SecretReferenceIntent.RESOLUTION_REQUESTED)
            .withSecretReference(SECRET_NAME)
            .getFirst();
    assertThat(requested.getValue().getJobKeys()).contains(jobKey);
  }

  /**
   * Blocks {@link #resolve} until the test opens the latch, so secret completion cannot race past
   * process-instance suspend or resume.
   */
  private record GatedSecretStore(CountDownLatch allowResolve, Map<String, String> secrets)
      implements SecretStore {
    private GatedSecretStore(final CountDownLatch allowResolve, final Map<String, String> secrets) {
      this.allowResolve = allowResolve;
      this.secrets = Map.copyOf(secrets);
    }

    @Override
    public Map<String, SecretResolutionResult> resolve(final Set<String> names) {
      try {
        if (!allowResolve.await(10, TimeUnit.SECONDS)) {
          throw new IllegalStateException("timed out waiting for test to open the secret store");
        }
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("interrupted waiting for test to open the secret store", e);
      }
      return names.stream()
          .collect(
              Collectors.toMap(
                  name -> name,
                  name ->
                      Optional.ofNullable(secrets.get(name))
                          .<SecretResolutionResult>map(Resolved::new)
                          .orElseGet(
                              () ->
                                  new Failed(SecretErrorCode.NOT_FOUND, "no such secret", null))));
    }

    @Override
    public List<String> list() {
      return List.copyOf(secrets.keySet());
    }
  }
}
