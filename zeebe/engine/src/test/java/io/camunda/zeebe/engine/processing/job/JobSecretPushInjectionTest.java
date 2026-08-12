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
import io.camunda.zeebe.engine.state.immutable.JobState.State;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.engine.util.RecordingJobStreamer;
import io.camunda.zeebe.engine.util.RecordingJobStreamer.RecordingJobStream;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.ServiceTaskBuilder;
import io.camunda.zeebe.protocol.impl.record.value.secretreference.SecretReferenceRecord;
import io.camunda.zeebe.protocol.impl.stream.job.ActivatedJob;
import io.camunda.zeebe.protocol.impl.stream.job.JobActivationPropertiesImpl;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobBatchIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.SecretReferenceIntent;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.JobBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.ResolutionState;
import io.camunda.zeebe.protocol.record.value.SecretReferenceRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Covers the secret resolution of the job-push activation path: a job is pushed only once every
 * secret reference of its input mappings has a cached value, the pushed job carries the values, and
 * a job parked for an uncached reference is pushed as soon as the background resolution completes.
 */
public final class JobSecretPushInjectionTest {

  private static final String PROCESS_ID = "process";
  private static final String TASK_ID = "task";
  private static final String JOB_TYPE = "task-type";
  private static final String SECRET_NAME = "token";
  private static final String SECRET_VALUE = "resolved-secret";

  private final Map<String, String> cachedSecrets = new ConcurrentHashMap<>();
  private final RecordingJobStreamer jobStreamer = new RecordingJobStreamer();

  /**
   * The background resolution is driven by hand: the tests write the {@code RESOLUTION_COMPLETE}
   * and {@code RESOLUTION_FAIL} commands the scheduler would write, and cache the value it would
   * cache, so what the push path does with a resolution outcome is tested without depending on the
   * timing of the scheduler. Its interval is set beyond the lifetime of a test so it never
   * interferes.
   */
  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition()
          .withJobStreamer(jobStreamer)
          .withSecretStoreRegistry(
              new SecretStoreRegistry(
                  Map.of("default", new NoopSecretStore()),
                  Map.of("default", new TestSecretCache())))
          .withEngineConfig(config -> config.setSecretResolutionInterval(Duration.ofHours(1)));

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private RecordingJobStream jobStream;

  @Before
  public void setUp() {
    jobStream = registerJobStream();
  }

  @Test
  public void shouldPushJobWithResolvedSecretValue() {
    // given
    cachedSecrets.put(SECRET_NAME, SECRET_VALUE);
    deploy(t -> t.zeebeInputExpression("\"Bearer \" + camunda.secrets.token", "authorization"));

    // when
    engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then - the pushed job carries the resolved value
    final ActivatedJob pushedJob = awaitPushedJob();
    assertThat(jobStream.getActivatedJobs()).hasSize(1);
    assertThat(pushedJob.jobRecord().getVariables())
        .containsEntry("authorization", "Bearer " + SECRET_VALUE);

    // and - the activation event that carries the job onto the log holds no variables at all
    final Record<JobBatchRecordValue> activated =
        RecordingExporter.jobBatchRecords(JobBatchIntent.ACTIVATED).withType(JOB_TYPE).getFirst();
    assertThat(activated.getValue().getJobs()).hasSize(1);
    assertThat(activated.getValue().getJobs().getFirst().getVariables()).isEmpty();

    // and - no exported record leaks the resolved secret value
    assertNoRecordLeaksTheSecretValue();
  }

  @Test
  public void shouldNotPushJobWhenSecretIsNotCached() {
    // given - the secret has no cached value and the store cannot resolve it either
    deploy(t -> t.zeebeInputExpression("\"Bearer \" + camunda.secrets.token", "authorization"));

    // when
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final long jobKey = jobKeyOf(processInstanceKey);

    // then - the resolution is requested for the job and the job is parked
    final Record<SecretReferenceRecordValue> requested =
        RecordingExporter.secretReferenceRecords(SecretReferenceIntent.RESOLUTION_REQUESTED)
            .withSecretReference(SECRET_NAME)
            .getFirst();
    assertThat(requested.getValue().getJobKeys()).containsExactly(jobKey);
    assertThat(jobState(jobKey)).isEqualTo(State.WAITING_FOR_SECRET_RESOLUTION);

    // and - the job is neither activated nor pushed: the process instance runs no further, so
    // waiting for the element instance the job belongs to bounds what can still be exported
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId(TASK_ID)
        .getFirst();
    assertThat(jobStream.getActivatedJobs()).isEmpty();
    assertThat(RecordingExporter.getRecords())
        .noneMatch(record -> record.getIntent() == JobBatchIntent.ACTIVATED);
  }

