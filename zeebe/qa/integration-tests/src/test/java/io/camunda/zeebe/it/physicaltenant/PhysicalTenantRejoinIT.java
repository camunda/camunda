/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.physicaltenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.atomix.cluster.MemberId;
import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.partition.RaftPartition;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.PartitionBrokerRole;
import io.camunda.client.api.response.PartitionInfo;
import io.camunda.client.api.response.Topology;
import io.camunda.zeebe.broker.Broker;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestClusterBuilder;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

/**
 * A physical tenant is a Raft partition group of its own (see {@code PhysicalTenantResolver}),
 * spanning every broker in the cluster just like the {@code default} group. A broker that restarts
 * after being stopped must therefore rejoin every physical tenant's partition group it used to host
 * - not just {@code default}'s.
 */
@ZeebeIntegration
final class PhysicalTenantRejoinIT {

  private static final String TENANT_A = "tenanta";
  private static final List<String> GROUPS =
      List.of(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, TENANT_A);
  private static final int PARTITIONS_COUNT = 3;

  // withPtConfig is flattened as a diff against a pristine default Camunda(): an empty modifier
  // produces an empty diff, so the tenant would never be discovered and never bootstrapped.
  // Routing through PhysicalTenantsITHelper both supplies a real diff (secondary storage = none)
  // and declares the per-tenant security.initialization block that
  // PhysicalTenantRequiredOverrideValidation requires once a non-default tenant is declared.
  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.none())
          .withTenant(TENANT_A, Storage.none())
          .build();

  @TestZeebe
  private final TestCluster cluster =
      new TestClusterBuilder()
          .withBrokersCount(3)
          .withReplicationFactor(3)
          .withPartitionsCount(PARTITIONS_COUNT)
          .withBrokerConfig(broker -> TENANTS.configure(broker.withUnauthenticatedAccess()))
          .build();

  @Test
  void shouldRejoinAllPhysicalTenantPartitionGroupsWhenBrokerRestarts() {
    // given - every partition of every physical tenant's group has a leader, and the broker under
    // test hosts partitions in both groups (replicationFactor == brokersCount, so every broker
    // hosts every partition of every group)
    GROUPS.forEach(this::awaitLeaderForEveryPartition);
    final var restarted = cluster.brokers().keySet().iterator().next();

    // when - the broker is stopped; the cluster re-stabilises on the survivors...
    cluster.brokers().get(restarted).stop();
    GROUPS.forEach(group -> awaitLeaderForEveryPartitionAmongSurvivors(group, restarted));

    // ...and the broker is restarted
    cluster.brokers().get(restarted).start();

    // then - the restarted broker rejoins every partition of every physical tenant's group in an
    // active raft role (FOLLOWER or LEADER, not INACTIVE)
    GROUPS.forEach(group -> awaitBrokerRejoinsEveryPartition(restarted, group));

    // and - both engines remain writable
    try (final var defaultClient =
            TENANTS
                .newClientBuilder(
                    cluster.availableGateway(), PhysicalTenantsITHelper.DEFAULT_TENANT_ID)
                .build();
        final var tenantAClient =
            TENANTS.newClientBuilder(cluster.availableGateway(), TENANT_A).build()) {
      assertThatCode(() -> publishMessage(defaultClient)).doesNotThrowAnyException();
      assertThatCode(() -> publishMessage(tenantAClient)).doesNotThrowAnyException();
    }
  }

  // uses the per-physical-tenant topology endpoint (client.newTopologyRequest()) rather than
  // inspecting the broker bean directly, exercising the same public contract clients rely on
  private void awaitLeaderForEveryPartition(final String group) {
    try (final var client = TENANTS.newClientBuilder(cluster.availableGateway(), group).build()) {
      Awaitility.await("every partition of physical tenant '" + group + "' has a leader")
          .atMost(Duration.ofSeconds(60))
          .pollInSameThread()
          .untilAsserted(
              () -> {
                final var topology = client.newTopologyRequest().send().join();
                IntStream.rangeClosed(1, PARTITIONS_COUNT)
                    .forEach(
                        partition -> assertThat(hasLeaderInTopology(topology, partition)).isTrue());
              });
    }
  }

  private static boolean hasLeaderInTopology(final Topology topology, final int partitionId) {
    return topology.getBrokers().stream()
        .flatMap(broker -> broker.getPartitions().stream())
        .anyMatch(partition -> partition.getPartitionId() == partitionId && partition.isLeader());
  }

  private void awaitLeaderForEveryPartitionAmongSurvivors(
      final String group, final MemberId excludedBroker) {
    Awaitility.await(
            "every partition of physical tenant '"
                + group
                + "' has a leader among the surviving brokers after "
                + excludedBroker
                + " was stopped")
        .atMost(Duration.ofSeconds(60))
        .pollInSameThread()
        .untilAsserted(
            () ->
                IntStream.rangeClosed(1, PARTITIONS_COUNT)
                    .forEach(
                        partition ->
                            assertThat(hasLeaderAmongSurvivors(group, partition, excludedBroker))
                                .isTrue()));
  }

  private boolean hasLeaderAmongSurvivors(
      final String group, final int partition, final MemberId excludedBroker) {
    return cluster.brokers().entrySet().stream()
        .filter(entry -> !entry.getKey().equals(excludedBroker))
        .filter(entry -> entry.getValue().isStarted())
        .anyMatch(entry -> roleOf(entry.getValue(), group, partition) == Role.LEADER);
  }

  // asserts that, for every partition the restarted broker should host in the given group, it now
  // reports an active role (FOLLOWER or LEADER) rather than INACTIVE. Uses the per-physical-tenant
  // topology endpoint (client.newTopologyRequest()) rather than inspecting the broker bean
  // directly, exercising the same public contract clients rely on.
  private void awaitBrokerRejoinsEveryPartition(final MemberId restarted, final String group) {
    try (final var client = TENANTS.newClientBuilder(cluster.availableGateway(), group).build()) {
      Awaitility.await(
              "broker "
                  + restarted
                  + " rejoins every partition of physical tenant '"
                  + group
                  + "' in an active role")
          .atMost(Duration.ofSeconds(90))
          .pollInSameThread()
          .untilAsserted(
              () -> {
                assertThat(cluster.brokers().get(restarted).isStarted()).isTrue();
                final var topology = client.newTopologyRequest().send().join();
                final var brokerInfo =
                    topology.getBrokers().stream()
                        .filter(broker -> broker.getMemberId().equals(restarted.id()))
                        .findFirst()
                        .orElse(null);
                assertThat(brokerInfo)
                    .describedAs(
                        "physical tenant '%s' topology entry for restarted broker %s",
                        group, restarted)
                    .isNotNull();
                final var hostedPartitions =
                    brokerInfo.getPartitions().stream()
                        .map(PartitionInfo::getPartitionId)
                        .collect(Collectors.toSet());
                assertThat(hostedPartitions)
                    .describedAs(
                        "partitions hosted by restarted broker %s in physical tenant '%s'",
                        restarted, group)
                    .containsExactlyInAnyOrderElementsOf(expectedPartitions());
                final Set<PartitionBrokerRole> roles =
                    brokerInfo.getPartitions().stream()
                        .map(PartitionInfo::getRole)
                        .collect(Collectors.toSet());
                assertThat(roles)
                    .describedAs(
                        "roles reported by the topology for restarted broker %s in physical tenant"
                            + " '%s'",
                        restarted, group)
                    .doesNotContain((PartitionBrokerRole) null, PartitionBrokerRole.INACTIVE);
              });
    }
  }

  // replicationFactor == brokersCount, so every broker hosts every partition of every group
  private static Set<Integer> expectedPartitions() {
    return IntStream.rangeClosed(1, PARTITIONS_COUNT).boxed().collect(Collectors.toSet());
  }

  private static Role roleOf(
      final TestStandaloneBroker broker, final String group, final int partition) {
    final var partitionManager =
        broker.bean(Broker.class).getBrokerContext().getPartitionManagers().get(group);
    if (partitionManager == null) {
      return null;
    }
    return partitionManager.getRaftPartitions().stream()
        .filter(raftPartition -> raftPartition.id().number() == partition)
        .map(RaftPartition::getRole)
        .findFirst()
        .orElse(null);
  }

  private static void publishMessage(final CamundaClient client) {
    client
        .newPublishMessageCommand()
        .messageName("rejoin-msg")
        .correlationKey(UUID.randomUUID().toString())
        .send()
        .join();
  }
}
