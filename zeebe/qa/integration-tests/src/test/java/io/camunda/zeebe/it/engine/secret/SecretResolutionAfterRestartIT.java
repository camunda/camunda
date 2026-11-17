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
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.resolutionWasRequestedFor;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.secretReference;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.uniqueSecretName;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.writeSecret;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.Strings;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Covers what happens to a secret a broker already resolved once it restarts: the values it held
 * are gone with the process, so the loop has to run again rather than the jobs of that secret
 * becoming undeliverable.
 *
 * <p>The cache is in-memory by design, and an engine test cannot lose it the way a restart does.
 */
@ZeebeIntegration
final class SecretResolutionAfterRestartIT {

  @TestZeebe(initMethod = "initTestStandaloneBroker")
  private static TestStandaloneBroker broker;

  @AutoClose private CamundaClient client;

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
    client = broker.newClientBuilder().build();
  }

  @Test
  void shouldResolveTheSecretAgainAfterARestartEmptiedTheCache() {
    // given - a secret this broker already resolved and served to a worker
    writeSecret(broker, secretName, secretValue);
    deployProcessWithInput(client, processId, jobType, secretReference(secretName));
    createProcessInstance(client, processId);
    assertThat(activateOneJob(client, jobType).getVariablesAsMap())
        .containsEntry(INPUT_TARGET, secretValue);

    // when
    client.close();
    broker.stop();
    broker.start().awaitCompleteTopology();
    client = broker.newClientBuilder().build();

    // then - the value is gone with the process that held it, so the next job of the same reference
    // is parked and resolved again before it is served
    final long processInstanceKey = createProcessInstance(client, processId);
    final long jobKey = awaitJobKeyOf(processInstanceKey);
    final ActivatedJob job = activateOneJob(client, jobType);
    assertThat(job.getKey()).isEqualTo(jobKey);
    assertThat(job.getVariablesAsMap()).containsEntry(INPUT_TARGET, secretValue);
    awaitActivationExported(jobType, jobKey);
    assertThat(resolutionWasRequestedFor(secretName, jobKey))
        .as("the job was parked and its reference resolved again after the restart")
        .isTrue();
    assertNoRecordCarriesValue(secretValue);
  }
}
