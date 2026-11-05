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
import io.camunda.configuration.Secrets.EnvStore;
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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Covers that each physical tenant's environment-variable store reads only the variables carrying
 * its own prefix: both tenants hold a variable for the same secret name with a different value, and
 * tenant B holds one more that tenant A must never see.
 *
 * <p>The broker runs inside this JVM, which cannot set its own environment variables, so the build
 * sets the {@code PT_ENV_SECRET_IT_*} variables on the forked test JVM; see the failsafe
 * configuration in this module's {@code pom.xml} and the {@code it} task in its {@code
 * build.gradle.kts}.
 */
@ZeebeIntegration
final class PhysicalTenantEnvSecretIsolationIT {

  private static final String TENANT_A = "tenanta";
  private static final String TENANT_B = "tenantb";

  // must match the variables the build sets on the test JVM
  private static final String PREFIX_A = "PT_ENV_SECRET_IT_TENANTA_";
  private static final String PREFIX_B = "PT_ENV_SECRET_IT_TENANTB_";

  private static final String SECRET_NAME = "API_KEY";
  private static final String ONLY_B_NAME = "ONLY_B";
  private static final String REFERENCE_PREFIX = "camunda.secrets.";
  private static final String SECRET_VALUE_A = "value-of-tenant-a";
  private static final String SECRET_VALUE_B = "value-of-tenant-b";
  private static final String ONLY_B_VALUE = "only-tenant-b";

  private static final String INPUT_TARGET = "token";

  private static final String PROCESS_ID = Strings.newRandomValidBpmnId();
  private static final String JOB_TYPE = Strings.newRandomValidBpmnId();

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
      withPerTenantEnvStores(
          TENANTS.configure(new TestStandaloneBroker().withUnauthenticatedAccess()));

  @AutoClose private CamundaClient tenantAClient;
  @AutoClose private CamundaClient tenantBClient;

  @BeforeAll
  static void requireTheVariablesTheBuildSets() {
    // run outside the build, e.g. from an IDE, the stores would be empty and the job test would
    // only fail after its long poll, without saying why
    requireVariable(PREFIX_A + SECRET_NAME, SECRET_VALUE_A);
    requireVariable(PREFIX_B + SECRET_NAME, SECRET_VALUE_B);
    requireVariable(PREFIX_B + ONLY_B_NAME, ONLY_B_VALUE);
  }

  // asserts on the one variable, never the whole environment, which a failure would print
  private static void requireVariable(final String name, final String expectedValue) {
    assertThat(System.getenv(name))
        .as(
            "%s, set by the failsafe <environmentVariables> in pom.xml and the 'it' task in"
                + " build.gradle.kts",
            name)
        .isEqualTo(expectedValue);
  }

  @BeforeEach
  void beforeEach() {
    tenantAClient = TENANTS.newClientBuilder(broker, TENANT_A).build();
    tenantBClient = TENANTS.newClientBuilder(broker, TENANT_B).build();
  }

  @Test
  void shouldHandEachTenantTheValueOfItsOwnPrefixedVariable() {
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
  void shouldListOnlyTheSecretsOfTheTenantsOwnPrefix() {
    // when
    final var referencesOfTenantA =
        tenantAClient.newListSecretsCommand().send().join().getReferences();
    final var referencesOfTenantB =
        tenantBClient.newListSecretsCommand().send().join().getReferences();

    // then - tenant B's extra variable is listed for tenant B only
    assertThat(referencesOfTenantA).containsExactly(REFERENCE_PREFIX + SECRET_NAME);
    assertThat(referencesOfTenantB)
        .containsExactlyInAnyOrder(REFERENCE_PREFIX + SECRET_NAME, REFERENCE_PREFIX + ONLY_B_NAME);
  }

  private static TestStandaloneBroker withPerTenantEnvStores(final TestStandaloneBroker broker) {
    Map.of(TENANT_A, PREFIX_A, TENANT_B, PREFIX_B)
        .forEach(
            (tenantId, prefix) -> {
              final var store = new EnvStore();
              store.setPrefix(prefix);
              broker.withPtConfig(
                  tenantId,
                  camunda -> camunda.getSecrets().getStores().getEnv().put("default", store));
            });
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
