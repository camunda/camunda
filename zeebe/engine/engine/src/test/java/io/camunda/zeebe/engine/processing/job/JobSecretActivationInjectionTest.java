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
import io.camunda.secretstore.SecretStoreRegistry;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.SecretActivationResponseCapture;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.ServiceTaskBuilder;
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
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;
import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class JobSecretActivationInjectionTest {

  private static final String PROCESS_ID = "process";
  private static final String TASK_ID = "task";
  private static final String JOB_TYPE = "task-type";

  // The default max fragment size of the test log stream, which caps both the appended event and
  // the activation response (see LogStreamBuilderImpl). The size checks are proxied against it.
  private static final int MAX_MESSAGE_SIZE = 4 * 1024 * 1024;

  @Rule public final EngineRule engine;

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private final SecretActivationResponseCapture secretActivation =
      new SecretActivationResponseCapture();

  public JobSecretActivationInjectionTest() {
    engine =
        EngineRule.singlePartition()
            .withSecretStoreRegistry(
                new SecretStoreRegistry(
                    Map.of("default", new NoopSecretStore()), Map.of("default", secretActivation)));
  }

  @Before
  public void setUp() {
    secretActivation.install(engine.getCommandResponseWriter());
  }

  @Test
  public void shouldInjectCachedSecretIntoResponseOnly() {
    // given
    secretActivation.putSecret("token", "resolved-secret");
    deploy(t -> t.zeebeInputExpression("\"Bearer \" + camunda.secrets.token", "authorization"));
    createInstanceAndAwaitJob();

    // when
    final Record<JobBatchRecordValue> activated =
        engine.jobs().withType(JOB_TYPE).withRequestStreamId(1).withRequestId(1L).activate();

    // then - the persisted ACTIVATED event keeps the unresolved placeholder (no secret in the log)
    assertThat(activated.getValue().getJobs().get(0).getVariables())
        .containsEntry("authorization", "Bearer camunda.secrets.token");

    // and - the worker response carries the resolved secret value
    assertThat(secretActivation.awaitActivationResponse().getJobs().get(0).getVariables())
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
    assertThat(secretActivation.awaitActivationResponse().getJobs()).isEmpty();

    // and - a RESOLUTION_REQUESTED event is written for the missing reference with the job key
    final Record<SecretReferenceRecordValue> requested =
        RecordingExporter.secretReferenceRecords(SecretReferenceIntent.RESOLUTION_REQUESTED)
            .withSecretReference("token")
            .getFirst();
    assertThat(requested.getValue().getStoreId()).isEqualTo(SecretStoreRegistry.DEFAULT_STORE_ID);
    assertThat(requested.getValue().getJobKeys()).containsExactly(jobKey);

    // and - the job is parked: a later activation does not hand it out again, even once the value
    // is cached (it is reactivated only after the background resolution completes, see #57852)
    secretActivation.putSecret("token", "resolved-secret");
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
    secretActivation.putSecret("token", "resolved-secret");
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
    secretActivation.failResolution(true);
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
    secretActivation.failResolution(false);
    secretActivation.putSecret("token", "resolved-secret");
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
    final var response = secretActivation.awaitActivationResponse();
    assertThat(response.getJobs()).hasSize(1);
    assertThat(response.getJobs().get(0).getVariables())
        .containsEntry("authorization", "plain-value");
  }

  @Test
  public void shouldNotChangeVariablesForJobWithoutSecrets() {
    // given
    secretActivation.putSecret("token", "resolved-secret");
    deploy(t -> t.zeebeInputExpression("\"plain-value\"", "authorization"));
    createInstanceAndAwaitJob();

    // when
    final Record<JobBatchRecordValue> activated =
        engine.jobs().withType(JOB_TYPE).withRequestStreamId(1).withRequestId(1L).activate();

    // then - variables are unchanged in both the event and the response
    assertThat(activated.getValue().getJobs().get(0).getVariables())
        .containsEntry("authorization", "plain-value");
    assertThat(secretActivation.awaitActivationResponse().getJobs().get(0).getVariables())
        .containsEntry("authorization", "plain-value");
  }

  @Test
  public void shouldActivateSingleJobWithSecretValueLargerThanCalculationBuffer() {
    // given - a single job whose injected secret value is larger than the batch calculation
    // buffer but still well within the message size; before the fix the buffer was misused as the
    // whole growth budget, so this job was wrongly dropped
    final var value = "x".repeat(EngineConfiguration.BATCH_SIZE_CALCULATION_BUFFER * 2);
    secretActivation.putSecret("token", value);
    deploy(t -> t.zeebeInputExpression("\"Bearer \" + camunda.secrets.token", "authorization"));
    createInstanceAndAwaitJob();

    // when
    final Record<JobBatchRecordValue> activated =
        engine.jobs().withType(JOB_TYPE).withRequestStreamId(1).withRequestId(1L).activate();

    // then - the job is activated normally, the batch is not truncated, and no incident is raised
    assertThat(activated.getValue().getJobs()).hasSize(1);
    assertThat(activated.getValue().isTruncated()).isFalse();
    final var response = secretActivation.awaitActivationResponse();
    assertThat(response.getJobs()).hasSize(1);
    assertThat(response.getJobs().get(0).getVariables())
        .containsEntry("authorization", "Bearer " + value);

    // and - no exported record (state, log) leaks the resolved secret value, and no message-size
    // incident is raised for a job that fits the message size
    final var records = RecordingExporter.getRecords();
    assertThat(records).noneMatch(record -> record.toString().contains(value));
    assertThat(records)
        .as("no incident is raised for a job that fits the message size")
        .noneMatch(record -> record.getValueType() == ValueType.INCIDENT);
  }

  @Test
  public void shouldDropJobExceedingMessageSizeBudgetAndHandItOutOnNextActivation() {
    // given - two jobs referencing the same secret; the injected value fits the free message size
    // once, but two of them together would push the response past it
    final var value = "x".repeat(2 * MAX_MESSAGE_SIZE / 3);
    secretActivation.putSecret("token", value);
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
    final var response = secretActivation.awaitActivationResponse();
    assertThat(response.getJobs()).hasSize(1);
    assertThat(response.getTruncated()).isTrue();
    assertThat(response.getJobs().get(0).getVariables())
        .containsEntry("authorization", "Bearer " + value);

    // and - the dropped job stays activatable: the next activation hands it out with the value.
    // This polls on the RESPONSE'S CONTENT (not just non-null, since the field already holds the
    // first, stale response above) via the plain getter, unlike the awaitActivationResponse() calls
    // elsewhere in this file.
    final Record<JobBatchRecordValue> secondAttempt =
        engine.jobs().withType(JOB_TYPE).withRequestStreamId(2).withRequestId(2L).activate();
    assertThat(secondAttempt.getValue().getJobs()).hasSize(1);
    Awaitility.await("until the second activation response is written")
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () ->
                assertThat(secretActivation.getActivationResponse().getJobs().get(0).getVariables())
                    .containsEntry("authorization", "Bearer " + value));

    // and - no exported record leaks the resolved secret value
    assertThat(RecordingExporter.getRecords())
        .noneMatch(record -> record.toString().contains(value));
  }

  @Test
  public void shouldRaiseIncidentWhenSecretValueCanNeverFitMessageSizeBudget() {
    // given - the injected value alone exceeds the whole message size, so no activation batch
    // could ever carry this job
    final var value = "x".repeat(MAX_MESSAGE_SIZE);
    secretActivation.putSecret("token", value);
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
    assertThat(secretActivation.awaitActivationResponse().getJobs()).isEmpty();

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
}