  @Test
  public void shouldNotPushJobWhenItsSecretFailsToResolve() {
    // given - a job parked for a reference the store has no value for
    deploy(t -> t.zeebeInputExpression("\"Bearer \" + camunda.secrets.token", "authorization"));
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final long jobKey = jobKeyOf(processInstanceKey);
    awaitResolutionRequests(1);

    // when - the resolution fails permanently
    failResolution();

    // then - the job gets a secret resolution incident and stays parked and unpushed
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED).withJobKey(jobKey).getFirst();
    assertThat(incident.getValue().getErrorType()).isEqualTo(ErrorType.SECRET_RESOLUTION_ERROR);
    assertThat(jobStream.getActivatedJobs()).isEmpty();
    assertThat(jobState(jobKey)).isEqualTo(State.WAITING_FOR_SECRET_RESOLUTION);
  }

  @Test
  public void shouldRequestResolutionOfEveryUncachedReferenceOfTheJob() {
    // given - a job with two uncached references, one of them used at two paths
    deploy(
        t ->
            t.zeebeInputExpression("camunda.secrets.token", "a")
                .zeebeInputExpression("camunda.secrets.token", "b")
                .zeebeInputExpression("camunda.secrets.apiKey", "c"));

    // when
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final long jobKey = jobKeyOf(processInstanceKey);

    // then - one request per reference, each naming the job once, and no push
    final var requests =
        RecordingExporter.secretReferenceRecords(SecretReferenceIntent.RESOLUTION_REQUESTED)
            .limit(2)
            .asList();
    assertThat(requests)
        .extracting(record -> record.getValue().getSecretReference())
        .containsExactlyInAnyOrder(SECRET_NAME, "apiKey");
    assertThat(requests)
        .allSatisfy(record -> assertThat(record.getValue().getJobKeys()).containsExactly(jobKey));
    assertThat(jobStream.getActivatedJobs()).isEmpty();
  }

  @Test
  public void shouldPushJobWithoutSecretReferencesUnchanged() {
    // given
    cachedSecrets.put(SECRET_NAME, SECRET_VALUE);
    deploy(t -> t.zeebeInputExpression("\"plain-value\"", "authorization"));

    // when
    engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then - the job is pushed with its variables unchanged and without any resolution request
    final ActivatedJob pushedJob = awaitPushedJob();
    assertThat(pushedJob.jobRecord().getVariables()).containsEntry("authorization", "plain-value");
    assertThat(RecordingExporter.getRecords())
        .noneMatch(record -> record.getValueType() == ValueType.SECRET_REFERENCE);
  }

  @Test
  public void shouldOnlyNotifyWorkersWhenNoStreamIsRegistered() {
    // given - no job stream for the job type, so the job is not pushed but only announced
    jobStreamer.clearStreams();
    deploy(t -> t.zeebeInputExpression("\"Bearer \" + camunda.secrets.token", "authorization"));

    // when
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final long jobKey = jobKeyOf(processInstanceKey);

    // then - the job stays activatable for a long poll, which owns the check on that path
    Awaitility.await("until the workers are notified")
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> assertThat(jobStreamer.notificationsForJob(JOB_TYPE)).isPositive());
    assertThat(jobState(jobKey)).isEqualTo(State.ACTIVATABLE);
    assertThat(RecordingExporter.getRecords())
        .noneMatch(record -> record.getValueType() == ValueType.SECRET_REFERENCE);
  }

  /**
   * Asserts no exported record carries the resolved value. The record values are scanned rather
   * than the records, since a record's own {@code toString} is truncated and could hide a value.
   */
  private void assertNoRecordLeaksTheSecretValue() {
    assertThat(RecordingExporter.getRecords())
        .noneMatch(record -> record.getValue().toString().contains(SECRET_VALUE));
  }

  private ActivatedJob awaitPushedJob() {
    Awaitility.await("until the job is pushed to the stream")
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(() -> assertThat(jobStream.getActivatedJobs()).isNotEmpty());
    return jobStream.getActivatedJobs().getFirst();
  }

  private List<Record<SecretReferenceRecordValue>> awaitResolutionRequests(final int count) {
    return awaitResolutionRequests(count, SECRET_NAME);
  }

  private List<Record<SecretReferenceRecordValue>> awaitResolutionRequests(
      final int count, final String secretName) {
    return RecordingExporter.secretReferenceRecords(SecretReferenceIntent.RESOLUTION_REQUESTED)
        .withSecretReference(secretName)
        .limit(count)
        .asList();
  }

  /** Writes the command the resolution scheduler writes once it cached the value. */
  private void completeResolution() {
    completeResolution(SECRET_NAME);
  }

  private void completeResolution(final String secretName) {
    engine.writeRecords(
        RecordToWrite.command()
            .secretReference(
                SecretReferenceIntent.RESOLUTION_COMPLETE,
                new SecretReferenceRecord()
                    .setSecretReference(secretName)
                    .setResolutionState(ResolutionState.SUCCESS)));
  }

  /** Writes the command the resolution scheduler writes when a reference cannot be resolved. */
  private void failResolution() {
    engine.writeRecords(
        RecordToWrite.command()
            .secretReference(
                SecretReferenceIntent.RESOLUTION_FAIL,
                new SecretReferenceRecord()
                    .setSecretReference(SECRET_NAME)
                    .setResolutionState(ResolutionState.NOT_FOUND)));
  }

  private RecordingJobStream registerJobStream() {
    final var worker = BufferUtil.wrapString("test");
    final var properties =
        new JobActivationPropertiesImpl()
            .setWorker(worker, 0, worker.capacity())
            .setTimeout(30_000L)
            .setTenantIds(List.of(TenantOwned.DEFAULT_TENANT_IDENTIFIER));
    return jobStreamer.addJobStream(BufferUtil.wrapString(JOB_TYPE), properties);
  }

  private long jobKeyOf(final long processInstanceKey) {
    return RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .getFirst()
        .getKey();
  }

  private State jobState(final long jobKey) {
    return engine.getProcessingState().getJobState().getState(jobKey);
  }

  private void deploy(final Consumer<ServiceTaskBuilder> modifier) {
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask(
                TASK_ID,
                t -> {
                  t.zeebeJobType(JOB_TYPE);
                  modifier.accept(t);
                })
            .endEvent()
            .done();
    engine.deployment().withXmlResource(process).deploy();
  }

  /** Serves the test's cached secrets, and takes the values the background resolution caches. */
  private final class TestSecretCache implements SecretCache {
    @Override
    public Optional<String> get(final String name) {
      return Optional.ofNullable(cachedSecrets.get(name));
    }

    @Override
    public void put(final String name, final String value) {
      cachedSecrets.put(name, value);
    }
  }
}
