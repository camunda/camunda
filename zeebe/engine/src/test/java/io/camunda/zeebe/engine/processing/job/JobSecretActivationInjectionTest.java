/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import io.camunda.secretstore.NoopSecretStore;
import io.camunda.secretstore.SecretCache;
import io.camunda.secretstore.SecretStoreRegistry;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.ServiceTaskBuilder;
import io.camunda.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobBatchIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.SecretReferenceIntent;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.JobBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.SecretReferenceRecordValue;
import io.camunda.zeebe.stream.api.CommandResponseWriter;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.stubbing.Answer;

public final class JobSecretActivationInjectionTest {

  private static final String PROCESS_ID = "process";
  private static final String TASK_ID = "task";
  private static final String JOB_TYPE = "task-type";

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
  private boolean failResolution;
  private CommandResponseWriter mockResponseWriter;
  private volatile JobBatchRecord activationResponse;

  @Before
  public void setUp() {
    mockResponseWriter = engine.getCommandResponseWriter();
    interceptResponseWriter();
  }

  @Test
  public void shouldInjectCachedSecretIntoResponseOnly() {
    // given
    cachedSecrets.put("token", "resolved-secret");
    deploy(t -> t.zeebeInputExpression("\"Bearer \" + camunda.secrets.token", "authorization"));
    createInstanceAndAwaitJob();

    // when
    final Record<JobBatchRecordValue> activated =
        engine.jobs().withType(JOB_TYPE).withRequestStreamId(1).withRequestId(1L).activate();

    // then - the persisted ACTIVATED event keeps the unresolved placeholder (no secret in the log)
    assertThat(activated.getValue().getJobs().get(0).getVariables())
        .containsEntry("authorization", "Bearer camunda.secrets.token");

    // and - the worker response carries the resolved secret value
    Awaitility.await("until the activation response is written")
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(activationResponse).isNotNull());
    assertThat(activationResponse.getJobs().get(0).getVariables())
        .containsEntry("authorization", "Bearer resolved-secret");

