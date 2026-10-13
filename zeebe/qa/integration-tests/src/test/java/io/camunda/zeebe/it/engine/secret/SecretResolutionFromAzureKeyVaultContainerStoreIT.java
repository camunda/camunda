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
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.awaitActivationExported;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.awaitJobKeyOf;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.createProcessInstance;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.deployProcessWithInput;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.incidentsOf;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.secretReference;
import static org.assertj.core.api.Assertions.assertThat;

import com.azure.security.keyvault.secrets.SecretClient;
import com.github.nagyesta.lowkeyvault.testcontainers.LowkeyVaultContainer;
import com.github.nagyesta.lowkeyvault.testcontainers.LowkeyVaultContainerBuilder;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.secretstore.azure.AzureKeyVaultSecretStore;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.Strings;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The container (bundle) counterpart of {@link SecretResolutionFromAzureKeyVaultStoreIT}: every
 * reference is a top-level JSON field of one shared Key Vault secret rather than a Key Vault secret
 * of its own. This is the Azure-specific resolution mode (decision #64971), so the full-stack path
 * is pinned here too — a worker is handed a value read out of the JSON container through the real
 * broker → gateway → engine → job path, with the emulator-trusting client installed the same way.
 */
@ZeebeIntegration
final class SecretResolutionFromAzureKeyVaultContainerStoreIT {

  private static final String PREFIX = "camunda-";
  private static final String CONTAINER_SECRET_NAME = "app-config";
  private static final String SECRET_KEY = "apiToken";
  private static final String SECRET_VALUE = "k3y-in-a-bundle";

  private static final LowkeyVaultContainer VAULT =
      LowkeyVaultContainerBuilder.lowkeyVault("nagyesta/lowkey-vault:7.3.127").build();

  static {
    VAULT.start();
    adminClient()
        .setSecret(
            PREFIX + CONTAINER_SECRET_NAME,
            "{\"" + SECRET_KEY + "\":\"" + SECRET_VALUE + "\",\"other\":\"ignored\"}");
    AzureKeyVaultSecretStore.overrideClientForTesting(config -> adminClient());
  }

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
            .withAzureKeyVaultSecretStore(
                VAULT.getDefaultVaultBaseUrl(), PREFIX, CONTAINER_SECRET_NAME)
            .withProcessingConfig(
                processing -> processing.getEngine().getSecrets().setInterval(RESOLUTION_INTERVAL));
  }

  @AfterAll
  static void tearDown() {
    AzureKeyVaultSecretStore.overrideClientForTesting(null);
    VAULT.stop();
  }

  @BeforeEach
  void setUp() {
    applyRecordWaitTime();
  }

  @Test
  void shouldHandWorkerAValueReadFromTheJsonContainer() {
    // given - a job referencing a key that lives inside the one container secret
    deployProcessWithInput(client, processId, jobType, secretReference(SECRET_KEY));
    final long processInstanceKey = createProcessInstance(client, processId);
    awaitJobKeyOf(processInstanceKey);

    // when
    final ActivatedJob job = activateOneJob(client, jobType);

    // then - the worker is handed the JSON field's value, with no incident and no record leaking it
    assertThat(job.getVariablesAsMap()).containsEntry(INPUT_TARGET, SECRET_VALUE);
    awaitActivationExported(jobType, job.getKey());
    assertThat(incidentsOf(processInstanceKey)).isEmpty();
    assertNoRecordCarriesValue(SECRET_VALUE);
  }

  private static SecretClient adminClient() {
    return VAULT.getClientFactory().getSecretClientBuilderForDefaultVault().buildClient();
  }
}
