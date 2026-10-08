/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.engine.secret;

import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.INPUT_TARGET;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.RESOLUTION_INTERVAL;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.activateOneJob;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.applyRecordWaitTime;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.assertNoRecordCarriesValue;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.awaitJobKeyOf;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.createProcessInstance;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.deployProcessWithInput;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.incidentsOf;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.secretReference;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.Strings;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Covers a secret travelling from an environment variable to a job worker through a real broker,
 * gateway and client. The broker runs in the test JVM, so the variable is set by Failsafe (see this
 * module's pom.xml); the test is skipped when run without it, e.g. from an IDE.
 */
@ZeebeIntegration
@EnabledIfEnvironmentVariable(named = SecretResolutionFromEnvVarStoreIT.VARIABLE, matches = ".+")
final class SecretResolutionFromEnvVarStoreIT {

  static final String PREFIX = "SECRETSTOREIT_";
  static final String SECRET_NAME = "token";
  static final String VARIABLE = PREFIX + SECRET_NAME;

  @TestZeebe(initMethod = "initTestStandaloneBroker")
  private static TestStandaloneBroker broker;

  @AutoClose private final CamundaClient client = broker.newClientBuilder().build();

  private final String processId = Strings.newRandomValidBpmnId();
  private final String jobType = Strings.newRandomValidBpmnId();

  @SuppressWarnings("unused")
  static void initTestStandaloneBroker() {
    broker =
        new TestStandaloneBroker()
            .withRecordingExporter(true)
            .withUnauthenticatedAccess()
            .withEnvVarSecretStore(PREFIX)
            .withProcessingConfig(
                processing -> processing.getEngine().getSecrets().setInterval(RESOLUTION_INTERVAL));
  }

  @BeforeEach
  void setUp() {
    applyRecordWaitTime();
  }

  @Test
  void shouldHandWorkerTheValueOfThePrefixedVariable() {
    // given
    final var expectedValue = System.getenv(VARIABLE);
    deployProcessWithInput(client, processId, jobType, secretReference(SECRET_NAME));
    final long processInstanceKey = createProcessInstance(client, processId);
    awaitJobKeyOf(processInstanceKey);

    // when
    final var job = activateOneJob(client, jobType);

    // then
    assertThat(job.getVariablesAsMap()).containsEntry(INPUT_TARGET, expectedValue);
    assertThat(incidentsOf(processInstanceKey)).isEmpty();
    assertNoRecordCarriesValue(expectedValue);
  }
}
