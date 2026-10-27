/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.engine.secret;

import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.AWAIT_TIMEOUT;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.INPUT_TARGET;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.JOB_TIMEOUT;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.LONG_POLL_TIMEOUT;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.REACTIVATION_BATCH_SIZE;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.applyRecordWaitTime;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.assertNoIncidentWasRaised;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.assertNoRecordCarriesValue;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.awaitActivationExported;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.awaitJobKeyOf;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.awaitNoStreamRegistered;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.awaitResolutionRequested;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.awaitStreamRegistered;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.backtickedSecretReference;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.createProcessInstance;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.createProcessInstances;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.deployProcessWithInput;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.incidentsOf;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.newBrokerWithEmptySecretStore;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.onlyJob;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.parkedJobKeys;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.pollJobs;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.pollUntilActivated;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.primingPoll;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.reactivationRounds;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.resolutionFailureStates;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.resolutionWasRequestedFor;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.secretReference;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.uniqueHyphenatedSecretName;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.uniqueSecretName;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.writeSecret;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.response.ActivateJobsResponse;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.response.StreamJobsResponse;
import io.camunda.client.api.search.enums.ClusterVariableKind;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.ResolutionState;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Covers what a worker actually receives for a job whose input mapping references a secret, on both
 * activation paths, against a real gateway, a real client and a real file-based secret store. That
 * includes a job that references more than one secret, the two bounds the resolution runs into once
 * a value or a waiting-job list gets large, and a secret a process reaches through a cluster
 * variable rather than a literal of its own.
 *
 * <p>The engine suites ({@code JobSecretActivationInjectionTest}, {@code
 * JobSecretPushInjectionTest} and {@code SecretResolutionLifecycleTest}) already cover the
 * processing of one command with a stubbed cache. What only this level can show is the part outside
 * the stream processor: that a long poll blocked on an empty result is woken by the reactivation's
 * workers-notified broadcast, that a reactivated job reaches a real remote job stream (which that
 * broadcast never reaches), and that no exported record carries a value a worker was handed.
 *
 * <p>All of it shares one broker, since none of these tests need a broker of their own and starting
 * one per group of tests is the expensive part. Every test uses its own secret name: the cache is
 * per broker and shared with the gateway's secret endpoints, so a name reused across tests would be
 * warm from the first test on and the cache-miss path would never run.
 */
@ZeebeIntegration
final class SecretResolutionJobActivationIT {

  /**
   * Larger than the broker's default max message size, which is what an activation batch has to
   * spare since the injector now measures growth against it rather than against {@code
   * EngineConfiguration#BATCH_SIZE_CALCULATION_BUFFER}.
   */
  private static final int OVERSIZED_SECRET_LENGTH = 4 * 1024 * 1024;

  /**
   * More than the 100 jobs one reactivation command carries, so the chain has to follow up, and
   * more than the 100 jobs one activation skips for uncached references ({@code
   * EngineConfiguration#MAX_UNCACHED_SECRET_JOBS_SKIPPED_PER_ACTIVATION}), so the first poll cuts
   * off at the skip cap and the client has to come back for the rest.
   */
  private static final int WAITING_JOBS = 150;

  /**
   * Only has to outlive the park, the resolution and the injection attempt of the one job the test
   * expects to be dropped, since nothing is ever returned to end the request early.
   */
  private static final Duration DROPPED_JOB_POLL_TIMEOUT = Duration.ofSeconds(5);

  private static final String CLUSTER_VARIABLE_SECRET_FIELD = "apiToken";

  /** The variable a second secret is mapped into, for the jobs that reference two. */
  private static final String OTHER_INPUT_TARGET = "otherToken";

  @TestZeebe(initMethod = "initTestStandaloneBroker")
  private static TestStandaloneBroker broker;

  @AutoClose private final CamundaClient client = broker.newClientBuilder().build();

  private final String secretName = uniqueSecretName();
  private final String secretValue = "value-of-" + secretName;
  private final String otherSecretName = uniqueSecretName();
  private final String otherSecretValue = "value-of-" + otherSecretName;
  private final String clusterVariableName = "cv" + UUID.randomUUID().toString().replace("-", "");
  private final String processId = Strings.newRandomValidBpmnId();
  private final String jobType = Strings.newRandomValidBpmnId();

