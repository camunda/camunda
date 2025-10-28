/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.clustering;

import io.atomix.cluster.MemberId;
import io.camunda.client.CamundaClient;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestClusterBuilder;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.asserts.TopologyAssert;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
final class GossipClusteringIT {
  @TestZeebe
  private final TestCluster cluster =
      new TestClusterBuilder()
          .withBrokerConfig(
              broker ->
                  broker.withBrokerConfig(
                      config ->
                          config
                              .getCluster()
                              .getMembership()
                              .setFailureTimeout(Duration.ofMillis(2000))
                              .setGossipInterval(Duration.ofMillis(150))
                              .setProbeInterval(Duration.ofMillis(250))
                              .setProbeInterval(Duration.ofMillis(250))))
          .withBrokersCount(3)
          .withReplicationFactor(3)
          .build();

  @AutoClose private CamundaClient client;

  @BeforeEach
  void beforeEach() {
    client = cluster.newClientBuilder().build();
  }

  @Test
  void shouldStartCluster() {
    // given - when - then
    cluster.awaitCompleteTopology();
  }

  @Test
  void shouldDistributePartitionsAndLeaderInformationInCluster() {
    // when - then
    TopologyAssert.assertThat(client.newTopologyRequest().send().join())
        .hasLeaderForEachPartition(1);
  }

  @Test
  void shouldRemoveMemberFromTopology() {
    // given
    final var stoppedBroker = cluster.brokers().get(MemberId.from("2"));

    // when
    stoppedBroker.stop();

    // then
    Awaitility.await("until broker is removed from topology")
        .atMost(Duration.ofSeconds(30))
        .pollInterval(Duration.ofMillis(250))
        .pollInSameThread()
        .untilAsserted(
            () ->
                TopologyAssert.assertThat(client.newTopologyRequest().send().join())
                    .doesNotContainBroker(2)
                    .hasLeaderForEachPartition(1));
  }

  @Test
  void shouldRemoveLeaderFromCluster() {
    // given
    final var leader = cluster.leaderForPartition(1);
    final var leaderId = Integer.parseInt(leader.nodeId().id());

    // when
    leader.stop();

    // then
    Awaitility.await("until leader is removed from topology")
        .atMost(Duration.ofSeconds(30))
        .pollInterval(Duration.ofMillis(250))
        .pollInSameThread()
        .untilAsserted(
            () ->
                TopologyAssert.assertThat(client.newTopologyRequest().send().join())
                    .doesNotContainBroker(leaderId)
                    .hasLeaderForEachPartition(1));
  }
}
