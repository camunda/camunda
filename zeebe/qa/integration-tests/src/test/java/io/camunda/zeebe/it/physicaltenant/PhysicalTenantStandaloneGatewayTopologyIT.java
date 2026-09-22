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

import io.camunda.client.api.response.PartitionBrokerRole;
import io.camunda.client.api.response.PartitionInfo;
import io.camunda.client.api.response.Topology;
import io.camunda.configuration.SecondaryStorage;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestClusterBuilder;
import io.camunda.zeebe.qa.util.cluster.TestGateway;
import io.camunda.zeebe.qa.util.cluster.TestHealthProbe;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.asserts.TopologyAssert;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

/** All other physical-tenant topology ITs use an embedded gateway. */
@ZeebeIntegration
final class PhysicalTenantStandaloneGatewayTopologyIT {

  private static final String TENANT_A = "tenanta";
  private static final int BROKERS_COUNT = 3;
  private static final int DEFAULT_TENANT_PARTITIONS_COUNT = 3;
  private static final int TENANT_A_PARTITIONS_COUNT = 2;
  private static final Map<String, Integer> GROUP_PARTITIONS_COUNT =
      Map.of(
          PhysicalTenantsITHelper.DEFAULT_TENANT_ID,
          DEFAULT_TENANT_PARTITIONS_COUNT,
          TENANT_A,
          TENANT_A_PARTITIONS_COUNT);

  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(
              PhysicalTenantsITHelper.DEFAULT_TENANT_ID,
              Storage.none(),
              DEFAULT_TENANT_PARTITIONS_COUNT)
          .withTenant(TENANT_A, Storage.none(), TENANT_A_PARTITIONS_COUNT)
          .build();

  @TestZeebe
  private final TestCluster cluster =
      new TestClusterBuilder()
          .withBrokersCount(BROKERS_COUNT)
          .withReplicationFactor(BROKERS_COUNT)
          .withPartitionsCount(DEFAULT_TENANT_PARTITIONS_COUNT)
          .withEmbeddedGateway(false)
          .withGatewaysCount(1)
          .withBrokerConfig(broker -> TENANTS.configure(broker.withUnauthenticatedAccess()))
          // A standalone gateway resolves its own PhysicalTenantIds independently of the broker.
          .withGatewayConfig(
              gateway ->
                  gateway
                      // TestClusterBuilder's default gatewayConfig also applies
                      // withUnauthenticatedAccess(), but a custom withGatewayConfig(Consumer<...>)
                      // replaces rather than extends it
                      .withUnauthenticatedAccess()
                      .withUnifiedConfig(
                          uc -> applyRdbmsH2(uc.getData().getSecondaryStorage(), "gw-default"))
                      .withPtConfig(
                          TENANT_A,
                          camunda -> {
                            // A standalone gateway fails fast if no secondary storage is configured
                            // with basic auth.
                            applyRdbmsH2(camunda.getData().getSecondaryStorage(), "gw-tenanta");
                            camunda
                                .getSecurity()
                                .getInitialization()
                                .setDefaultRoles(
                                    Map.of(
                                        "admin",
                                        Map.of("users", List.of(TENANTS.adminUsername(TENANT_A)))));
                          }))
          .build();