  @SuppressWarnings("unused")
  static void initTestStandaloneBroker() {
    broker = newBrokerWithEmptySecretStore();
  }

  @BeforeEach
  void setUp() {
    applyRecordWaitTime();
  }

  @Test
  void shouldCompleteBlockedLongPollOnceTheSecretResolves() {
    // given - a job whose secret the store holds but the cache does not
    writeSecret(broker, secretName, secretValue);
    deployProcessWithSecretInput();
    final long processInstanceKey = createProcessInstance(client, processId);
    awaitJobKeyOf(processInstanceKey);

    // when - the request parks the job on its first attempt and stays open, so it is the
    // reactivation that completes it
    final ActivatedJob job = onlyJob(poll().join());

    // then
    assertThat(job.getVariablesAsMap()).containsEntry(INPUT_TARGET, secretValue);

    // and - the job took the park and background resolution route, rather than a warm cache
    awaitActivationExported(jobType, job.getKey());
    assertThat(resolutionWasRequestedFor(secretName, job.getKey())).isTrue();
    assertThat(incidentsOf(processInstanceKey)).isEmpty();
    assertNoRecordCarriesValue(secretValue);
  }

  @Test
  void shouldResolveBacktickedHyphenatedSecretReference() {
    // given - a dashed store name, the shape a Kubernetes secret data key or a GCP secret id
    // routinely has, referenced the only way FEEL allows: backtick-escaped. The AST detector
    // applies no charset check, so this authoring form already worked; the test pins it end to end
    // because nothing else did
    final String hyphenatedName = uniqueHyphenatedSecretName();
    final String hyphenatedValue = "value-of-" + hyphenatedName;
    writeSecret(broker, hyphenatedName, hyphenatedValue);
    deployProcessWithInput(client, processId, jobType, backtickedSecretReference(hyphenatedName));
    final long processInstanceKey = createProcessInstance(client, processId);
    awaitJobKeyOf(processInstanceKey);

    // when
    final ActivatedJob job = onlyJob(poll().join());

    // then - the dash survives the detector, the job record, the store lookup and the injector
    assertThat(job.getVariablesAsMap()).containsEntry(INPUT_TARGET, hyphenatedValue);
    awaitActivationExported(jobType, job.getKey());
    assertThat(resolutionWasRequestedFor(hyphenatedName, job.getKey())).isTrue();
    assertThat(incidentsOf(processInstanceKey)).isEmpty();
    assertNoRecordCarriesValue(hyphenatedValue);
  }

  @Test
  void shouldPushJobToStreamOnceTheSecretResolves() {
    // given - a stream registered before the job exists, so the job is pushed rather than polled
    writeSecret(broker, secretName, secretValue);
    deployProcessWithSecretInput();
    final List<ActivatedJob> streamed = new CopyOnWriteArrayList<>();
    final var stream = openJobStream(streamed::add);
    final long processInstanceKey;

    try {
      awaitStreamRegistered(broker, jobType);

      // when
      processInstanceKey = createProcessInstance(client, processId);

      // then - the only push carries the value, so the job was never pushed while it was uncached
      Awaitility.await("until the job is pushed")
          .atMost(AWAIT_TIMEOUT)
          .untilAsserted(() -> assertThat(streamed).hasSize(1));
    } finally {
      stream.cancel(true);
    }

    assertThat(streamed)
        .singleElement()
        .satisfies(
            job -> assertThat(job.getVariablesAsMap()).containsEntry(INPUT_TARGET, secretValue));
    final long jobKey = streamed.get(0).getKey();
    awaitActivationExported(jobType, jobKey);
    assertThat(resolutionWasRequestedFor(secretName, jobKey)).isTrue();
    assertThat(incidentsOf(processInstanceKey)).isEmpty();
    assertNoRecordCarriesValue(secretValue);
  }

