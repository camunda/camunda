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
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.applyRecordWaitTime;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.awaitJobKeyOf;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.awaitNoStreamRegistered;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.createProcessInstance;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.deployProcessWithInput;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.incidentsOf;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.newBrokerWithEmptySecretStore;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.onlyJob;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.pollJobs;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.primingPoll;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.secretReference;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.uniqueSecretName;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.writeSecret;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Covers what the "Retry" the operator triggers in Operate actually does to a {@code
 * SECRET_RESOLUTION_ERROR} incident, against a real gateway, client and file-based secret store.
 *
 * <p>Resolving such an incident hands the job back to the push path and nothing else. Secret
 * resolution is requested only from the two activation paths, so with no worker attached the retry
 * re-reads nothing: the incident record flips to resolved, the instance reads as healthy, and the
 * secret is still missing. See <a
 * href="https://github.com/camunda/camunda/issues/62727">#62727</a>.
 *
 * <p>{@code SecretResolutionJobActivationIT} already covers the retry that follows the operator
 * actually creating the secret. What is missing there, and is what this suite adds, is the retry
 * that fixes nothing — the case an operator reaches by acting on a wait status that pointed at the
 * wrong cause.
 *
 * <p>No test here registers a job stream, and none polls after the retry unless it says so. That is
 * the condition under test: a poll would itself re-check the references, so it must not be the only
 * thing that does.
 */
@ZeebeIntegration
final class SecretIncidentRetryIT {

  /**
   * Only has to outlive a background resolution cycle. Nothing is ever returned to end the request
   * early, so every poll asserting an empty result waits this out in full.
   */
  private static final Duration EMPTY_POLL_TIMEOUT = Duration.ofSeconds(5);

  @TestZeebe(initMethod = "initTestStandaloneBroker")
  private static TestStandaloneBroker broker;

  @AutoClose private final CamundaClient client = broker.newClientBuilder().build();

  private final String secretName = uniqueSecretName();
  private final String secretValue = "value-of-" + secretName;
  private final String processId = Strings.newRandomValidBpmnId();
  private final String jobType = Strings.newRandomValidBpmnId();

  @SuppressWarnings("unused")
  static void initTestStandaloneBroker() {
    broker = newBrokerWithEmptySecretStore();
  }

  @BeforeEach
  void setUp() {
    applyRecordWaitTime();
    awaitNoStreamRegistered(broker, jobType);
  }

  @Test
  void shouldRaiseIncidentAgainWhenTheOperatorRetriesWithoutCreatingTheSecret() {
    // given - an incident for a secret the store does not hold
    final long processInstanceKey = incidentedInstance();
    final Record<IncidentRecordValue> incident = awaitSecretIncident(processInstanceKey);

    // when - the operator retries without creating the secret, and no worker is connected
    client.newResolveIncidentCommand(incident.getKey()).send().join();

    // then - the incident comes back on its own, so the instance never reads as healthy
    Awaitility.await("until the incident is raised again")
        .atMost(AWAIT_TIMEOUT)
        .untilAsserted(
            () ->
                assertThat(incidentsOf(processInstanceKey))
                    .describedAs("a retry that fixed nothing does not clear the incident for good")
                    .hasSize(2));
  }

  @Test
  void shouldNotLeaveInstanceWithoutAnActiveIncidentAfterRetryingWithoutCreatingTheSecret() {
    // given
    final long processInstanceKey = incidentedInstance();
    final Record<IncidentRecordValue> incident = awaitSecretIncident(processInstanceKey);

    // when
    client.newResolveIncidentCommand(incident.getKey()).send().join();

    // the client call returns once the command is committed, exporting follows. Without anchoring
    // on the resolved record the count below can still be the original incident's, and read as one
    // active incident before any replacement exists.
    RecordingExporter.incidentRecords(IncidentIntent.RESOLVED)
        .withRecordKey(incident.getKey())
        .getFirst();

    // then - what the operator sees is an instance with an unresolved incident on it. Asserted as
    // "raised minus resolved" rather than on the raised count alone, since the point is the state
    // the UI reads back, not that some record was written at some point.
    Awaitility.await("until an active incident is on the instance again")
        .atMost(AWAIT_TIMEOUT)
        .untilAsserted(
            () ->
                assertThat(activeIncidentCountOf(processInstanceKey))
                    .describedAs("the instance still carries an incident while the secret is gone")
                    .isOne());
  }

  @Test
  void shouldNotHandOutTheJobToAWorkerConnectingAfterARetryThatFixedNothing() {
    // given - a retry that created no secret
    final long processInstanceKey = incidentedInstance();
    final Record<IncidentRecordValue> incident = awaitSecretIncident(processInstanceKey);
    client.newResolveIncidentCommand(incident.getKey()).send().join();

    // when - a worker shows up afterwards, as one eventually does
    final List<ActivatedJob> activated =
        pollJobs(client, jobType, 1, EMPTY_POLL_TIMEOUT).join().getJobs();

    // then - it is not handed a job whose secret still cannot be resolved
    assertThat(activated)
        .describedAs("a job blocked on a missing secret is never handed to a worker")
        .isEmpty();
  }

  @Test
  void shouldDeliverTheJobWhenTheOperatorCreatesTheSecretBeforeRetrying() {
    // given - the operator fixes the actual cause first, which must keep working
    final long processInstanceKey = incidentedInstance();
    final Record<IncidentRecordValue> incident = awaitSecretIncident(processInstanceKey);
    writeSecret(broker, secretName, secretValue);

    // when
    client.newResolveIncidentCommand(incident.getKey()).send().join();

    // then - the job is delivered with the value and no further incident is raised
    final ActivatedJob job = onlyJob(pollJobs(client, jobType, 1, AWAIT_TIMEOUT).join());
    assertThat(job.getVariablesAsMap()).containsEntry(INPUT_TARGET, secretValue);
    assertThat(incidentsOf(processInstanceKey))
        .describedAs("a retry after the secret exists resolves the instance for good")
        .hasSize(1);
  }

  /**
   * Creates an instance and drives the one poll that checks its references, parks its job and
   * requests the resolution that then fails. With no stream registered a created job is announced
   * unchecked, so without this poll nothing would ever look at the secret.
   */
  private long incidentedInstance() {
    deployProcessWithInput(client, processId, jobType, secretReference(secretName));
    final long processInstanceKey = createProcessInstance(client, processId);
    awaitJobKeyOf(processInstanceKey);
    assertThat(primingPoll(client, jobType)).isEmpty();
    return processInstanceKey;
  }

  private Record<IncidentRecordValue> awaitSecretIncident(final long processInstanceKey) {
    return RecordingExporter.incidentRecords(IncidentIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withErrorType(ErrorType.SECRET_RESOLUTION_ERROR)
        .getFirst();
  }

  /** Incidents raised for the instance that have not been resolved again, as recorded now. */
  private long activeIncidentCountOf(final long processInstanceKey) {
    final long raised = incidentsOf(processInstanceKey).size();
    final long resolved =
        RecordingExporter.getRecords().stream()
            .filter(record -> record.getIntent() == IncidentIntent.RESOLVED)
            .filter(record -> record.getValue() instanceof IncidentRecordValue)
            .map(record -> (IncidentRecordValue) record.getValue())
            .filter(incident -> incident.getProcessInstanceKey() == processInstanceKey)
            .count();
    return raised - resolved;
  }
}
