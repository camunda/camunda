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
import io.camunda.zeebe.management.cluster.PlannedOperationsResponse;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestClusterBuilder;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.cluster.TestZeebePort;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.asserts.TopologyAssert;
import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies that a broker count change through {@code POST /actuator/cluster/brokers} reaches every
 * physical tenant's partition group (issue #60193), not only the default one.
 *
 * <p>Before this, adding a broker gave capacity only the default tenant could use, and removing one
 * was rejected outright — the plan moved only the default tenant's partitions off the departing
 * broker, so the member leave was refused because the broker still held another tenant's
 * partitions.
 *
 * <p>Both tenants deliberately run the same number of partitions. Six partitions spread over three
 * brokers by a single cluster-wide round robin put one partition of *each* tenant on every broker,
 * so a plan that only ever saw the default group leaves the third broker without any {@code
 * tenanta} partition — visible in the per-tenant topology rather than only in a plan.
 */
@ZeebeIntegration
final class PhysicalTenantBrokerScalingIT {

  private static final String TENANT_A = "tenanta";
  private static final int INITIAL_BROKERS_COUNT = 2;
  private static final int PARTITIONS_PER_TENANT = 3;
  private static final int REPLICATION_FACTOR = 1;
  // TestClusterBuilder sizes the actor threads as (partitions * replicationFactor) / brokers, which
  // counts one tenant's partitions only: here that is a single thread, for the partitions of both
  // tenants a broker ends up hosting. Size them for every partition of both tenants instead.
  private static final int ACTOR_THREAD_COUNT = 2 * PARTITIONS_PER_TENANT;

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
          .withBrokersCount(INITIAL_BROKERS_COUNT)
          .withReplicationFactor(REPLICATION_FACTOR)
          .withPartitionsCount(PARTITIONS_PER_TENANT)
          .withBrokerConfig(broker -> configureTenants(broker, INITIAL_BROKERS_COUNT))
          .build();

  private TestStandaloneBroker addedBroker;

  @AfterEach
  void closeAddedBroker() {
    if (addedBroker != null) {
      addedBroker.close();
      addedBroker = null;
    }
  }

  @Test
  void shouldPlacePartitionsOfEveryTenantOnAnAddedBroker() {
    // given — both tenants run their partitions on the two initial brokers
    awaitTopology(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, INITIAL_BROKERS_COUNT);
    awaitTopology(TENANT_A, INITIAL_BROKERS_COUNT);

    // when — a third broker joins the cluster
    final int newBrokerId = INITIAL_BROKERS_COUNT;
    final int newClusterSize = INITIAL_BROKERS_COUNT + 1;
    addedBroker = startBroker(newBrokerId, newClusterSize);
    scaleBrokers(newClusterSize);

    // then — every tenant places a partition on it, not just the default one. isComplete alone
    // would not catch this: with three partitions at replication factor one it holds even when the
    // new broker sits empty and all three stay on the original two.
    awaitTopology(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, newClusterSize);
    awaitTopology(TENANT_A, newClusterSize);
    awaitHoldsAPartition(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, newBrokerId);
    awaitHoldsAPartition(TENANT_A, newBrokerId);
  }

  @Test
  void shouldMoveEveryTenantsPartitionsOffARemovedBroker() {
    // given — a third broker holding a partition of each tenant
    final int removedBrokerId = INITIAL_BROKERS_COUNT;
    final int grownClusterSize = INITIAL_BROKERS_COUNT + 1;
    addedBroker = startBroker(removedBrokerId, grownClusterSize);
    scaleBrokers(grownClusterSize);
    awaitHoldsAPartition(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, removedBrokerId);
    awaitHoldsAPartition(TENANT_A, removedBrokerId);

    // when — that broker is removed again
    scaleBrokers(INITIAL_BROKERS_COUNT);
    awaitLeftTheCluster(removedBrokerId);
    // Stop it only once it has left, so it no longer gossips its broker info into the gateway's
    // topology — mirroring what ScaleDownBrokersTest does for a decommissioned broker.
    addedBroker.close();
    addedBroker = null;

    // then — every partition of both tenants is healthy on the two remaining brokers, which is only
    // true if each tenant moved what the departing broker held
    awaitTopology(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, INITIAL_BROKERS_COUNT);
    awaitTopology(TENANT_A, INITIAL_BROKERS_COUNT);
  }

