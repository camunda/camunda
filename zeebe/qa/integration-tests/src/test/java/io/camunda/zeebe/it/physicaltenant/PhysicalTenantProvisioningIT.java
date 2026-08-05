/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.physicaltenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestClusterBuilder;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.asserts.TopologyAssert;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * A physical tenant added to a broker's static configuration after the cluster has already been
 * bootstrapped once must be picked up on the next restart: the coordinator's {@code
 * PhysicalTenantProvisioningInitializer} provisions a new partition group for it directly, without
 * a fresh full-cluster bootstrap. This verifies the whole path end-to-end: a brand-new physical
 * tenant's partitions become visible via the client-facing (physical-tenant-scoped) topology
 * endpoint, and a process can be deployed to and instantiated within it.
 */
@ZeebeIntegration
final class PhysicalTenantProvisioningIT {

  private static final String TENANT_A = "tenanta";
  private static final String TENANT_B = "tenantb";
  private static final int BROKERS_COUNT = 3;
  private static final int TENANT_B_PARTITIONS_COUNT = 2;

  // declared at cluster bootstrap time: only the default tenant and tenantA
  private static final PhysicalTenantsITHelper TENANTS_BEFORE =
      PhysicalTenantsITHelper.builder()
          .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.none())
          .withTenant(TENANT_A, Storage.none())
          .build();

  // the same tenants, plus tenantB — added to the static configuration only after the cluster has
  // already been running once, then applied on restart
  private static final PhysicalTenantsITHelper TENANTS_AFTER =
      PhysicalTenantsITHelper.builder()
          .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.none())
          .withTenant(TENANT_A, Storage.none())
          .withTenant(TENANT_B, Storage.none())
          .build();

  @TestZeebe
  private final TestCluster cluster =
      new TestClusterBuilder()
          .withBrokersCount(BROKERS_COUNT)
          .withReplicationFactor(BROKERS_COUNT)
          .withPartitionsCount(2)
          .withBrokerConfig(broker -> TENANTS_BEFORE.configure(broker.withUnauthenticatedAccess()))
          .build();

  @Test
  void shouldProvisionNewPhysicalTenantAddedAfterInitialBootstrapOnRestart() {
    // given - the cluster is up and running with only the default tenant and tenantA
    try (final var tenantAClient =
        TENANTS_BEFORE.newClientBuilder(cluster.availableGateway(), TENANT_A).build()) {
      await("tenantA's topology is complete before the restart")
          .atMost(Duration.ofSeconds(60))
          .untilAsserted(
              () ->
                  TopologyAssert.assertThat(tenantAClient.newTopologyRequest().send().join())
                      .isComplete(BROKERS_COUNT, 2, BROKERS_COUNT));
    }

    // when - tenantB is added to every broker's static configuration and the cluster is restarted
    cluster.shutdown();
    cluster
        .brokers()
        .values()
        .forEach(
            broker -> {
              TENANTS_AFTER.configure(broker);
              broker.withPtConfig(
                  TENANT_B,
                  camunda -> camunda.getCluster().setPartitionCount(TENANT_B_PARTITIONS_COUNT));
            });
    cluster.start().awaitCompleteTopology();

    // then - tenantB's own partition group is visible through the client-facing, physical-tenant-
    // scoped topology endpoint (the same endpoint used to observe any other physical tenant),
    // with the partition count configured for tenantB specifically - not cloned from another
    // tenant's partition count
    try (final var tenantBClient =
        TENANTS_AFTER.newClientBuilder(cluster.availableGateway(), TENANT_B).build()) {
      await("tenantB's newly-provisioned partitions are visible and have a leader")
          .atMost(Duration.ofSeconds(60))
          .ignoreExceptions()
          .untilAsserted(
              () ->
                  TopologyAssert.assertThat(tenantBClient.newTopologyRequest().send().join())
                      .isComplete(BROKERS_COUNT, TENANT_B_PARTITIONS_COUNT, BROKERS_COUNT));

      // and - a process can be deployed to and instantiated within the newly-provisioned tenant
      final String processId = "provisioning-process";
      final BpmnModelInstance process =
          Bpmn.createExecutableProcess(processId).startEvent().endEvent().done();

      await("deployment to the newly-provisioned tenantB succeeds")
          .atMost(Duration.ofSeconds(30))
          .ignoreExceptions()
          .untilAsserted(
              () ->
                  assertThat(
                          tenantBClient
                              .newDeployResourceCommand()
                              .addProcessModel(process, processId + ".bpmn")
                              .send()
                              .join()
                              .getProcesses())
                      .isNotEmpty());

      final long processInstanceKey =
          tenantBClient
              .newCreateInstanceCommand()
              .bpmnProcessId(processId)
              .latestVersion()
              .send()
              .join()
              .getProcessInstanceKey();
      assertThat(processInstanceKey).isPositive();
    }

    // and - tenantA, which already existed before the restart, is unaffected
    try (final var tenantAClient =
        TENANTS_AFTER.newClientBuilder(cluster.availableGateway(), TENANT_A).build()) {
      TopologyAssert.assertThat(tenantAClient.newTopologyRequest().send().join())
          .isComplete(BROKERS_COUNT, 2, BROKERS_COUNT);
    }
  }
}