    // and - no exported record (state, log) leaks the resolved secret value
    assertThat(RecordingExporter.getRecords())
        .noneMatch(record -> record.toString().contains("resolved-secret"));
  }

  @Test
  public void shouldParkJobAndRequestResolutionWhenSecretIsNotCached() {
    // given - the secret has no cached value (empty cache)
    deploy(t -> t.zeebeInputExpression("\"Bearer \" + camunda.secrets.token", "authorization"));
    final long processInstanceKey = createInstanceAndAwaitJob();
    final long jobKey = jobKeyOf(processInstanceKey);

    // when
    final Record<JobBatchRecordValue> activated =
        engine.jobs().withType(JOB_TYPE).withRequestStreamId(1).withRequestId(1L).activate();

    // then - the job is not handed out, neither in the event nor in the response
    assertThat(activated.getValue().getJobs()).isEmpty();
    assertThat(activated.getValue().getJobKeys()).isEmpty();
    Awaitility.await("until the activation response is written")
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(activationResponse).isNotNull());
    assertThat(activationResponse.getJobs()).isEmpty();

    // and - a RESOLUTION_REQUESTED event is written for the missing reference with the job key
    final Record<SecretReferenceRecordValue> requested =
        RecordingExporter.secretReferenceRecords(SecretReferenceIntent.RESOLUTION_REQUESTED)
            .withSecretReference("token")
            .getFirst();
    assertThat(requested.getValue().getStoreId()).isEmpty();
    assertThat(requested.getValue().getJobKeys()).containsExactly(jobKey);

    // and - the job is parked: a later activation does not hand it out again, even once the value
    // is cached (it is reactivated only after the background resolution completes, see #57852)
    cachedSecrets.put("token", "resolved-secret");
    final Record<JobBatchRecordValue> secondAttempt =
        engine.jobs().withType(JOB_TYPE).withRequestStreamId(2).withRequestId(2L).activate();
    assertThat(secondAttempt.getValue().getJobs()).isEmpty();

    // and - no exported record (state, log) leaks the resolved secret value
    assertThat(RecordingExporter.getRecords())
        .noneMatch(record -> record.toString().contains("resolved-secret"));
  }

  @Test
  public void shouldRequestResolutionOncePerReferenceForMultipleWaitingJobs() {
    // given - two jobs of the same type waiting on the same uncached secret reference
    deploy(t -> t.zeebeInputExpression("\"Bearer \" + camunda.secrets.token", "authorization"));
    final long firstJobKey = jobKeyOf(createInstanceAndAwaitJob());
    final long secondJobKey = jobKeyOf(createInstanceAndAwaitJob());

    // when
    engine.jobs().withType(JOB_TYPE).withRequestStreamId(1).withRequestId(1L).activate();

    // then - a single RESOLUTION_REQUESTED event carries both waiting job keys
    final Record<SecretReferenceRecordValue> requested =
        RecordingExporter.secretReferenceRecords(SecretReferenceIntent.RESOLUTION_REQUESTED)
            .withSecretReference("token")
            .getFirst();
    assertThat(requested.getValue().getJobKeys())
        .containsExactlyInAnyOrder(firstJobKey, secondJobKey);
  }

  @Test
  public void shouldRequestResolutionForEachMissingReferenceOfAJob() {
    // given - a job with two uncached secret references
    deploy(
        t ->
            t.zeebeInputExpression("camunda.secrets.token", "a")
                .zeebeInputExpression("camunda.secrets.apiKey", "b"));
    final long jobKey = jobKeyOf(createInstanceAndAwaitJob());

    // when
    engine.jobs().withType(JOB_TYPE).withRequestStreamId(1).withRequestId(1L).activate();

    // then - one RESOLUTION_REQUESTED event per reference, each carrying the job key
    final var requested =
        RecordingExporter.secretReferenceRecords(SecretReferenceIntent.RESOLUTION_REQUESTED)
            .limit(2)
            .asList();
    assertThat(requested)
        .extracting(record -> record.getValue().getSecretReference())
        .containsExactlyInAnyOrder("token", "apiKey");
    assertThat(requested)
        .allSatisfy(record -> assertThat(record.getValue().getJobKeys()).containsExactly(jobKey));
  }

  @Test
  public void shouldNotRequestResolutionWhenSecretIsCached() {
    // given
    cachedSecrets.put("token", "resolved-secret");
    deploy(t -> t.zeebeInputExpression("\"Bearer \" + camunda.secrets.token", "authorization"));
    final long processInstanceKey = createInstanceAndAwaitJob();

    // when - the job is activated normally
    final Record<JobBatchRecordValue> activated =
        engine.jobs().withType(JOB_TYPE).withRequestStreamId(1).withRequestId(1L).activate();
    assertThat(activated.getValue().getJobs()).hasSize(1);

    // then - completing the job runs the process to the end without any RESOLUTION_REQUESTED event
    engine.job().withKey(activated.getValue().getJobKeys().get(0)).complete();
    assertThat(RecordingExporter.records().limitToProcessInstance(processInstanceKey))
        .noneMatch(record -> record.getValueType() == ValueType.SECRET_REFERENCE);
  }

  @Test
  public void shouldFailActivationWhenSecretCacheLookupThrows() {
    // given
    failResolution = true;
    deploy(t -> t.zeebeInputExpression("\"Bearer \" + camunda.secrets.token", "authorization"));
    createInstanceAndAwaitJob();

    // when - the cache failure propagates and fails the activation command
    final Record<JobBatchRecordValue> rejection =
        engine.jobs().withType(JOB_TYPE).expectRejection().activate();

    // then - the command is rejected with the processing error and no job is handed out
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.PROCESSING_ERROR);
    assertThat(rejection.getRejectionReason()).contains("resolver exploded");
    assertThat(
            RecordingExporter.records()
                .limit(record -> record.getRecordType() == RecordType.COMMAND_REJECTION)
                .withIntent(JobBatchIntent.ACTIVATED))
        .isEmpty();

    // and - the job stays activatable: with a working cache the next activation hands it out
    failResolution = false;
    cachedSecrets.put("token", "resolved-secret");
    final Record<JobBatchRecordValue> secondAttempt = engine.jobs().withType(JOB_TYPE).activate();
    assertThat(secondAttempt.getValue().getJobs()).hasSize(1);
  }

  @Test
  public void shouldActivateJobBehindJobWithUncachedSecret() {
    // given - a job with an uncached secret is created before a job without secret references,
    // both of the same type
    deploy(t -> t.zeebeInputExpression("\"Bearer \" + camunda.secrets.token", "authorization"));
    createInstanceAndAwaitJob();
    deploy("plain-process", t -> t.zeebeInputExpression("\"plain-value\"", "authorization"));
    createInstanceAndAwaitJob("plain-process");

    // when - activating at most one job
    final Record<JobBatchRecordValue> activated =
        engine
            .jobs()
            .withType(JOB_TYPE)
            .withMaxJobsToActivate(1)
            .withRequestStreamId(1)
            .withRequestId(1L)
            .activate();

    // then - the uncached job does not consume the batch slot; the job behind it is handed out
    assertThat(activated.getValue().getJobs()).hasSize(1);
    assertThat(activated.getValue().getJobs().get(0).getVariables())
        .containsEntry("authorization", "plain-value");
    Awaitility.await("until the activation response is written")
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(activationResponse).isNotNull());
    assertThat(activationResponse.getJobs()).hasSize(1);
    assertThat(activationResponse.getJobs().get(0).getVariables())
        .containsEntry("authorization", "plain-value");
  }

  @Test
  public void shouldNotChangeVariablesForJobWithoutSecrets() {
    // given
    cachedSecrets.put("token", "resolved-secret");
    deploy(t -> t.zeebeInputExpression("\"plain-value\"", "authorization"));
    createInstanceAndAwaitJob();

    // when
    final Record<JobBatchRecordValue> activated =
        engine.jobs().withType(JOB_TYPE).withRequestStreamId(1).withRequestId(1L).activate();

    // then - variables are unchanged in both the event and the response
    assertThat(activated.getValue().getJobs().get(0).getVariables())
        .containsEntry("authorization", "plain-value");
    Awaitility.await("until the activation response is written")
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(activationResponse).isNotNull());
    assertThat(activationResponse.getJobs().get(0).getVariables())
        .containsEntry("authorization", "plain-value");
  }

  @Test
  public void shouldDropJobExceedingMessageSizeBudgetAndHandItOutOnNextActivation() {
    // given - two jobs referencing the same secret; the injected value fits the batch growth
    // budget once, but not twice
    final var value = "x".repeat(EngineConfiguration.BATCH_SIZE_CALCULATION_BUFFER / 2 + 1000);
    cachedSecrets.put("token", value);
    deploy(t -> t.zeebeInputExpression("\"Bearer \" + camunda.secrets.token", "authorization"));
    createInstanceAndAwaitJob();
    createInstanceAndAwaitJob();

    // when
    final Record<JobBatchRecordValue> activated =
        engine.jobs().withType(JOB_TYPE).withRequestStreamId(1).withRequestId(1L).activate();

    // then - only the first job is activated; the second is dropped and the batch is marked
    // truncated so the client polls again right away
    assertThat(activated.getValue().getJobs()).hasSize(1);
    assertThat(activated.getValue().isTruncated()).isTrue();
    Awaitility.await("until the activation response is written")
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(activationResponse).isNotNull());
    assertThat(activationResponse.getJobs()).hasSize(1);
    assertThat(activationResponse.getTruncated()).isTrue();
    assertThat(activationResponse.getJobs().get(0).getVariables())
        .containsEntry("authorization", "Bearer " + value);

    // and - the dropped job stays activatable: the next activation hands it out with the value
    final Record<JobBatchRecordValue> secondAttempt =
        engine.jobs().withType(JOB_TYPE).withRequestStreamId(2).withRequestId(2L).activate();
    assertThat(secondAttempt.getValue().getJobs()).hasSize(1);
    Awaitility.await("until the second activation response is written")
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () ->
                assertThat(activationResponse.getJobs().get(0).getVariables())
                    .containsEntry("authorization", "Bearer " + value));

    // and - no exported record leaks the resolved secret value
    assertThat(RecordingExporter.getRecords())
        .noneMatch(record -> record.toString().contains(value));
  }

  @Test
  public void shouldRaiseIncidentWhenSecretValueCanNeverFitMessageSizeBudget() {
    // given - the injected value alone exceeds the whole batch growth budget, so no activation
    // batch could ever carry this job
    final var value = "x".repeat(EngineConfiguration.BATCH_SIZE_CALCULATION_BUFFER + 1000);
    cachedSecrets.put("token", value);
    deploy(t -> t.zeebeInputExpression("\"Bearer \" + camunda.secrets.token", "authorization"));
    final long processInstanceKey = createInstanceAndAwaitJob();
    final long jobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getKey();

    // when
    final Record<JobBatchRecordValue> activated =
        engine.jobs().withType(JOB_TYPE).withRequestStreamId(1).withRequestId(1L).activate();

    // then - the job is not handed out and a message-size incident is raised for it, like for a
    // job that is too large to activate without secrets
    assertThat(activated.getValue().getJobs()).isEmpty();
    Awaitility.await("until the activation response is written")
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(activationResponse).isNotNull());
    assertThat(activationResponse.getJobs()).isEmpty();

    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED).withJobKey(jobKey).getFirst();
    assertThat(incident.getValue().getErrorType()).isEqualTo(ErrorType.MESSAGE_SIZE_EXCEEDED);

    // and - the incident disables the job: a later activation does not hand it out either
    final Record<JobBatchRecordValue> secondAttempt =
        engine.jobs().withType(JOB_TYPE).withRequestStreamId(2).withRequestId(2L).activate();
    assertThat(secondAttempt.getValue().getJobs()).isEmpty();

    // and - no exported record (including the incident) leaks the resolved secret value
    assertThat(RecordingExporter.getRecords())
        .noneMatch(record -> record.toString().contains(value));
  }

  private long createInstanceAndAwaitJob() {
    return createInstanceAndAwaitJob(PROCESS_ID);
  }

  private long createInstanceAndAwaitJob(final String processId) {
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();
    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .getFirst();
    return processInstanceKey;
  }

  private long jobKeyOf(final long processInstanceKey) {
    return RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .getFirst()
        .getKey();
  }

  private void deploy(final Consumer<ServiceTaskBuilder> modifier) {
    deploy(PROCESS_ID, modifier);
  }

  private void deploy(final String processId, final Consumer<ServiceTaskBuilder> modifier) {
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
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

  private void interceptResponseWriter() {
    doAnswer(
            (Answer<CommandResponseWriter>)
                invocation -> {
                  final var arguments = invocation.getArguments();
                  if (arguments != null
                      && arguments.length == 1
                      && arguments[0] instanceof final JobBatchRecord jobBatchRecord) {
                    // copy the record: engine record objects are reused across commands, so the
                    // captured reference could otherwise be overwritten by a later command
                    final var copy = new JobBatchRecord();
                    final MutableDirectBuffer buffer =
                        new UnsafeBuffer(new byte[jobBatchRecord.getLength()]);
                    jobBatchRecord.write(buffer, 0);
                    copy.wrap(buffer);
                    activationResponse = copy;
                  }
                  return mockResponseWriter;
                })
        .when(mockResponseWriter)
        .valueWriter(any());
  }

  /** Serves the test's cached secrets and simulates a broken cache when the flag is set. */
  private final class TestSecretCache implements SecretCache {
    @Override
    public Optional<String> get(final String name) {
      if (failResolution) {
        throw new IllegalStateException("resolver exploded");
      }
      return Optional.ofNullable(cachedSecrets.get(name));
    }

    @Override
    public void put(final String name, final String value) {
      cachedSecrets.put(name, value);
    }
  }
}
