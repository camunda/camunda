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
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.createProcessInstance;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.deployProcessWithInput;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.newBrokerWithSecretStore;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.secretReference;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.uniqueSecretName;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.Strings;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Covers the harness rather than the engine: a broker instance that is closed and started again has
 * to find the secret store it was configured with, because closing it deletes the directory that
 * store is configured to read while the configuration keeps naming it.
 *
 * <p>Surefire's {@code rerunFailingTestsCount}, which CI sets, produces exactly that cycle. A rerun
 * re-executes the class in the same JVM without running the static initializer that configured the
 * store, so it restarts the same instance. Without the rebuild every rerun of a failed secret test
 * reports a store outage instead of the failure that was being investigated.
 */
@ZeebeIntegration
final class SecretStoreRebuiltAfterCloseIT {

  private static final String SECRET_NAME = uniqueSecretName();
  private static final String SECRET_VALUE = "value-of-" + SECRET_NAME;

  @TestZeebe(initMethod = "initTestStandaloneBroker")
  private static TestStandaloneBroker broker;

  @AutoClose private CamundaClient client;

  private final String processId = Strings.newRandomValidBpmnId();
  private final String jobType = Strings.newRandomValidBpmnId();

  @SuppressWarnings("unused")
  static void initTestStandaloneBroker() {
    // the secret is handed to the store's writer rather than written to the directory afterwards,
    // because the writer is what a rebuild of the directory replays
    broker =
        newBrokerWithSecretStore(
            directory ->
                Files.writeString(
                    directory.resolve(SECRET_NAME), SECRET_VALUE, StandardCharsets.UTF_8));
  }

  @BeforeEach
  void setUp() {
    applyRecordWaitTime();
    client = broker.newClientBuilder().build();
  }

  @Test
  void shouldResolveASecretAfterTheBrokerWasClosedAndStartedAgain() {
    // given - a close that took the directory backing the store with it
    client.close();
    broker.close();
    assertThat(broker.getFileBasedSecretStoreDirectory())
        .as("the close deleted the directory the store is configured to read")
        .doesNotExist();

    // when - the same instance is started again, as a rerun of this class would do
    broker.start().awaitCompleteTopology();
    client = broker.newClientBuilder().build();

    // then - the store holds the secrets it was configured with, so a reference resolves rather
    // than every lookup failing on a store the broker cannot read
    deployProcessWithInput(client, processId, jobType, secretReference(SECRET_NAME));
    createProcessInstance(client, processId);
    assertThat(activateOneJob(client, jobType).getVariablesAsMap())
        .containsEntry(INPUT_TARGET, SECRET_VALUE);
  }
}