  @Test
  void shouldActivateWithoutParkingWhenTheSecretIsAlreadyCached() {
    // given - a first instance whose resolution left the value in the cache
    writeSecret(broker, secretName, secretValue);
    deployProcessWithSecretInput();
    createProcessInstance(client, processId);
    onlyJob(poll().join());

    // when - a second instance runs against the warm cache
    final long processInstanceKey = createProcessInstance(client, processId);
    awaitJobKeyOf(processInstanceKey);
    final ActivatedJob job = onlyJob(poll().join());

    // then - it is activated with the value, and was never parked on the way there. A park would
    // have been requested before the activation this awaits, so it cannot be in flight still
    assertThat(job.getProcessInstanceKey()).isEqualTo(processInstanceKey);
    assertThat(job.getVariablesAsMap()).containsEntry(INPUT_TARGET, secretValue);
    awaitActivationExported(jobType, job.getKey());
    assertThat(resolutionWasRequestedFor(secretName, job.getKey())).isFalse();
    assertNoRecordCarriesValue(secretValue);
  }

  @Test
  void shouldParkJobEvenWhenTheWorkerDoesNotFetchTheSecretVariable() {
    // given - a worker that asks for another variable than the one the secret is mapped into
    writeSecret(broker, secretName, secretValue);
    deployProcessWithSecretInput();
    final long processInstanceKey =
        createProcessInstance(client, processId, Map.of("other", "plain"));
    awaitJobKeyOf(processInstanceKey);

    // when
    final ActivatedJob job =
        onlyJob(
            client
                .newActivateJobsCommand()
                .jobType(jobType)
                .maxJobsToActivate(1)
                .timeout(JOB_TIMEOUT)
                .fetchVariables("other")
                .requestTimeout(LONG_POLL_TIMEOUT)
                .send()
                .join());

    // then - the job was still held back until its reference resolved, since the check is on the
    // job's references and not on the variables the worker asked for
    assertThat(job.getVariablesAsMap())
        .containsEntry("other", "plain")
        .doesNotContainKey(INPUT_TARGET);
    awaitActivationExported(jobType, job.getKey());
    assertThat(resolutionWasRequestedFor(secretName, job.getKey())).isTrue();
    assertNoRecordCarriesValue(secretValue);
  }

  @Test
  void shouldNotHandTheSecretValueToAListenerJobOfTheSameElement() {
    // given - a task whose input mapping references a secret, plus an end execution listener on it
    writeSecret(broker, secretName, secretValue);
    final String listenerType = Strings.newRandomValidBpmnId();
    client
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask(
                    "task",
                    task ->
                        task.zeebeJobType(jobType)
                            .zeebeInputExpression(secretReference(secretName), INPUT_TARGET)
                            .zeebeEndExecutionListener(listenerType))
                .endEvent()
                .done(),
            processId + ".bpmn")
        .send()
        .join();
    final long processInstanceKey = createProcessInstance(client, processId);

    // when - the task's worker receives the value and completes the job
    final ActivatedJob taskJob = onlyJob(poll().join());
    assertThat(taskJob.getVariablesAsMap()).containsEntry(INPUT_TARGET, secretValue);
    client.newCompleteCommand(taskJob.getKey()).send().join();