  private void awaitLeftTheCluster(final int brokerId) {
    final var actuator = ClusterActuator.of(cluster.availableGateway());
    await("broker %d has left the cluster configuration".formatted(brokerId))
        .atMost(Duration.ofMinutes(2))
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(actuator.getTopology().getBrokers())
                    .extracting(brokerState -> brokerState.getId().toString())
                    .doesNotContain(String.valueOf(brokerId)));
  }

  /**
   * Submits the broker set through {@code POST /actuator/cluster/brokers}. Completion is not
   * awaited here but through each tenant's own topology, which is what shows the partitions really
   * moved rather than only that a plan finished.
   *
   * <p>A retry is needed because the cluster can still be applying its own initial configuration
   * change when the test starts, and rejects a second change while one is in progress. The request
   * either returns or throws a {@link feign.FeignException}, so it cannot be handed to {@code
   * untilAsserted}, which only retries on {@link AssertionError}.
   */
  private void scaleBrokers(final int newClusterSize) {
    final var actuator = ClusterActuator.of(cluster.availableGateway());
    final var brokerIds = IntStream.range(0, newClusterSize).boxed().toList();
    final var response = new PlannedOperationsResponse[1];
    await("the cluster accepts the broker set %s".formatted(brokerIds))
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptions()
        .until(
            () -> {
              response[0] = actuator.scaleBrokers(brokerIds);
              return true;
            });
    assertThat(response[0].getPlannedChanges()).isNotEmpty();
  }

  /**
   * Asserts through the tenant's own client that its partitions are healthy on {@code size}
   * brokers.
   */
  private void awaitTopology(final String physicalTenantId, final int clusterSize) {
    try (final var client =
        TENANTS.newClientBuilder(cluster.availableGateway(), physicalTenantId).build()) {
      await(
              "physical tenant '%s' has a complete topology over %d brokers"
                  .formatted(physicalTenantId, clusterSize))
          .atMost(Duration.ofMinutes(2))
          .ignoreExceptions()
          .untilAsserted(
              () ->
                  TopologyAssert.assertThat(client.newTopologyRequest().send().join())
                      .isComplete(clusterSize, PARTITIONS_PER_TENANT, REPLICATION_FACTOR));
    }
  }

  private void awaitHoldsAPartition(final String physicalTenantId, final int brokerId) {
    try (final var client =
        TENANTS.newClientBuilder(cluster.availableGateway(), physicalTenantId).build()) {
      await(
              "physical tenant '%s' holds a partition on broker %d"
                  .formatted(physicalTenantId, brokerId))
          .atMost(Duration.ofMinutes(1))
          .ignoreExceptions()
          .untilAsserted(
              () ->
                  assertThat(client.newTopologyRequest().send().join().getBrokers())
                      .filteredOn(broker -> broker.getNodeId() == brokerId)
                      .singleElement()
                      .satisfies(
                          broker ->
                              assertThat(broker.getPartitions())
                                  .describedAs(
                                      "partitions of tenant '%s' on broker %d",
                                      physicalTenantId, brokerId)
                                  .isNotEmpty()));
    }
  }

  /** Starts a broker outside the {@link TestCluster}, configured like the ones inside it. */
  private TestStandaloneBroker startBroker(final int nodeId, final int clusterSize) {
    final var contactPoint =
        cluster.brokers().get(MemberId.from("0")).address(TestZeebePort.CLUSTER);
    final var broker =
        configureTenants(new TestStandaloneBroker(), clusterSize)
            .withUnifiedConfig(
                camunda -> {
                  camunda.getCluster().setName(cluster.name());
                  camunda.getCluster().setNodeId(nodeId);
                  camunda.getCluster().setInitialContactPoints(List.of(contactPoint));
                });
    broker.start();
    return broker;
  }

  private static TestStandaloneBroker configureTenants(
      final TestStandaloneBroker broker, final int clusterSize) {
    return TENANTS.configure(
        broker
            .withUnauthenticatedAccess()
            .withPtConfig(
                TENANT_A, camunda -> camunda.getCluster().setPartitionCount(PARTITIONS_PER_TENANT))
            .withUnifiedConfig(
                camunda -> {
                  camunda.getCluster().setSize(clusterSize);
                  camunda.getSystem().setCpuThreadCount(ACTOR_THREAD_COUNT);
                  camunda.getSystem().setIoThreadCount(ACTOR_THREAD_COUNT);
                }));
  }
}
