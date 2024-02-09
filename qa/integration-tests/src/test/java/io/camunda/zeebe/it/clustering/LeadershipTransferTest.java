/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.clustering;

import static io.camunda.zeebe.qa.util.topology.ClusterActuatorAssert.assertThat;
import static org.awaitility.Awaitility.await;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestHealthProbe;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.asserts.TopologyAssert;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Tests that leadership transfer works as expected.
 *
 * <p>Leadership transfer is supposed to make the failover process faster. We start with a cluster
 * with an unreasonable election timeout of 10 minutes.
 *
 * <p>To ensure that this cluster can start and elect a leader for the only partition, we start with
 * replication factor 1. This ensures that the only member becomes leader without waiting for the 10
 * minutes election timeout.
 *
 * <p>We then have the other two members join the partition such that we have replication factor 3.
 * This allows us to stop the initial member and await a new leader to be elected.
 *
 * <p>Usually this would time out because the election timeout is so high. With leadership transfer,
 * we don't depend on the timeout and can assert that another member becomes leader.
 */
@ZeebeIntegration
final class LeadershipTransferTest {
  @TestZeebe(awaitStarted = true, awaitReady = false, awaitCompleteTopology = false)
  static TestCluster cluster =
      TestCluster.builder()
          .withEmbeddedGateway(true)
          .withBrokersCount(3)
          .withPartitionsCount(1)
          .withReplicationFactor(1)
          .withBrokerConfig(
              broker ->
                  broker.brokerConfig().getCluster().setElectionTimeout(Duration.ofMinutes(10)))
          .build();

  @BeforeAll
  static void setup() {
    // Only wait for the first broker to be ready, the others won't become ready until we join them
    cluster.brokers().get(MemberId.from("0")).probe(TestHealthProbe.READY);

    final var clusterActuator = ClusterActuator.of(cluster.availableGateway());
    final var scaleUp = clusterActuator.scaleBrokers(List.of(0, 1, 2));
    await().untilAsserted(() -> assertThat(cluster).hasAppliedChanges(scaleUp));

    final var firstJoin = clusterActuator.joinPartition(1, 1, 2);
    await().untilAsserted(() -> assertThat(cluster).hasAppliedChanges(firstJoin));

    final var secondJoin = clusterActuator.joinPartition(2, 1, 3);
    await().untilAsserted(() -> assertThat(cluster).hasAppliedChanges(secondJoin));

    // now wait for the full cluster to be ready
    cluster.await(TestHealthProbe.READY);
  }

  @Test
  void canFailOverThroughLeadershipTransfer() {
    // given
    final var initialLeader = cluster.brokers().get(MemberId.from("0"));

    // when
    initialLeader.stop();

    // then
    try (final var client = cluster.newClientBuilder().build()) {
      await("new leader is elected")
          .untilAsserted(
              () ->
                  TopologyAssert.assertThat(client.newTopologyRequest().send().join())
                      // partition has a leader and it's not (still) the stopped broker
                      .hasLeaderForPartition(
                          1, leader -> leader != null && leader.getNodeId() != 0));
    }
  }
}
