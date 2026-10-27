/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.secretstore.SecretErrorCode;
import io.camunda.secretstore.SecretResolutionResult;
import io.camunda.secretstore.SecretResolutionResult.Failed;
import io.camunda.secretstore.SecretResolutionResult.Resolved;
import io.camunda.secretstore.SecretStore;
import io.camunda.secretstore.SecretStoreRegistry;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.SecretReferenceIntent;
import io.camunda.zeebe.protocol.record.value.JobBatchRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

/**
 * Covers suspend of a job parked in {@code WAITING_FOR_SECRET_RESOLUTION}: suspension must override
 * secret-waiting so a later secret reactivation cannot put the job back into the hand-out index
 * while the process instance is still suspended.
 */
public final class JobSuspensionSecretWaitingTest {

  private static final String SECRET_NAME = "token";
  private static final String SECRET_VALUE = "resolved-secret";
  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();
  private final CountDownLatch allowResolve = new CountDownLatch(1);

  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition()
          .withSecretStoreRegistry(
              new SecretStoreRegistry(
                  Map.of(
                      SecretStoreRegistry.DEFAULT_STORE_ID,
                      new GatedSecretStore(allowResolve, Map.of(SECRET_NAME, SECRET_VALUE)))))
          .withEngineConfig(config -> config.setSecretResolutionInterval(Duration.ofMillis(100)));

  @After
  public void openGate() {
    // unblock any store call still waiting if the test failed before opening the gate
    allowResolve.countDown();
  }

  @Test
  public void shouldKeepSecretWaitingJobParkedAfterSecretResolution() {
    // given - a job whose secret the store holds, but the cache does not; the store blocks until
    // the
    // test opens the gate so resolution cannot race past suspend
    final String jobType = Strings.newRandomValidBpmnId();
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
                                "\"Bearer \" + camunda.secrets." + SECRET_NAME, "authorization"))
                .done())
        .deploy();
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();
    final long jobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getKey();

    final Record<JobBatchRecordValue> parked =
        engine.jobs().withType(jobType).withRequestStreamId(1).withRequestId(1L).activate();
    assertThat(parked.getValue().getJobs()).isEmpty();
    RecordingExporter.secretReferenceRecords(SecretReferenceIntent.RESOLUTION_REQUESTED)
        .withSecretReference(SECRET_NAME)
        .getFirst();

    // when - suspend while the job is still waiting; then let the store answer
    engine.processInstance().withInstanceKey(processInstanceKey).suspend();
    assertThat(
            RecordingExporter.jobRecords(JobIntent.SUSPENDED)
                .withRecordKey(jobKey)
                .withProcessInstanceKey(processInstanceKey)
                .exists())
        .isTrue();
    allowResolve.countDown();

    // then - secret resolution finishes and tries to reactivate the job
    RecordingExporter.secretReferenceRecords(SecretReferenceIntent.RESOLUTION_COMPLETED)
        .withSecretReference(SECRET_NAME)
        .getFirst();
    RecordingExporter.secretReferenceRecords(SecretReferenceIntent.BATCH_JOBS_REACTIVATED)
        .withSecretReference(SECRET_NAME)
        .getFirst();

    // and - the job stays out of the hand-out index while the instance is suspended
    final Record<JobBatchRecordValue> batch =
        engine.jobs().withType(jobType).withRequestStreamId(2).withRequestId(2L).activate();
    assertThat(batch.getValue().getJobKeys()).isEmpty();
  }

  /**
   * Blocks {@link #resolve} until the test opens the latch, so secret completion cannot race past
   * process-instance suspension.
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
