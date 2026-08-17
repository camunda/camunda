/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.physicaltenant;

import static org.awaitility.Awaitility.await;

import io.camunda.zeebe.management.cluster.ClusterConfigPatchRequest;
import io.camunda.zeebe.management.cluster.ClusterConfigPatchRequestPartitions;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
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
 * Verifies that a replication factor change through {@code PATCH /actuator/cluster} reaches every
 * physical tenant's partition group (issue #60192), not only the default one.
 *
 * <p>The replication factor is a cluster-wide setting — it cannot be scoped to a tenant, and
 * combining it with {@code physicalTenant} is rejected — so a change that reached only the default
 * tenant left the others at a durability level nobody chose, with nothing in the response saying
 * so.
 *
 * <p>The two tenants are deliberately given different partition counts. A wrong-group write would
 * otherwise be hard to see: if both ran the same number of partitions, replicating the wrong one
 * would still leave every count looking plausible.
 */
@ZeebeIntegration
final class PhysicalTenantReplicationFactorIT {

  private static final String TENANT_A = "tenanta";
  private static final int BROKERS_COUNT = 3;
  private static final int DEFAULT_TENANT_PARTITIONS_COUNT = 1;
  private static final int TENANT_A_PARTITIONS_COUNT = 2;
  private static final int INITIAL_REPLICATION_FACTOR = 1;
  private static final int TARGET_REPLICATION_FACTOR = 3;
  private static final int ACTOR_THREAD_COUNT =
      DEFAULT_TENANT_PARTITIONS_COUNT + TENANT_A_PARTITIONS_COUNT;

  // Routing through PhysicalTenantsITHelper both supplies a real config diff (secondary storage =
  // none) so the tenant is discovered and bootstrapped, and declares the per-tenant
  // security.initialization block that PhysicalTenantRequiredOverrideValidation requires once a
  // non-default tenant exists.
  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.none())
          .withTenant(TENANT_A, Storage.none())
          .build();

  @TestZeebe
  private final TestCluster cluster =
      new TestClusterBuilder()
          .withBrokersCount(BROKERS_COUNT)
          .withReplicationFactor(INITIAL_REPLICATION_FACTOR)
          .withPartitionsCount(DEFAULT_TENANT_PARTITIONS_COUNT)
          .withBrokerConfig(
              broker ->
                  TENANTS.configure(
                      broker
                          .withUnauthenticatedAccess()
                          .withPtConfig(
                              TENANT_A,
                              camunda ->
                                  camunda.getCluster().setPartitionCount(TENANT_A_PARTITIONS_COUNT))
                          // TestClusterBuilder sizes the actor threads as
                          // (partitions * replicationFactor) / brokers, which is 0 for a cluster
                          // that starts at replication factor 1 — and a scheduler with no threads
                          // cannot start a broker at all. Size them for what the brokers hold once
                          // every partition of both tenants is fully replicated.
                          .withUnifiedConfig(
                              camunda -> {
                                camunda.getSystem().setCpuThreadCount(ACTOR_THREAD_COUNT);
                                camunda.getSystem().setIoThreadCount(ACTOR_THREAD_COUNT);
                              })))
          .build();

  @Test
  void shouldReplicateEveryPhysicalTenantsPartitions() {
    // given — every partition of both tenants is held by a single broker
    awaitReplicationFactor(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, INITIAL_REPLICATION_FACTOR);
    awaitReplicationFactor(TENANT_A, INITIAL_REPLICATION_FACTOR);

    // when — the replication factor is raised, naming no tenant
    awaitAccepted(
        "the cluster accepts the replication factor change",
        () ->
            ClusterActuator.of(cluster.availableGateway())
                .patchCluster(
                    new ClusterConfigPatchRequest()
                        .partitions(
                            new ClusterConfigPatchRequestPartitions()
                                .replicationFactor(TARGET_REPLICATION_FACTOR)),
                    false,
                    false));

    // then — every partition of every tenant really gained its replicas. Asserting through each
    // tenant's own client topology, rather than the cluster configuration, is what shows the
    // replicas joined and are healthy instead of only being planned.
    awaitReplicationFactor(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, TARGET_REPLICATION_FACTOR);
    awaitReplicationFactor(TENANT_A, TARGET_REPLICATION_FACTOR);
  }

  private void awaitReplicationFactor(
      final String physicalTenantId, final int expectedReplicationFactor) {
    final var partitionCount =
        PhysicalTenantsITHelper.DEFAULT_TENANT_ID.equals(physicalTenantId)
            ? DEFAULT_TENANT_PARTITIONS_COUNT
            : TENANT_A_PARTITIONS_COUNT;
    try (final var client =
        TENANTS.newClientBuilder(cluster.availableGateway(), physicalTenantId).build()) {
      await(
              "physical tenant '%s' replicates its %d partitions %d times"
                  .formatted(physicalTenantId, partitionCount, expectedReplicationFactor))
          .atMost(Duration.ofSeconds(120))
          .ignoreExceptions()
          .untilAsserted(
              () ->
                  TopologyAssert.assertThat(client.newTopologyRequest().send().join())
                      .isComplete(BROKERS_COUNT, partitionCount, expectedReplicationFactor));
    }
  }

  /**
   * Retries {@code request} until the cluster accepts it. The request is not an assertion — it
   * either returns or throws a {@link feign.FeignException} — so it cannot be handed to {@code
   * untilAsserted}, which only retries on {@link AssertionError}. A retry is needed because the
   * cluster can still be applying its own initial configuration change when the test starts, and
   * rejects a second change while one is in progress.
   */
  private void awaitAccepted(final String alias, final Runnable request) {
    await(alias)
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptions()
        .until(
            () -> {
              request.run();
              return true;
            });
  }
}
