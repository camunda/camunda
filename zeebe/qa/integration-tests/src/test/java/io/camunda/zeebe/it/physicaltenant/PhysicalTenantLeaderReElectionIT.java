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
import io.camunda.client.CamundaClient;
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
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

/**
 * A physical tenant is a Raft partition group of its own (see {@code PhysicalTenantResolver}),
 * spanning every broker in the cluster just like the {@code default} group. Losing a broker must
 * therefore trigger leader re-election independently in every physical tenant's group, and the
 * cluster must remain available for every physical tenant once re-election completes - not just for
 * {@code default}.
 */
@ZeebeIntegration
final class PhysicalTenantLeaderReElectionIT {

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
  void shouldReelectLeadersForAllPhysicalTenantsWhenBrokerIsKilled() {
    // given - every partition of every physical tenant's group has a leader
    GROUPS.forEach(this::awaitLeaderForEveryPartition);
    final Map<String, Map<Integer, MemberId>> leadersBefore =
        GROUPS.stream().collect(Collectors.toMap(g -> g, this::currentLeaders));

    // a broker leading at least one partition in both groups is picked when one exists, so the
    // kill exercises re-election in both groups at once; otherwise any broker still leads at
    // least one partition in one of the two groups (3 partitions, RF3, 3 brokers)
    final var victim = pickVictim(leadersBefore);

    // when - the leader is killed
    cluster.brokers().get(victim).stop();

    // then - every partition of every physical tenant's group has a new leader among the
    // surviving brokers, and any partition the victim used to lead now has a different leader
    GROUPS.forEach(group -> awaitLeaderForEveryPartitionAmongSurvivors(group, victim));
    GROUPS.forEach(
        group -> {
          final var leadersAfter = currentLeaders(group);
          leadersBefore
              .get(group)
              .forEach(
                  (partition, previousLeader) -> {
                    if (previousLeader.equals(victim)) {
                      assertThat(leadersAfter.get(partition))
                          .describedAs(
                              "new leader of partition %d in group '%s' after killing %s",
                              partition, group, victim)
                          .isNotEqualTo(victim);
                    }
                  });
        });

    // and - the cluster remains available for both physical tenants
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

  // prefers a broker leading at least one partition in both groups, so a single kill exercises
  // re-election in both groups; falls back to any leader if no such broker exists
  private MemberId pickVictim(final Map<String, Map<Integer, MemberId>> leadersBefore) {
    final var defaultLeaders =
        leadersBefore.get(PhysicalTenantsITHelper.DEFAULT_TENANT_ID).values();
    final var tenantALeaders = leadersBefore.get(TENANT_A).values();
    return defaultLeaders.stream()
        .filter(tenantALeaders::contains)
        .findFirst()
        .orElseGet(() -> defaultLeaders.iterator().next());
  }

  private void awaitLeaderForEveryPartition(final String group) {
    Awaitility.await("every partition of physical tenant '" + group + "' has a leader")
        .atMost(Duration.ofSeconds(60))
        .pollInSameThread()
        .untilAsserted(
            () ->
                IntStream.rangeClosed(1, PARTITIONS_COUNT)
                    .forEach(partition -> assertThat(leadersOf(group, partition)).hasSize(1)));
  }

  private void awaitLeaderForEveryPartitionAmongSurvivors(
      final String group, final MemberId excludedBroker) {
    Awaitility.await(
            "every partition of physical tenant '"
                + group
                + "' has a leader among the surviving brokers after "
                + excludedBroker
                + " was killed")
        .atMost(Duration.ofSeconds(60))
        .pollInSameThread()
        .untilAsserted(
            () ->
                IntStream.rangeClosed(1, PARTITIONS_COUNT)
                    .forEach(
                        partition -> {
                          final var leaders = leadersOf(group, partition);
                          assertThat(leaders).hasSize(1);
                          assertThat(leaders).doesNotContain(excludedBroker);
                        }));
  }

  // maps every partition number (1..PARTITIONS_COUNT) of the given group to its current leader's
  // MemberId, by asking every (still reachable) broker for its own raft roles
  private Map<Integer, MemberId> currentLeaders(final String group) {
    return IntStream.rangeClosed(1, PARTITIONS_COUNT)
        .boxed()
        .collect(
            Collectors.toMap(
                partition -> partition,
                partition -> leadersOf(group, partition).iterator().next()));
  }

  private List<MemberId> leadersOf(final String group, final int partition) {
    return cluster.brokers().entrySet().stream()
        .filter(entry -> entry.getValue().isStarted())
        .filter(entry -> isLeaderOf(entry.getValue(), group, partition))
        .map(Map.Entry::getKey)
        .collect(Collectors.toList());
  }

  private static boolean isLeaderOf(
      final TestStandaloneBroker broker, final String group, final int partition) {
    final var partitionManager =
        broker.bean(Broker.class).getBrokerContext().getPartitionManagers().get(group);
    if (partitionManager == null) {
      return false;
    }
    return partitionManager.getRaftPartitions().stream()
        .filter(raftPartition -> raftPartition.id().number() == partition)
        .anyMatch(raftPartition -> raftPartition.getRole() == Role.LEADER);
  }

  private static void publishMessage(final CamundaClient client) {
    client
        .newPublishMessageCommand()
        .messageName("reelection-msg")
        .correlationKey(UUID.randomUUID().toString())
        .send()
        .join();
  }
}
