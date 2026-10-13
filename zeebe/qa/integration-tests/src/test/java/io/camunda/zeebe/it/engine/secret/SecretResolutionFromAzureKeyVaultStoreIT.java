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
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.backtickedSecretReference;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.createProcessInstance;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.deployProcessWithInput;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.incidentsOf;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.primingPoll;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.secretReference;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.uniqueHyphenatedSecretName;
import static io.camunda.zeebe.it.engine.secret.SecretResolutionTestHarness.uniqueSecretName;
import static org.assertj.core.api.Assertions.assertThat;

import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;
import com.github.nagyesta.lowkeyvault.testcontainers.LowkeyVaultContainer;
import com.github.nagyesta.lowkeyvault.testcontainers.LowkeyVaultContainerBuilder;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.secretstore.azure.AzureKeyVaultSecretStore;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Covers what a worker actually receives for a job whose input mapping references a secret, against
 * a real gateway, a real client and the real Azure Key Vault secret store — the production {@code
 * AzureKeyVaultSecretStore.fromConfig} path, its one-by-one resolver, its cache and its concurrency
 * wrapper — talking to a Lowkey Vault emulator over the wire. The module-level suite ({@code
 * AzureKeyVaultSecretStoreIT}) proves the store resolves against a real Key Vault server in
 * isolation; what only this level can show is that the value a worker is handed came through that
 * store along the full broker → gateway → engine → job path, and that no exported record leaks it.
 *
 * <p>The store the broker builds authenticates with {@code DefaultAzureCredential} over a
 * certificate-validating HTTP client, which cannot reach the emulator's self-signed endpoint. So
 * the test installs an emulator-trusting {@link SecretClient} through {@link
 * AzureKeyVaultSecretStore#overrideClientForTesting} before the broker's context builds the store —
 * done in the static initializer, which the JLS guarantees runs before the {@code @TestZeebe}
 * extension reads any static member of this class, {@link #VAULT} included. Everything else on the
 * build path — the probe, the resolver choice, the cache and the concurrency wrap — stays
 * production code.
 *
 * <p>This is a container-backed suite, so it shares one broker and one emulator across its tests;
 * each test uses its own secret name, since the cache is per broker and a reused name would be warm
 * from the first test on and never exercise the cache-miss path.
 */
@ZeebeIntegration
final class SecretResolutionFromAzureKeyVaultStoreIT {

  private static final String PREFIX = "camunda-";

  private static final LowkeyVaultContainer VAULT =
      LowkeyVaultContainerBuilder.lowkeyVault("nagyesta/lowkey-vault:7.3.127").build();

  static {
    VAULT.start();
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
            .withAzureKeyVaultSecretStore(VAULT.getDefaultVaultBaseUrl(), PREFIX)
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
  void shouldHandWorkerTheSecretResolvedFromKeyVault() {
    // given - a secret the vault holds under the configured prefix, referenced by a job's input
    // mapping
    final String secretName = uniqueSecretName();
    final String secretValue = "value-of-" + secretName;
    seedSecret(secretName, secretValue);
    deployProcessWithInput(client, processId, jobType, secretReference(secretName));
    final long processInstanceKey = createProcessInstance(client, processId);
    awaitJobKeyOf(processInstanceKey);

    // when
    final ActivatedJob job = activateOneJob(client, jobType);

    // then - the value the vault holds reached the worker, raised no incident and leaked into no
    // record
    assertThat(job.getVariablesAsMap()).containsEntry(INPUT_TARGET, secretValue);
    awaitActivationExported(jobType, job.getKey());
    assertThat(incidentsOf(processInstanceKey)).isEmpty();
    assertNoRecordCarriesValue(secretValue);
  }

  @Test
  void shouldResolveBacktickedHyphenatedReference() {
    // given - a dashed name, the shape a Key Vault secret routinely has (db-password), referenced
    // the only way FEEL allows: backtick-escaped
    final String secretName = uniqueHyphenatedSecretName();
    final String secretValue = "value-of-" + secretName;
    seedSecret(secretName, secretValue);
    deployProcessWithInput(client, processId, jobType, backtickedSecretReference(secretName));
    final long processInstanceKey = createProcessInstance(client, processId);
    awaitJobKeyOf(processInstanceKey);

    // when
    final ActivatedJob job = activateOneJob(client, jobType);

    // then - the dash survives the reference, the store lookup and the injector
    assertThat(job.getVariablesAsMap()).containsEntry(INPUT_TARGET, secretValue);
    awaitActivationExported(jobType, job.getKey());
    assertThat(incidentsOf(processInstanceKey)).isEmpty();
    assertNoRecordCarriesValue(secretValue);
  }

  @Test
  void shouldRaiseIncidentForMissingSecretThenResolveOnceSeeded() {
    // given - a job referencing a secret the vault does not hold yet
    final String secretName = uniqueSecretName();
    final String secretValue = "value-of-" + secretName;
    deployProcessWithInput(client, processId, jobType, secretReference(secretName));
    final long processInstanceKey = createProcessInstance(client, processId);
    awaitJobKeyOf(processInstanceKey);

    // when - a poll parks the job and the background resolution asks the vault for a secret it does
    // not have
    assertThat(primingPoll(client, jobType)).isEmpty();

    // then - the waiting job gets an incident naming its secret
    final Record<IncidentRecordValue> incident =
        awaitSecretResolutionIncident(processInstanceKey, secretName);

    // when - the secret is created and the operator resolves the incident
    seedSecret(secretName, secretValue);
    client.newResolveIncidentCommand(incident.getKey()).send().join();

    // then - the job is handed out again, now with the value the vault holds
    final ActivatedJob job = activateOneJob(client, jobType);
    assertThat(job.getVariablesAsMap()).containsEntry(INPUT_TARGET, secretValue);
    awaitActivationExported(jobType, job.getKey());
    assertNoRecordCarriesValue(secretValue);
  }

  @Test
  void shouldRaiseIncidentForExpiredSecret() {
    // given - a secret the vault holds but whose expiry is already in the past. Key Vault's own
    // enabled/expiry model, honored by the store, is the Azure-specific decision this pins: an
    // expired secret is a miss, not a value handed to a worker
    final String secretName = uniqueSecretName();
    final KeyVaultSecret expired =
        new KeyVaultSecret(PREFIX + secretName, "value-of-" + secretName);
    expired.getProperties().setExpiresOn(OffsetDateTime.now().minusMinutes(5));
    adminClient().setSecret(expired);
    deployProcessWithInput(client, processId, jobType, secretReference(secretName));
    final long processInstanceKey = createProcessInstance(client, processId);
    awaitJobKeyOf(processInstanceKey);

    // when
    assertThat(primingPoll(client, jobType)).isEmpty();

    // then - the expired secret resolves as a miss and the job gets an incident, not the value
    awaitSecretResolutionIncident(processInstanceKey, secretName);
    assertNoRecordCarriesValue("value-of-" + secretName);
  }

  private static SecretClient adminClient() {
    return VAULT.getClientFactory().getSecretClientBuilderForDefaultVault().buildClient();
  }

  private static void seedSecret(final String name, final String value) {
    adminClient().setSecret(PREFIX + name, value);
  }

  private static Record<IncidentRecordValue> awaitSecretResolutionIncident(
      final long processInstanceKey, final String secretName) {
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withErrorType(ErrorType.SECRET_RESOLUTION_ERROR)
            .getFirst();
    assertThat(incident.getValue().getErrorMessage()).contains(secretName);
    return incident;
  }
}