    // then - the listener job of the same element sees the placeholder, since a listener job is
    // created without secret references and only the referencing job's variables are injected
    final ActivatedJob listenerJob = onlyJob(poll(listenerType).join());
    assertThat(listenerJob.getVariablesAsMap())
        .containsEntry(INPUT_TARGET, secretReference(secretName));
    awaitActivationExported(listenerType, listenerJob.getKey());
    assertThat(incidentsOf(processInstanceKey)).isEmpty();
    assertNoRecordCarriesValue(secretValue);
  }

  @Test
  void shouldRaiseIncidentForAnUnknownSecretAndDeliverAfterTheOperatorFixesIt() {
    // given - a job referencing a secret the store does not hold
    deployProcessWithSecretInput();
    final long processInstanceKey = createProcessInstance(client, processId);
    awaitJobKeyOf(processInstanceKey);
    // with no stream registered the job is announced unchecked, so it is this poll that checks its
    // references, parks it and requests the resolution that then fails
    assertThat(primingPoll(client, jobType)).isEmpty();
    final Record<IncidentRecordValue> incident = awaitSecretIncident(processInstanceKey);

    // then - one incident naming the secret, and nothing activatable behind it
    assertThat(incident.getValue().getErrorType()).isEqualTo(ErrorType.SECRET_RESOLUTION_ERROR);
    assertThat(incident.getValue().getErrorMessage()).contains(secretName);
    assertThat(incident.getValue().getJobKey()).isPositive();

    // and - the secret was missing rather than its store, the other way round from
    // SecretStoreUnavailableIT. Both raise this same incident, so without reading the state off the
    // RESOLUTION_FAILED record neither test can tell which of the two it got
    assertThat(resolutionFailureStates(secretName))
        .as("the secret was missing, not its store")
        .containsOnly(ResolutionState.NOT_FOUND);

    // when - the operator adds the missing secret and resolves the incident
    writeSecret(broker, secretName, secretValue);
    client.newResolveIncidentCommand(incident.getKey()).send().join();

    // then - the job is handed out again with the value, and no second incident was raised
    final ActivatedJob job = onlyJob(poll().join());
    assertThat(job.getVariablesAsMap()).containsEntry(INPUT_TARGET, secretValue);
    awaitActivationExported(jobType, job.getKey());
    assertThat(incidentsOf(processInstanceKey)).hasSize(1);
    assertNoRecordCarriesValue(secretValue);
  }

  @Test
  void shouldPushJobToStreamAfterTheOperatorResolvesTheIncident() {
    // given - a stream registered for a job whose secret the store does not hold
    deployProcessWithSecretInput();
    final List<ActivatedJob> streamed = new CopyOnWriteArrayList<>();
    final var stream = openJobStream(streamed::add);
    final long processInstanceKey;

    try {
      awaitStreamRegistered(broker, jobType);
      processInstanceKey = createProcessInstance(client, processId);
      final Record<IncidentRecordValue> incident = awaitSecretIncident(processInstanceKey);
      assertThat(streamed).as("a job with an unresolved secret is never pushed").isEmpty();

      // when - the operator adds the missing secret and resolves the incident
      writeSecret(broker, secretName, secretValue);
      client.newResolveIncidentCommand(incident.getKey()).send().join();

      // then - resolving the incident hands the job back to the push path, which pushes it with the
      // value once the reference resolves again
      Awaitility.await("until the job is pushed")
          .atMost(AWAIT_TIMEOUT)
          .untilAsserted(() -> assertThat(streamed).hasSize(1));
    } finally {
      stream.cancel(true);
    }

    assertThat(streamed)
        .singleElement()
        .satisfies(
            job -> assertThat(job.getVariablesAsMap()).containsEntry(INPUT_TARGET, secretValue));
    awaitActivationExported(jobType, streamed.get(0).getKey());
    assertThat(incidentsOf(processInstanceKey)).hasSize(1);
    assertNoRecordCarriesValue(secretValue);
  }

  @Test
  void shouldAnnounceJobToPollingWorkersWhenTheStreamIsGone() {
    // given - a job parked on a secret the store does not hold, with a stream registered
    deployProcessWithSecretInput();
    final List<ActivatedJob> streamed = new CopyOnWriteArrayList<>();
    final var stream = openJobStream(streamed::add);
    final Record<IncidentRecordValue> incident;

    try {
      awaitStreamRegistered(broker, jobType);
      final long processInstanceKey = createProcessInstance(client, processId);
      incident = awaitSecretIncident(processInstanceKey);
    } finally {
      // the stream is gone before the job becomes available again, so nothing can push it
      stream.cancel(true);
    }
    awaitNoStreamRegistered(broker, jobType);

    // when - the operator adds the secret and resolves the incident
    writeSecret(broker, secretName, secretValue);
    client.newResolveIncidentCommand(incident.getKey()).send().join();

    // then - the reactivation announces the job type instead of pushing, so a poll receives it
    final ActivatedJob job = onlyJob(poll().join());
    assertThat(job.getVariablesAsMap()).containsEntry(INPUT_TARGET, secretValue);
    assertThat(streamed).isEmpty();
    awaitActivationExported(jobType, job.getKey());
    assertNoRecordCarriesValue(secretValue);
  }

  @Test
  void shouldInjectTheSecretAgainWhenAFailedJobIsRetried() {
    // given - a job that was activated with its secret value and then failed with a retry left
    writeSecret(broker, secretName, secretValue);
    deployProcessWithSecretInput();
    createProcessInstance(client, processId);
    final ActivatedJob firstActivation = onlyJob(poll().join());
    assertThat(firstActivation.getVariablesAsMap()).containsEntry(INPUT_TARGET, secretValue);

    // when
    client.newFailCommand(firstActivation.getKey()).retries(1).send().join();

    // then - the retry carries the value again, never the placeholder it is stored with
    final ActivatedJob job = onlyJob(poll().join());
    assertThat(job.getKey()).isEqualTo(firstActivation.getKey());
    assertThat(job.getVariablesAsMap()).containsEntry(INPUT_TARGET, secretValue);
    awaitActivationExported(jobType, job.getKey());
    assertNoRecordCarriesValue(secretValue);
  }

  @Test
  void shouldTerminateInstanceWhoseJobIsParkedOnAnUnresolvableSecret() {
    // given - a job parked on a secret the store does not hold, so it stays parked
    deployProcessWithSecretInput();
    final long processInstanceKey = createProcessInstance(client, processId);
    awaitJobKeyOf(processInstanceKey);
    assertThat(primingPoll(client, jobType)).isEmpty();
    awaitResolutionRequested(secretName);

    // when - the instance is cancelled while its job waits on that reference
    client.newCancelInstanceCommand(processInstanceKey).send().join();

    // then - the instance terminates, and the reference's drain skipping a job that is gone leaves
    // the broker serving the next instance of the same reference once the secret is there
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_TERMINATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.PROCESS)
                .limit(1)
                .exists())
        .as("the cancelled instance terminated")
        .isTrue();

    writeSecret(broker, secretName, secretValue);
    final long nextInstanceKey = createProcessInstance(client, processId);
    final ActivatedJob job = onlyJob(poll().join());
    assertThat(job.getProcessInstanceKey()).isEqualTo(nextInstanceKey);
    assertThat(job.getVariablesAsMap()).containsEntry(INPUT_TARGET, secretValue);
    awaitActivationExported(jobType, job.getKey());
    assertNoRecordCarriesValue(secretValue);
  }

  @Test
  void shouldActivateWithASecretReachedThroughAClusterVariable() {
    // given - a cluster variable holding the reference, and a process reading it
    writeSecret(broker, secretName, secretValue);
    createSecretReferenceClusterVariable();
    deployProcessReadingTheClusterVariable();
    createProcessInstance(client, processId);

    // when
    final ActivatedJob job = onlyJob(poll().join());

    // then
    assertThat(job.getVariablesAsMap()).containsEntry(INPUT_TARGET, secretValue);
    awaitActivationExported(jobType, job.getKey());
    assertThat(resolutionWasRequestedFor(secretName, job.getKey())).isTrue();
    assertNoRecordCarriesValue(secretValue);
  }

  @Test
  void shouldActivateWithADashedSecretReachedThroughAClusterVariable() {
    // given - a dashed store name reached through a cluster variable. This is the one path that
    // finds a reference by scanning raw text (ClusterVariableSecretReferenceScanner) rather than by
    // walking a parsed FEEL AST, so it is the path the reference charset actually gates
    final String dashedName = uniqueHyphenatedSecretName();
    final String dashedValue = "value-of-" + dashedName;
    writeSecret(broker, dashedName, dashedValue);
    createSecretReferenceClusterVariable(dashedName);
    deployProcessReadingTheClusterVariable();
    final long processInstanceKey = createProcessInstance(client, processId);

    // then - the whole dashed name is captured and resolved. The narrower charset stopped the name
    // at the dash, which requested an unrelated secret when one of that shorter name existed
    final ActivatedJob job = onlyJob(poll().join());
    assertThat(job.getVariablesAsMap()).containsEntry(INPUT_TARGET, dashedValue);
    awaitActivationExported(jobType, job.getKey());
    assertThat(resolutionWasRequestedFor(dashedName, job.getKey())).isTrue();
    assertThat(incidentsOf(processInstanceKey)).isEmpty();
    assertNoRecordCarriesValue(dashedValue);
  }

  @Test
  void shouldPushJobWithASecretReachedThroughAClusterVariable() {
    // given
    writeSecret(broker, secretName, secretValue);
    createSecretReferenceClusterVariable();
    deployProcessReadingTheClusterVariable();
    final List<ActivatedJob> streamed = new CopyOnWriteArrayList<>();
    final var stream = openJobStream(streamed::add);

    try {
      awaitStreamRegistered(broker, jobType);

      // when
      createProcessInstance(client, processId);

      // then
      Awaitility.await("until the job is pushed")
          .atMost(AWAIT_TIMEOUT)
          .untilAsserted(() -> assertThat(streamed).hasSize(1));
    } finally {
      stream.cancel(true);
    }

    assertThat(streamed)
        .singleElement()
        .satisfies(
            job -> assertThat(job.getVariablesAsMap()).containsEntry(INPUT_TARGET, secretValue));
    final long jobKey = streamed.get(0).getKey();
    awaitActivationExported(jobType, jobKey);
    assertThat(resolutionWasRequestedFor(secretName, jobKey)).isTrue();
    assertNoRecordCarriesValue(secretValue);
  }

  @Test
  void shouldServeTheUpdatedReferenceAfterTheClusterVariableIsUpdated() {
    // given - a worker served the secret the cluster variable pointed at when it was created
    writeSecret(broker, secretName, secretValue);
    writeSecret(broker, otherSecretName, otherSecretValue);
    createSecretReferenceClusterVariable(secretName);
    deployProcessReadingTheClusterVariable();
    createProcessInstance(client, processId);
    assertThat(onlyJob(poll().join()).getVariablesAsMap()).containsEntry(INPUT_TARGET, secretValue);

    // when - the variable is updated to hold the reference of another secret
    client
        .newGloballyScopedClusterVariableUpdateRequest()
        .update(
            clusterVariableName,
            Map.of(CLUSTER_VARIABLE_SECRET_FIELD, secretReference(otherSecretName)))
        .send()
        .join();

    // then - the update is scanned like the creation was, so the next job carries the new secret
    final long processInstanceKey = createProcessInstance(client, processId);
    final ActivatedJob job = onlyJob(poll().join());
    assertThat(job.getProcessInstanceKey()).isEqualTo(processInstanceKey);
    assertThat(job.getVariablesAsMap()).containsEntry(INPUT_TARGET, otherSecretValue);
    awaitActivationExported(jobType, job.getKey());
    assertThat(resolutionWasRequestedFor(otherSecretName, job.getKey())).isTrue();
    assertNoRecordCarriesValue(secretValue);
    assertNoRecordCarriesValue(otherSecretValue);
  }

  @Test
  void shouldActivateOnceWithEveryReferenceOfAJobResolved() {
    // given - one of the two secrets of a job already in the cache, from an earlier job of its own
    writeSecret(broker, secretName, secretValue);
    writeSecret(broker, otherSecretName, otherSecretValue);
    final String warmUpProcessId = Strings.newRandomValidBpmnId();
    final String warmUpJobType = Strings.newRandomValidBpmnId();
    deployProcessWithInput(client, warmUpProcessId, warmUpJobType, secretReference(secretName));
    createProcessInstance(client, warmUpProcessId);
    assertThat(onlyJob(poll(warmUpJobType).join()).getVariablesAsMap())
        .containsEntry(INPUT_TARGET, secretValue);

    // when - a job references both, so one is cached and the other is not
    deployProcessWithTwoSecretInputs();
    final long processInstanceKey = createProcessInstance(client, processId);
    final ActivatedJob job = onlyJob(poll().join());

    // then - the job waited for the reference that was missing and was then activated once with
    // both values, rather than handed out as soon as one of them could be injected
    assertThat(job.getProcessInstanceKey()).isEqualTo(processInstanceKey);
    assertThat(job.getVariablesAsMap())
        .containsEntry(INPUT_TARGET, secretValue)
        .containsEntry(OTHER_INPUT_TARGET, otherSecretValue);
    awaitActivationExported(jobType, job.getKey());
    assertThat(resolutionWasRequestedFor(otherSecretName, job.getKey()))
        .as("the reference that was not cached was requested")
        .isTrue();
    assertThat(resolutionWasRequestedFor(secretName, job.getKey()))
        .as("the reference that was cached was not requested again")
        .isFalse();
    assertThat(incidentsOf(processInstanceKey)).isEmpty();
    assertNoRecordCarriesValue(secretValue);
    assertNoRecordCarriesValue(otherSecretValue);
  }

  @Test
  void shouldRaiseIncidentWhenOnlyOneOfTheReferencesOfAJobResolves() {
    // given - a job referencing two secrets, one of which the store does not hold
    writeSecret(broker, secretName, secretValue);
    deployProcessWithTwoSecretInputs();
    final long processInstanceKey = createProcessInstance(client, processId);
    awaitJobKeyOf(processInstanceKey);
    assertThat(primingPoll(client, jobType)).isEmpty();

    // when - one reference resolves and the other cannot
    final Record<IncidentRecordValue> incident = awaitSecretIncident(processInstanceKey);

    // then - the job is not handed out with what could be resolved; it gets an incident naming the
    // reference that failed, and the reactivation of the one that resolved leaves it alone since it
    // is still parked on the other
    assertThat(incident.getValue().getErrorMessage()).contains(otherSecretName);
    assertThat(resolutionFailureStates(otherSecretName)).containsOnly(ResolutionState.NOT_FOUND);
    assertThat(resolutionFailureStates(secretName)).isEmpty();

    // when - the operator adds the missing secret and resolves the incident
    writeSecret(broker, otherSecretName, otherSecretValue);
    client.newResolveIncidentCommand(incident.getKey()).send().join();

    // then - the job is handed out with both values, and no second incident was raised
    final ActivatedJob job = onlyJob(poll().join());
    assertThat(job.getVariablesAsMap())
        .containsEntry(INPUT_TARGET, secretValue)
        .containsEntry(OTHER_INPUT_TARGET, otherSecretValue);
    awaitActivationExported(jobType, job.getKey());
    assertThat(incidentsOf(processInstanceKey)).hasSize(1);
    assertNoRecordCarriesValue(secretValue);
    assertNoRecordCarriesValue(otherSecretValue);
  }

  @Test
  void shouldRaiseIncidentWhenTheSecretValueOutgrowsTheActivationBatch() {
    // given - a secret whose value cannot be injected without outgrowing the batch
    final String oversizedValue = "v".repeat(OVERSIZED_SECRET_LENGTH);
    writeSecret(broker, secretName, oversizedValue);
    deployProcessWithSecretInput();
    final long processInstanceKey = createProcessInstance(client, processId);

    // when - the job is polled, which parks it, resolves the value and then attempts the injection.
    // The dropped job is taken out of the activation, so this request has nothing left to return
    // and only its own timeout ends it
    final CamundaFuture<ActivateJobsResponse> pendingPoll =
        pollJobs(client, jobType, 1, DROPPED_JOB_POLL_TIMEOUT);

    // then - the job is not activated and the worker is left with an incident to act on
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(incident.getValue().getErrorType()).isEqualTo(ErrorType.MESSAGE_SIZE_EXCEEDED);
    assertThat(incident.getValue().getErrorMessage())
        .contains(String.valueOf(incident.getValue().getJobKey()));
    assertThat(pendingPoll.join().getJobs()).isEmpty();

    // and - the value was resolved into the cache before the injection was rejected, so a value too
    // large to hand out is still a value that must not reach a record. The incident above is
    // written after the injection, so the records of that attempt are exported by now
    assertNoRecordCarriesValue(oversizedValue);
  }

  @Test
  void shouldDeliverEveryJobWaitingOnOneReference() {
    // given - more jobs waiting on one reference than a single reactivation command carries
    writeSecret(broker, secretName, secretValue);
    deployProcessWithSecretInput();
    final List<Long> processInstanceKeys = createProcessInstances(client, processId, WAITING_JOBS);

    // when
    final Map<Long, ActivatedJob> activated = pollUntilActivated(client, jobType, WAITING_JOBS);

    // then - every waiting job was handed out, each with the resolved value
    assertThat(activated.values())
        .allSatisfy(
            job -> assertThat(job.getVariablesAsMap()).containsEntry(INPUT_TARGET, secretValue));
    assertThat(activated.values())
        .map(ActivatedJob::getProcessInstanceKey)
        .containsExactlyInAnyOrderElementsOf(processInstanceKeys);
    activated.keySet().forEach(jobKey -> awaitActivationExported(jobType, jobKey));
    assertNoIncidentWasRaised();
    assertNoRecordCarriesValue(secretValue);

    // and - the reactivation drained the jobs it parked in chained rounds rather than one batch,
    // which delivering them all does not by itself show. How many jobs end up parked is a matter of
    // timing (a job whose poll finds the value already cached is never parked at all), so the round
    // count is required against the jobs that actually were parked rather than against a fixed
    // number
    final Set<Long> parked = parkedJobKeys(secretName);
    final List<List<Long>> rounds = reactivationRounds(secretName);
    assertThat(rounds)
        .as("no round carried more jobs than one reactivation command holds")
        .allSatisfy(round -> assertThat(round).hasSizeLessThanOrEqualTo(REACTIVATION_BATCH_SIZE));
    assertThat(rounds.stream().flatMap(List::stream).toList())
        .as("every parked job was reactivated")
        .containsAll(parked);
    assertThat(rounds)
        .as("the %d parked jobs took more than one reactivation round".formatted(parked.size()))
        .hasSizeGreaterThanOrEqualTo(
            (parked.size() + REACTIVATION_BATCH_SIZE - 1) / REACTIVATION_BATCH_SIZE);
  }

  private Record<IncidentRecordValue> awaitSecretIncident(final long processInstanceKey) {
    return RecordingExporter.incidentRecords(IncidentIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withErrorType(ErrorType.SECRET_RESOLUTION_ERROR)
        .getFirst();
  }

  private void deployProcessWithSecretInput() {
    deployProcessWithInput(client, processId, jobType, secretReference(secretName));
  }

  /** A task whose input mappings read two secrets, into two different variables. */
  private void deployProcessWithTwoSecretInputs() {
    client
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask(
                    "task",
                    task ->
                        task.zeebeJobType(jobType)
                            .zeebeInputExpression(secretReference(secretName), INPUT_TARGET)
                            .zeebeInputExpression(
                                secretReference(otherSecretName), OTHER_INPUT_TARGET))
                .endEvent()
                .done(),
            processId + ".bpmn")
        .send()
        .join();
  }

  private void createSecretReferenceClusterVariable() {
    createSecretReferenceClusterVariable(secretName);
  }

  private void createSecretReferenceClusterVariable(final String referencedSecret) {
    client
        .newGloballyScopedClusterVariableCreateRequest()
        .create(
            clusterVariableName,
            Map.of(CLUSTER_VARIABLE_SECRET_FIELD, secretReference(referencedSecret)))
        .kind(ClusterVariableKind.SECRET_REFERENCE)
        .send()
        .join();
  }

  private void deployProcessReadingTheClusterVariable() {
    deployProcessWithInput(
        client,
        processId,
        jobType,
        "camunda.vars.cluster.%s.%s".formatted(clusterVariableName, CLUSTER_VARIABLE_SECRET_FIELD));
  }

  /**
   * A long poll for one job of this test's type, kept apart from the harness's {@code pollJobs}.
   */
  private CamundaFuture<ActivateJobsResponse> poll() {
    return poll(jobType);
  }

  private CamundaFuture<ActivateJobsResponse> poll(final String type) {
    return pollJobs(client, type, 1, LONG_POLL_TIMEOUT);
  }

  private CamundaFuture<StreamJobsResponse> openJobStream(final Consumer<ActivatedJob> consumer) {
    return client
        .newStreamJobsCommand()
        .jobType(jobType)
        .consumer(consumer)
        .workerName("streamer")
        .timeout(JOB_TIMEOUT)
        .send();
  }
}
