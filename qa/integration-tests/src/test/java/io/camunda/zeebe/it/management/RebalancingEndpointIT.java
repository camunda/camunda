/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.management;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.PartitionInfo;
import io.camunda.zeebe.qa.util.actuator.RebalanceActuator;
import io.camunda.zeebe.qa.util.cluster.TestGateway;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneCluster;
import io.camunda.zeebe.qa.util.cluster.ZeebeHealthProbe;
import io.camunda.zeebe.qa.util.cluster.junit.ManageTestNodes;
import io.camunda.zeebe.qa.util.cluster.junit.ManageTestNodes.TestCluster;
import io.camunda.zeebe.test.util.junit.AutoCloseResources;
import io.camunda.zeebe.test.util.junit.AutoCloseResources.AutoCloseResource;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@ManageTestNodes
@AutoCloseResources
final class RebalancingEndpointIT {
  @TestCluster
  private final TestStandaloneCluster cluster =
      TestStandaloneCluster.builder()
          .withEmbeddedGateway(true)
          .withBrokersCount(3)
          .withPartitionsCount(3)
          .withReplicationFactor(3)
          .build();

  @AutoCloseResource private ZeebeClient client;

  @BeforeEach
  void setup() {
    // we use broker 1 as the "restart" broker if we have a good leadership distribution, so make
    // sure the client is from another broker
    client = cluster.brokers().get(MemberId.from("0")).newClientBuilder().build();
  }

  @Test
  void shouldRebalanceCluster() {
    // given
    forceBadLeaderDistribution();

    // when
    triggerRebalancing();

    // then
    reachesGoodLeaderDistribution();
  }

  private void triggerRebalancing() {
    final TestGateway<?> gateway = cluster.availableGateway();
    RebalanceActuator.ofAddress(gateway.monitoringAddress()).rebalance();
  }

  private void forceBadLeaderDistribution() {
    // Only restart a broker if we currently have a good distribution.
    // Otherwise, restarting might accidentally create a good distribution and
    // `waitForBadLeaderDistribution` times out.
    if (hasGoodLeaderDistribution()) {
      final var brokerId = MemberId.from("1");
      final var stoppedBroker = cluster.brokers().get(brokerId).stop();
      waitForBadLeaderDistribution();
      stoppedBroker.start().await(ZeebeHealthProbe.READY);
    }
  }

  private void waitForBadLeaderDistribution() {
    Awaitility.await("At least one broker is leader for more than one partition")
        .timeout(Duration.ofSeconds(30))
        .during(Duration.ofSeconds(10))
        .until(this::hasBadLeaderDistribution);
  }

  private void reachesGoodLeaderDistribution() {
    Awaitility.await("All brokers are leader for exactly one partition")
        .timeout(Duration.ofSeconds(30))
        .during(Duration.ofSeconds(10))
        .until(this::hasGoodLeaderDistribution);
  }

  private boolean hasBadLeaderDistribution() {
    return client.newTopologyRequest().send().join().getBrokers().stream()
        .anyMatch(
            brokerInfo ->
                brokerInfo.getPartitions().stream().filter(PartitionInfo::isLeader).count() > 1);
  }

  private boolean hasGoodLeaderDistribution() {
    return client.newTopologyRequest().send().join().getBrokers().stream()
        .allMatch(
            brokerInfo ->
                brokerInfo.getPartitions().stream().filter(PartitionInfo::isLeader).count() == 1);
  }
}
