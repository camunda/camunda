/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.engine.secret;

import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.INPUT_TARGET;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.activateOneJob;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.applyRecordWaitTime;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.assertNoRecordCarriesValue;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.awaitActivationExported;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.awaitJobKeyOf;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.createProcessInstance;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.deployProcessWithInput;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.newBrokerWithEmptySecretStore;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.primingPoll;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.resolutionFailureStates;
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
import io.camunda.zeebe.protocol.record.value.ResolutionState;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Covers the store-level failure of the background resolution, as opposed to a secret the store
 * answers for and does not have: the store is taken away entirely, the retries run out, and every
 * job waiting on the reference ends up with an incident an operator can act on.
 *
 * <p>Reaching that branch means a store that actually fails, which is a real store and a real
 * configuration rather than a stub answering on demand.
 */
@ZeebeIntegration
final class SecretStoreUnavailableIT {

  private static final Duration RETRY_DELAY = Duration.ofMillis(200);
  private static final int RETRY_MAX_ATTEMPTS = 2;

  @TestZeebe(initMethod = "initTestStandaloneBroker")
  private static TestStandaloneBroker broker;

  @AutoClose private final CamundaClient client = broker.newClientBuilder().build();

  private final String secretName = uniqueSecretName();
  private final String secretValue = "value-of-" + secretName;
  private final String processId = Strings.newRandomValidBpmnId();
  private final String jobType = Strings.newRandomValidBpmnId();

  @SuppressWarnings("unused")
  static void initTestStandaloneBroker() {
    broker =
        newBrokerWithEmptySecretStore()
            .withProcessingConfig(
                processing -> {
                  final var secrets = processing.getEngine().getSecrets();
                  // the ladder is only here to be exhausted, so it is walked down quickly
                  secrets.setRetryMaxAttempts(RETRY_MAX_ATTEMPTS);
                  secrets.setRetryInitialDelay(RETRY_DELAY);
                  secrets.setRetryMaxDelay(RETRY_DELAY);
                });
  }

  @BeforeEach
  void setUp() {
    applyRecordWaitTime();
  }

  @AfterEach
  void restoreStore() {
    if (Files.exists(movedAwayDirectory())) {
      move(movedAwayDirectory(), storeDirectory());
    }
  }

  @Test
  void shouldIncidentWaitingJobsWhenTheStoreStaysUnavailable() {
    // given - a job whose secret the store holds, but the store itself is gone
    writeSecret(broker, secretName, secretValue);
    deployProcessWithInput(client, processId, jobType, secretReference(secretName));
    final long processInstanceKey = createProcessInstance(client, processId);
    awaitJobKeyOf(processInstanceKey);
    move(storeDirectory(), movedAwayDirectory());

    // when - a poll parks the job and requests a resolution the store cannot answer
    assertThat(primingPoll(client, jobType)).isEmpty();

    // then - the retries run out and the waiting job gets an incident naming its secret
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withErrorType(ErrorType.SECRET_RESOLUTION_ERROR)
            .getFirst();
    assertThat(incident.getValue().getErrorMessage()).contains(secretName);

    // and - the reference failed because the store was unavailable, which is the branch this test
    // is here for: a secret the store answers for and does not have raises the same incident, so
    // without this the test would stay green if a missing store degraded into a missing secret. The
    // record is exported before the incident above, which the incident's own await has waited for
    assertThat(resolutionFailureStates(secretName))
        .as("the reference was failed by the store outage, not by a per-secret failure")
        .containsOnly(ResolutionState.STORE_UNAVAILABLE);

    // when - the store is back and the operator resolves the incident
    move(movedAwayDirectory(), storeDirectory());
    client.newResolveIncidentCommand(incident.getKey()).send().join();

    // then - the job is handed out again, now with the value the store holds
    final ActivatedJob job = activateOneJob(client, jobType);
    assertThat(job.getVariablesAsMap()).containsEntry(INPUT_TARGET, secretValue);
    awaitActivationExported(jobType, job.getKey());
    assertNoRecordCarriesValue(secretValue);
  }

  private Path storeDirectory() {
    return broker.getFileBasedSecretStoreDirectory();
  }

  private Path movedAwayDirectory() {
    return storeDirectory().resolveSibling(storeDirectory().getFileName() + "-unavailable");
  }

  private static void move(final Path from, final Path to) {
    try {
      Files.move(from, to);
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to move '" + from + "' to '" + to + "'", e);
    }
  }
}
