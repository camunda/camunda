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
import io.camunda.configuration.Secrets.FileStore;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.util.FileUtil;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;

/**
 * Covers that the secret a job is handed comes from its own physical tenant's store: two tenants
 * configure a store of their own, both holding a secret of the same name with a different value,
 * and each tenant's worker only ever sees its own.
 *
 * <p>A store is configured and built per physical tenant at broker startup, so nothing below the
 * running application can show that the engine of one tenant's partitions reads the right one.
 */
@ZeebeIntegration
final class PhysicalTenantSecretIsolationIT {

  private static final String TENANT_A = "tenanta";
  private static final String TENANT_B = "tenantb";

  private static final String SECRET_NAME = "s" + UUID.randomUUID().toString().replace("-", "");
  private static final String SECRET_VALUE_A = "value-of-tenant-a";
  private static final String SECRET_VALUE_B = "value-of-tenant-b";

  private static final String INPUT_TARGET = "token";

  // random, like every other id this suite uses, so nothing this test deploys can collide with
  // what another test left behind
  private static final String PROCESS_ID = Strings.newRandomValidBpmnId();
  private static final String JOB_TYPE = Strings.newRandomValidBpmnId();

  private static final Duration RESOLUTION_INTERVAL = Duration.ofMillis(200);
  private static final Duration LONG_POLL_TIMEOUT = Duration.ofSeconds(30);
  private static final Duration JOB_TIMEOUT = Duration.ofMinutes(5);

  private static final Map<String, String> VALUE_BY_TENANT =
      Map.of(TENANT_A, SECRET_VALUE_A, TENANT_B, SECRET_VALUE_B);

  private static final Map<String, Path> STORE_BY_TENANT =
      Map.of(TENANT_A, createStore(TENANT_A), TENANT_B, createStore(TENANT_B));

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

  @AfterAll
  static void deleteStores() {
    STORE_BY_TENANT.values().forEach(PhysicalTenantSecretIsolationIT::deleteQuietly);
  }

  @Test
  void shouldHandEachTenantTheSecretOfItsOwnStore() {
    // given
    tenantAClient = TENANTS.newClientBuilder(broker, TENANT_A).build();
    tenantBClient = TENANTS.newClientBuilder(broker, TENANT_B).build();
    deployAndCreateInstance(tenantAClient);
    deployAndCreateInstance(tenantBClient);

    // when
    final ActivatedJob jobOfTenantA = activateOneJob(tenantAClient);
    final ActivatedJob jobOfTenantB = activateOneJob(tenantBClient);

    // then - the same reference resolves to a different value per tenant
    assertThat(jobOfTenantA.getVariablesAsMap()).containsEntry(INPUT_TARGET, SECRET_VALUE_A);
    assertThat(jobOfTenantB.getVariablesAsMap()).containsEntry(INPUT_TARGET, SECRET_VALUE_B);
  }

  private static TestStandaloneBroker withPerTenantSecretStores(final TestStandaloneBroker broker) {
    STORE_BY_TENANT.forEach(
        (tenantId, directory) -> {
          final var store = new FileStore();
          store.setPath(directory.toString());
          broker.withPtConfig(
              tenantId,
              camunda -> {
                camunda.getSecrets().getStores().getFile().put("default", store);
                camunda.getProcessing().getEngine().getSecrets().setInterval(RESOLUTION_INTERVAL);
              });
        });
    return broker;
  }

  /** One directory per tenant, each holding the same secret name with the tenant's own value. */
  private static Path createStore(final String tenantId) {
    try {
      final Path directory = Files.createTempDirectory("secret-store-" + tenantId + "-");
      Files.writeString(
          directory.resolve(SECRET_NAME), VALUE_BY_TENANT.get(tenantId), StandardCharsets.UTF_8);
      return directory;
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to write the store of tenant " + tenantId, e);
    }
  }

  private static void deleteQuietly(final Path directory) {
    try {
      FileUtil.deleteFolderIfExists(directory);
    } catch (final IOException e) {
      // a leftover temp directory is not worth failing a passing test over
    }
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
                            .zeebeInputExpression("camunda.secrets." + SECRET_NAME, INPUT_TARGET))
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
