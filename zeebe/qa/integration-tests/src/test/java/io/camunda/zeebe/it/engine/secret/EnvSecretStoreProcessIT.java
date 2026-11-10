/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.engine.secret;

import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.RESOLUTION_INTERVAL;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.applyRecordWaitTime;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.assertNoRecordCarriesValue;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.awaitJobKeyOf;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.createProcessInstance;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.deployProcessWithInput;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.primingPoll;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.resolutionFailureStates;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.secretReference;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.configuration.Secrets.EnvStore;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.ResolutionState;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.logging.LogCapturer;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.UUID;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Covers that a job whose environment-variable secret is unset raises an incident instead of being
 * handed out, and that no value the store holds leaks into the incident or the logs. The values a
 * worker receives are covered against the real image in {@code EnvSecretStoreContainerSmokeIT}.
 *
 * <p>The store looks up only the name it is asked for, so this path never reads the stored value
 * today: the leak assertions guard against a change that would dump the store's content, such as
 * listing the names or values it holds in an error.
 *
 * <p>The broker runs inside this JVM, which cannot set its own environment variables, so the build
 * sets {@code ENV_SECRET_PROCESS_IT_UNREFERENCED_NAME} on the forked test JVM; see the failsafe
 * configuration in this module's {@code pom.xml} and the {@code it} task in its {@code
 * build.gradle.kts}.
 */
@ZeebeIntegration
final class EnvSecretStoreProcessIT {

  // must match the variable the build sets on the test JVM
  private static final String PREFIX = "ENV_SECRET_PROCESS_IT_";
  private static final String STORED_NAME = "UNREFERENCED_NAME";
  private static final String STORED_VALUE = "stored-value-that-must-not-leak";

  @TestZeebe
  private static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withRecordingExporter(true)
          .withUnauthenticatedAccess()
          .withUnifiedConfig(
              camunda -> {
                final var store = new EnvStore();
                store.setPrefix(PREFIX);
                camunda.getSecrets().getStores().getEnv().put("default", store);
              })
          .withProcessingConfig(
              processing -> processing.getEngine().getSecrets().setInterval(RESOLUTION_INTERVAL));

  @AutoClose private final CamundaClient client = BROKER.newClientBuilder().build();

  private final String processId = Strings.newRandomValidBpmnId();
  private final String jobType = Strings.newRandomValidBpmnId();

  @BeforeAll
  static void requireTheVariableTheBuildSets() {
    // if run outside the build, e.g. from an IDE, the store would be empty and the leak assertions
    // would pass without a value that could leak
    // asserts on the one variable, never the whole environment, which a failure would print
    assertThat(System.getenv(PREFIX + STORED_NAME))
        .as(
            "%s%s, set by the failsafe <environmentVariables> in pom.xml and the 'it' task in"
                + " build.gradle.kts",
            PREFIX, STORED_NAME)
        .isEqualTo(STORED_VALUE);
  }

  @BeforeEach
  void setUp() {
    applyRecordWaitTime();
  }

  @Test
  void shouldRaiseAnIncidentInsteadOfHandingOutAJobWhoseVariableIsUnset() {
    // given
    final String missingName = "MISSING_" + UUID.randomUUID().toString().replace("-", "");
    deployProcessWithInput(client, processId, jobType, secretReference(missingName));

    try (final var logs = LogCapturer.capturing("io.camunda", Level.DEBUG)) {
      final long processInstanceKey = createProcessInstance(client, processId);
      awaitJobKeyOf(processInstanceKey);

      // when - the poll checks the job's references and finds the variable unset
      final var jobs = primingPoll(client, jobType);
      final var incident =
          RecordingExporter.incidentRecords(IncidentIntent.CREATED)
              .withProcessInstanceKey(processInstanceKey)
              .withErrorType(ErrorType.SECRET_RESOLUTION_ERROR)
              .getFirst()
              .getValue();

      // then - no job, and an incident that names the secret as not found
      assertThat(jobs).isEmpty();
      assertThat(incident.getErrorMessage()).contains(missingName);
      assertThat(resolutionFailureStates(missingName)).containsOnly(ResolutionState.NOT_FOUND);

      // and - neither the name nor the value the store holds leaks into the records or the logs
      assertThat(incident.getErrorMessage()).doesNotContain(STORED_NAME, STORED_VALUE);
      assertNoRecordCarriesValue(STORED_NAME);
      assertNoRecordCarriesValue(STORED_VALUE);
      assertThat(logs.contains(STORED_NAME)).as("logs contain a stored secret name").isFalse();
      assertThat(logs.contains(STORED_VALUE)).as("logs contain a secret value").isFalse();
    }
  }
}
