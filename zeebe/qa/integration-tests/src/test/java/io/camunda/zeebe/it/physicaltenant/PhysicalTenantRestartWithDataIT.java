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
import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.cluster.DefaultPhysicalTenantRegistry;
import io.camunda.cluster.PhysicalTenantHealth;
import io.camunda.cluster.PhysicalTenantHealth.State;
import io.camunda.zeebe.broker.PhysicalTenantLifecycleService;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
final class PhysicalTenantRestartWithDataIT {

  private static final int INSTANCES = 1000;
  private static final int RESTARTS = 5;
  private static final int BATCH = 50;

  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.none())
          .build();

  @TestZeebe
  private final TestStandaloneBroker broker =
      TENANTS.configure(new TestStandaloneBroker().withUnauthenticatedAccess());

  private CamundaClient client;

  @AfterEach
  void tearDown() {
    if (client != null) {
      client.close();
    }
  }

  @Test
  void shouldServeAgainAfterRestartWithActiveInstances() {
    // given
    final var tenantId =
        "data" + Long.toString(Math.abs(UUID.randomUUID().getMostSignificantBits()), 36);
    final var configuration = tenantConfiguration();
    lifecycle().add(tenantId, configuration);
    awaitHealthy(tenantId);
    client = TENANTS.newClientBuilder(broker, tenantId).preferRestOverGrpc(false).build();
    client
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess("waiting")
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType("never-completed"))
                .endEvent()
                .done(),
            "waiting.bpmn")
        .send()
        .join(30, TimeUnit.SECONDS);
    for (int batch = 0; batch < INSTANCES / BATCH; batch++) {
      Awaitility.await("batch of instances created")
          .atMost(Duration.ofSeconds(60))
          .ignoreExceptions()
          .until(this::createBatch);
    }

    // when
    final List<String> timings = new ArrayList<>();
    for (int i = 0; i < RESTARTS; i++) {
      final long start = System.nanoTime();
      lifecycle().replace(tenantId, configuration);
      final long restarted = System.nanoTime();
      awaitHealthy(tenantId);
      final long healthy = System.nanoTime();
      Awaitility.await()
          .atMost(Duration.ofSeconds(60))
          .ignoreExceptions()
          .until(
              () ->
                  client
                          .newPublishMessageCommand()
                          .messageName("after-restart")
                          .correlationKey(UUID.randomUUID().toString())
                          .send()
                          .join(10, TimeUnit.SECONDS)
                      != null);
      final long serving = System.nanoTime();
      timings.add(
          "replace=%dms healthy=%dms serving=%dms"
              .formatted(
                  TimeUnit.NANOSECONDS.toMillis(restarted - start),
                  TimeUnit.NANOSECONDS.toMillis(healthy - start),
                  TimeUnit.NANOSECONDS.toMillis(serving - start)));
    }

    // then
    System.out.printf(
        "Tenant restart with %d active instances: %s%n", INSTANCES, String.join(", ", timings));
    assertThat(timings).hasSize(RESTARTS);
    assertThat(
            client
                .newCreateInstanceCommand()
                .bpmnProcessId("waiting")
                .latestVersion()
                .send()
                .join(10, TimeUnit.SECONDS)
                .getProcessInstanceKey())
        .isPositive();
  }

  private boolean createBatch() {
    final List<CamundaFuture<ProcessInstanceEvent>> created = new ArrayList<>();
    for (int i = 0; i < BATCH; i++) {
      created.add(
          client.newCreateInstanceCommand().bpmnProcessId("waiting").latestVersion().send());
    }
    created.forEach(future -> future.join(30, TimeUnit.SECONDS));
    return true;
  }

  private void awaitHealthy(final String tenantId) {
    Awaitility.await()
        .atMost(Duration.ofSeconds(60))
        .pollInterval(Duration.ofMillis(10))
        .until(
            () ->
                registry().health(tenantId).map(PhysicalTenantHealth::state).orElse(null)
                    == State.HEALTHY);
  }

  private Map<String, Object> tenantConfiguration() {
    final Map<String, Object> configuration = new HashMap<>();
    configuration.put("data.secondary-storage.type", "none");
    configuration.put("security.initialization.default-roles.admin.users[0]", "admin");
    if (broker
        .unifiedConfig()
        .getData()
        .getExporters()
        .containsKey(TestStandaloneBroker.RECORDING_EXPORTER_ID)) {
      configuration.put("data.exporters-assigned[0]", TestStandaloneBroker.RECORDING_EXPORTER_ID);
    }
    return configuration;
  }

  private PhysicalTenantLifecycleService lifecycle() {
    return broker.bean(PhysicalTenantLifecycleService.class);
  }

  private DefaultPhysicalTenantRegistry registry() {
    return broker.bean(DefaultPhysicalTenantRegistry.class);
  }
}
