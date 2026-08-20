/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.clustering.topology;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.PartitionInfo;
import io.camunda.client.api.response.Topology;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.asserts.TopologyAssert;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Checks that even with brokers stopping and restarting, the cluster topology is eventually
 * consistent. Uses {@link TestCluster} with stop/start for broker isolation.
 */
@ZeebeIntegration
final class TopologyFaultToleranceIT {
  @TestZeebe
  private final TestCluster cluster =
      TestCluster.builder()
          .withBrokersCount(2)
          .withPartitionsCount(1)
          .withReplicationFactor(2)
          .withGatewaysCount(1)
          .withEmbeddedGateway(false)
          .withBrokerConfig(
              broker ->
                  broker
                      .withSecondaryStorageType(SecondaryStorageType.none)
                      .withUnauthenticatedAccess()
                      .withUnifiedConfig(TopologyFaultToleranceIT::configureFastMembership))
          .withGatewayConfig(
              gateway ->
                  gateway.withUnifiedConfig(TopologyFaultToleranceIT::configureFastMembership))
          .build();

  @AutoClose private CamundaClient client;

  @BeforeEach
  void setUp() {
    client = cluster.newClientBuilder().build();
  }

  @Test
  void shouldDetectTopologyChanges() {
    // given
    final var brokers = cluster.brokers();

    // when - then
    for (var broker : brokers.values()) {
      broker.stop();
      awaitBrokerIsRemovedFromTopology(broker);

      broker.start();
      awaitTopologyIsComplete();
    }
  }

  @Test
  void shouldDetectIncompleteTopology() {
    // given
    final var brokerId = 0;
    final var broker = cluster.brokers().get(MemberId.from(String.valueOf(brokerId)));

    // when - stop one broker to lose quorum; broker 0 is stopped, broker 1 remains
    broker.stop();
    awaitBrokerIsRemovedFromTopology(broker);

    // then - the remaining broker should be a follower for all partitions (no quorum to lead)
    Awaitility.await("topology is a single node, follower for all partitions")
        .atMost(Duration.ofMinutes(2))
        .pollInterval(Duration.ofMillis(250))
        .untilAsserted(
            () ->
                TopologyAssert.assertThat(getTopologyFromClient())
                    .hasBrokersCount(1)
                    .hasBrokerSatisfying(
                        b -> {
                          assertThat(b.getNodeId()).isOne();
                          assertThat(b.getPartitions())
                              .describedAs("has %d follower partitions", cluster.partitionsCount())
                              .hasSize(cluster.partitionsCount())
                              .allMatch(Predicate.not(PartitionInfo::isLeader));
                        }));
  }

  private void awaitTopologyIsComplete() {
    Awaitility.await("topology is complete")
        .atMost(Duration.ofMinutes(2))
        .pollInterval(Duration.ofMillis(250))
        .untilAsserted(
            () ->
                TopologyAssert.assertThat(getTopologyFromClient())
                    .isComplete(
                        cluster.brokers().size(),
                        cluster.partitionsCount(),
                        cluster.replicationFactor()));
  }

  private void awaitBrokerIsRemovedFromTopology(final TestStandaloneBroker broker) {
    final var nodeId = Integer.parseInt(broker.nodeId().id());

    Awaitility.await("broker " + nodeId + " is removed from topology")
        .atMost(Duration.ofMinutes(2))
        .pollInterval(Duration.ofMillis(250))
        .untilAsserted(
            () -> TopologyAssert.assertThat(getTopologyFromClient()).doesNotContainBroker(nodeId));
  }

  private Topology getTopologyFromClient() {
    return client.newTopologyRequest().send().join(5, TimeUnit.SECONDS);
  }

  private static void configureFastMembership(final Camunda config) {
    final var membership = config.getCluster().getMembership();

    membership.setSyncInterval(Duration.ofSeconds(1));
    membership.setFailureTimeout(Duration.ofSeconds(1));
    membership.setBroadcastUpdates(true);
    membership.setProbeTimeout(Duration.ofMillis(100));
    membership.setProbeInterval(Duration.ofMillis(250));
    membership.setGossipInterval(Duration.ofMillis(250));
  }
}