  private static void applyRdbmsH2(final SecondaryStorage secondaryStorage, final String dbName) {
    secondaryStorage.setType(SecondaryStorageType.rdbms);
    final var rdbms = secondaryStorage.getRdbms();
    rdbms.setUrl(
        "jdbc:h2:mem:" + dbName + "-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
    rdbms.setUsername("sa");
    rdbms.setPassword("");
  }

  @Test
  void shouldResolveTopologyForEveryPhysicalTenantThroughStandaloneGateway() {
    // given - a cluster served solely by a standalone gateway (embedded gateway disabled)
    final var gateway = cluster.gateways().values().iterator().next();

    // when / then - every physical tenant's partitions, leaders, and roles resolve correctly
    // through the standalone gateway
    GROUP_PARTITIONS_COUNT.keySet().forEach(group -> awaitCompleteTopology(gateway, group));

    // when - the standalone gateway itself restarts
    gateway.stop().start();

    // then - topology still resolves correctly for every physical tenant through the restarted
    // standalone gateway
    GROUP_PARTITIONS_COUNT.keySet().forEach(group -> awaitCompleteTopology(gateway, group));

    // when - a broker is stopped while the standalone gateway remains up...
    final var restarted = cluster.brokers().keySet().iterator().next();
    final var restartedNodeId = Integer.parseInt(restarted.id());
    cluster.brokers().get(restarted).stop();
    GROUP_PARTITIONS_COUNT
        .keySet()
        .forEach(group -> awaitLeaderAmongSurvivors(gateway, group, restartedNodeId));
    // ...and the standalone gateway itself was never bounced during the outage
    assertThatCode(() -> gateway.probe(TestHealthProbe.READY))
        .as("standalone gateway stayed operational while a broker was down")
        .doesNotThrowAnyException();

    // ...then the broker restarts
    cluster.brokers().get(restarted).start();

    // then - topology resolves correctly for every physical tenant again
    GROUP_PARTITIONS_COUNT.keySet().forEach(group -> awaitCompleteTopology(gateway, group));
    GROUP_PARTITIONS_COUNT
        .keySet()
        .forEach(group -> awaitBrokerRejoinsEveryPartition(gateway, group, restartedNodeId));
  }

  private void awaitCompleteTopology(final TestGateway<?> gateway, final String group) {
    final var partitionsCount = GROUP_PARTITIONS_COUNT.get(group);
    try (final var client = TENANTS.newClientBuilder(gateway, group).build()) {
      Awaitility.await(
              "physical tenant '"
                  + group
                  + "' has a complete topology through the standalone gateway")
          .atMost(Duration.ofSeconds(60))
          .pollInSameThread()
          .ignoreExceptions()
          .untilAsserted(
              () -> {
                final var topology = client.newTopologyRequest().send().join();
                TopologyAssert.assertThat(topology)
                    .isComplete(BROKERS_COUNT, partitionsCount, BROKERS_COUNT);
                assertResolvedRoles(topology, partitionsCount);
              });
    }
  }

  private static void assertResolvedRoles(final Topology topology, final int partitionsCount) {
    final var partitionsById =
        topology.getBrokers().stream()
            .flatMap(broker -> broker.getPartitions().stream())
            .collect(Collectors.groupingBy(PartitionInfo::getPartitionId));

    assertThat(partitionsById)
        .describedAs("topology partitions with resolved roles")
        .hasSize(partitionsCount);
    partitionsById.forEach(
        (partitionId, replicas) -> {
          assertThat(replicas)
              .describedAs("replicas of partition %d", partitionId)
              .extracting(PartitionInfo::getRole)
              .containsOnly(PartitionBrokerRole.LEADER, PartitionBrokerRole.FOLLOWER);
          assertThat(replicas)
              .describedAs("leaders of partition %d", partitionId)
              .filteredOn(PartitionInfo::isLeader)
              .hasSize(1);
        });
  }

  private void awaitLeaderAmongSurvivors(
      final TestGateway<?> gateway, final String group, final int excludedNodeId) {
    final var partitionsCount = GROUP_PARTITIONS_COUNT.get(group);
    try (final var client = TENANTS.newClientBuilder(gateway, group).build()) {
      Awaitility.await(
              "physical tenant '"
                  + group
                  + "' has a leader for every partition among the surviving brokers, through the"
                  + " standalone gateway, after broker "
                  + excludedNodeId
                  + " was stopped")
          .atMost(Duration.ofSeconds(60))
          .pollInSameThread()
          .ignoreExceptions()
          .untilAsserted(
              () ->
                  TopologyAssert.assertThat(client.newTopologyRequest().send().join())
                      .doesNotContainBroker(excludedNodeId)
                      .hasLeaderForEachPartition(partitionsCount));
    }
  }

  private void awaitBrokerRejoinsEveryPartition(
      final TestGateway<?> gateway, final String group, final int restartedNodeId) {
    final var partitionsCount = GROUP_PARTITIONS_COUNT.get(group);
    try (final var client = TENANTS.newClientBuilder(gateway, group).build()) {
      Awaitility.await(
              "broker "
                  + restartedNodeId
                  + " rejoins every partition of physical tenant '"
                  + group
                  + "' in an active role, as seen through the standalone gateway")
          .atMost(Duration.ofSeconds(90))
          .pollInSameThread()
          .ignoreExceptions()
          .untilAsserted(
              () -> {
                final var topology = client.newTopologyRequest().send().join();
                final var brokerInfo =
                    topology.getBrokers().stream()
                        .filter(broker -> broker.getNodeId() == restartedNodeId)
                        .findFirst()
                        .orElse(null);
                assertThat(brokerInfo)
                    .describedAs(
                        "physical tenant '%s' topology entry for restarted broker %d",
                        group, restartedNodeId)
                    .isNotNull();
                final var hostedPartitions =
                    brokerInfo.getPartitions().stream()
                        .map(PartitionInfo::getPartitionId)
                        .collect(Collectors.toSet());
                assertThat(hostedPartitions)
                    .describedAs(
                        "partitions hosted by restarted broker %d in physical tenant '%s'",
                        restartedNodeId, group)
                    .containsExactlyInAnyOrderElementsOf(expectedPartitions(partitionsCount));
                final Set<PartitionBrokerRole> roles =
                    brokerInfo.getPartitions().stream()
                        .map(PartitionInfo::getRole)
                        .collect(Collectors.toSet());
                assertThat(roles)
                    .describedAs(
                        "roles reported by the topology for restarted broker %d in physical"
                            + " tenant '%s'",
                        restartedNodeId, group)
                    .doesNotContain((PartitionBrokerRole) null, PartitionBrokerRole.INACTIVE);
              });
    }
  }

  private static Set<Integer> expectedPartitions(final int partitionsCount) {
    return IntStream.rangeClosed(1, partitionsCount).boxed().collect(Collectors.toSet());
  }
}
