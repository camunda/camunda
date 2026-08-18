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

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestClusterBuilder;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Verifies that a partition join and a partition leave through {@code
 * /actuator/cluster/brokers/{brokerId}/partitions/{partitionId}} act on the physical tenant the
 * request names (issue #60078), and on no other.
 *
 * <p>Partition ids restart at 1 in every physical tenant, so both tenants here run a partition
 * numbered 1. Before the endpoints took a {@code physicalTenant} parameter they resolved that
 * number against the default tenant whatever the operator meant, which made repairing a non-default
 * tenant's replication impossible and silently changed the default tenant instead.
 *
 * <p>The replicas each tenant starts with are read from the cluster rather than assumed: which
 * broker a single-replica partition lands on is the distributor's business, and the test only needs
 * one broker that holds tenant A's partition and one that does not.
 */
@ZeebeIntegration
final class PhysicalTenantPartitionJoinLeaveIT {

  private static final String TENANT_A = "tenanta";
  private static final int PARTITION_ID = 1;
  private static final int BROKERS_COUNT = 2;
  private static final int PARTITIONS_COUNT = 1;
  private static final int REPLICATION_FACTOR = 1;
  private static final int JOIN_PRIORITY = 1;

  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.none())
          .withTenant(TENANT_A, Storage.none())
          .build();

  @TestZeebe
  private final TestCluster cluster =
      new TestClusterBuilder()
          .withBrokersCount(BROKERS_COUNT)
          .withReplicationFactor(REPLICATION_FACTOR)
          .withPartitionsCount(PARTITIONS_COUNT)
          .withBrokerConfig(
              broker ->
                  TENANTS.configure(
                      broker
                          .withUnauthenticatedAccess()
                          // TestClusterBuilder sizes the actor threads as
                          // (partitions * replicationFactor) / brokers, which is 0 here, and a
                          // scheduler with no threads cannot start a broker at all. Size them for
                          // both tenants' partitions on one broker, which is what the join
                          // produces.
                          .withUnifiedConfig(
                              camunda -> {
                                camunda.getSystem().setCpuThreadCount(2);
                                camunda.getSystem().setIoThreadCount(2);
                              })))
          .build();

  @Test
  void shouldJoinAndLeaveOnlyTheNamedPhysicalTenantsPartition() {
    // given — each tenant's partition 1 has a single replica, and the default tenant's replicas are
    // recorded so any change to them is visible
    final var actuator = ClusterActuator.of(cluster.availableGateway());
    awaitReplicas(actuator, TENANT_A, REPLICATION_FACTOR);
    final var tenantAHolder = replicasOf(actuator, TENANT_A).iterator().next();
    final var defaultReplicas = replicasOf(actuator, PhysicalTenantsITHelper.DEFAULT_TENANT_ID);
    final var joiningBroker =
        cluster.brokers().keySet().stream()
            .map(MemberId::id)
            .filter(id -> !id.equals(tenantAHolder))
            .findFirst()
            .orElseThrow();

    // when — that other broker joins partition 1 of tenant A
    awaitAccepted(
        "the cluster accepts the join",
        () ->
            actuator.joinPartition(
                Integer.parseInt(joiningBroker), PARTITION_ID, JOIN_PRIORITY, TENANT_A));

    // then — tenant A's partition 1 gains the replica...
    await("tenant A's partition is replicated by the joining broker")
        .atMost(Duration.ofSeconds(120))
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(replicasOf(actuator, TENANT_A))
                    .containsExactlyInAnyOrder(tenantAHolder, joiningBroker));
    // ...and the default tenant's identically numbered partition is untouched
    assertThat(replicasOf(actuator, PhysicalTenantsITHelper.DEFAULT_TENANT_ID))
        .isEqualTo(defaultReplicas);

    // when — the same broker leaves partition 1 of tenant A again
    awaitAccepted(
        "the cluster accepts the leave",
        () -> actuator.leavePartition(Integer.parseInt(joiningBroker), PARTITION_ID, TENANT_A));

    // then — tenant A is back to its single replica, and the default tenant still never moved
    await("tenant A's partition is no longer replicated by that broker")
        .atMost(Duration.ofSeconds(120))
        .ignoreExceptions()
        .untilAsserted(
            () -> assertThat(replicasOf(actuator, TENANT_A)).containsExactly(tenantAHolder));
    assertThat(replicasOf(actuator, PhysicalTenantsITHelper.DEFAULT_TENANT_ID))
        .isEqualTo(defaultReplicas);
  }

  /** The ids of the brokers replicating {@link #PARTITION_ID} of the given physical tenant. */
  private Set<String> replicasOf(final ClusterActuator actuator, final String physicalTenantId) {
    return actuator.getTopology(physicalTenantId).getBrokers().stream()
        .filter(
            broker ->
                broker.getPartitions().stream()
                    .anyMatch(partition -> Objects.equals(partition.getId(), PARTITION_ID)))
        .map(broker -> broker.getId().toString())
        .collect(Collectors.toSet());
  }

  private void awaitReplicas(
      final ClusterActuator actuator, final String physicalTenantId, final int expected) {
    await(
            "physical tenant '%s' replicates its partition %d times"
                .formatted(physicalTenantId, expected))
        .atMost(Duration.ofSeconds(120))
        .ignoreExceptions()
        .untilAsserted(() -> assertThat(replicasOf(actuator, physicalTenantId)).hasSize(expected));
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
