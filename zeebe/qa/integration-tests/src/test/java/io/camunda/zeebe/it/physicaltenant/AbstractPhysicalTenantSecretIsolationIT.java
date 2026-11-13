/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.physicaltenant;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.configuration.Camunda;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.Strings;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Contract tests for physical-tenant secret store isolation: two tenants each configure a store of
 * their own, both holding a secret of the same name with a different value, and tenant B holds one
 * more that tenant A must never see. Each tenant's jobs and listings only ever see its own store.
 *
 * <p>A store is configured and built per physical tenant at broker startup, so nothing below the
 * running application can show that the engine of one tenant's partitions reads the right one.
 *
 * <p>Subclasses back each tenant's store with {@link #SECRETS_BY_TENANT} and register it through
 * {@link #configureStore(String, Camunda)}; a new store type gets this coverage by implementing
 * that one hook.
 */
@ZeebeIntegration
abstract class AbstractPhysicalTenantSecretIsolationIT {

  protected static final String TENANT_A = "tenanta";
  protected static final String TENANT_B = "tenantb";

  // fixed, not random: the build sets these names and values as the PT_ENV_SECRET_IT_* variables
  // that PhysicalTenantEnvSecretIsolationIT reads (failsafe <environmentVariables> in pom.xml, the
  // 'it' task in build.gradle.kts), so a change here must be made there too
  private static final String SECRET_NAME = "API_KEY";
  private static final String ONLY_B_NAME = "ONLY_B";
  private static final String SECRET_VALUE_A = "value-of-tenant-a";
  private static final String SECRET_VALUE_B = "value-of-tenant-b";
  private static final String ONLY_B_VALUE = "only-tenant-b";

  /** The secrets, by name, that each tenant's store must hold. */
  protected static final Map<String, Map<String, String>> SECRETS_BY_TENANT =
      Map.of(
          TENANT_A,
          Map.of(SECRET_NAME, SECRET_VALUE_A),
          TENANT_B,
          Map.of(SECRET_NAME, SECRET_VALUE_B, ONLY_B_NAME, ONLY_B_VALUE));

  private static final String REFERENCE_PREFIX = "camunda.secrets.";
  private static final String INPUT_TARGET = "token";

  // random, unlike the secrets above and like every other id this suite uses, so nothing this test
  // deploys can collide with what another test left behind
  private static final String PROCESS_ID = Strings.newRandomValidBpmnId();
  private static final String JOB_TYPE = Strings.newRandomValidBpmnId();

  private static final Duration RESOLUTION_INTERVAL = Duration.ofMillis(200);
  private static final Duration LONG_POLL_TIMEOUT = Duration.ofSeconds(30);
  private static final Duration JOB_TIMEOUT = Duration.ofMinutes(5);

  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.none())
          .withTenant(TENANT_A, Storage.none())
          .withTenant(TENANT_B, Storage.none())
          .build();

  @TestZeebe
  private final TestStandaloneBroker broker =
      withPerTenantSecretStores(
          TENANTS.configure(new TestStandaloneBroker().withUnauthenticatedAccess()));

  @AutoClose private CamundaClient tenantAClient;
  @AutoClose private CamundaClient tenantBClient;

  /**
   * Registers, in the given tenant's configuration, a store holding exactly that tenant's entry of
   * {@link #SECRETS_BY_TENANT}.
   *
   * <p>The broker replays this hook on every start, twice per start (see {@code
   * TestSpringApplication#physicalTenantProperties}), so it must only change the configuration it
   * is handed. Seed the store's secrets outside it, e.g. once in a static initializer.
   */
  protected abstract void configureStore(String tenantId, Camunda tenantConfig);

  @BeforeEach
  void startClients() {
    tenantAClient = TENANTS.newClientBuilder(broker, TENANT_A).build();
    tenantBClient = TENANTS.newClientBuilder(broker, TENANT_B).build();
  }

  @Test
  void shouldHandEachTenantTheSecretOfItsOwnStore() {
    // given
    deployAndCreateInstance(tenantAClient);
    deployAndCreateInstance(tenantBClient);

    // when
    final ActivatedJob jobOfTenantA = activateOneJob(tenantAClient);
    final ActivatedJob jobOfTenantB = activateOneJob(tenantBClient);

    // then - the same reference resolves to a different value per tenant
    assertThat(jobOfTenantA.getVariablesAsMap()).containsEntry(INPUT_TARGET, SECRET_VALUE_A);
    assertThat(jobOfTenantB.getVariablesAsMap()).containsEntry(INPUT_TARGET, SECRET_VALUE_B);
  }

  @Test
  void shouldListOnlyTheSecretsOfTheTenantsOwnStore() {
    // when
    final var referencesOfTenantA =
        tenantAClient.newListSecretsCommand().send().join().getReferences();
    final var referencesOfTenantB =
        tenantBClient.newListSecretsCommand().send().join().getReferences();

    // then - tenant B's extra secret is listed for tenant B only
    assertThat(referencesOfTenantA).containsExactly(REFERENCE_PREFIX + SECRET_NAME);
    assertThat(referencesOfTenantB)
        .containsExactlyInAnyOrder(REFERENCE_PREFIX + SECRET_NAME, REFERENCE_PREFIX + ONLY_B_NAME);
  }

  private TestStandaloneBroker withPerTenantSecretStores(final TestStandaloneBroker broker) {
    SECRETS_BY_TENANT
        .keySet()
        .forEach(
            tenantId ->
                broker.withPtConfig(
                    tenantId,
                    camunda -> {
                      configureStore(tenantId, camunda);
                      camunda
                          .getProcessing()
                          .getEngine()
                          .getSecrets()
                          .setInterval(RESOLUTION_INTERVAL);
                    }));
    return broker;
  }

  private static void deployAndCreateInstance(final CamundaClient client) {
    client
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask(
                    "task",
                    task ->
                        task.zeebeJobType(JOB_TYPE)
                            .zeebeInputExpression(REFERENCE_PREFIX + SECRET_NAME, INPUT_TARGET))
                .endEvent()
                .done(),
            PROCESS_ID + ".bpmn")
        .send()
        .join();
    client.newCreateInstanceCommand().bpmnProcessId(PROCESS_ID).latestVersion().send().join();
  }

  private static ActivatedJob activateOneJob(final CamundaClient client) {
    final var jobs =
        client
            .newActivateJobsCommand()
            .jobType(JOB_TYPE)
            .maxJobsToActivate(1)
            .timeout(JOB_TIMEOUT)
            .requestTimeout(LONG_POLL_TIMEOUT)
            .send()
            .join()
            .getJobs();
    assertThat(jobs).hasSize(1);
    return jobs.get(0);
  }
}
