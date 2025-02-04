/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.management;

import io.atomix.cluster.MemberId;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.PartitionInfo;
import io.camunda.zeebe.qa.util.actuator.RebalanceActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestGateway;
import io.camunda.zeebe.qa.util.cluster.TestHealthProbe;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.asserts.TopologyAssert;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ZeebeIntegration
final class RebalancingEndpointIT {
  private static final Logger LOG = LoggerFactory.getLogger(RebalancingEndpointIT.class);

  @TestZeebe
  private final TestCluster cluster =
      TestCluster.builder()
          .withEmbeddedGateway(true)
          .withBrokersCount(3)
          .withPartitionsCount(3)
          .withReplicationFactor(3)
          .build();

  @AutoClose private CamundaClient client;

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
    RebalanceActuator.of(gateway).rebalance();
  }

  @SuppressWarnings("resource")
  private void forceBadLeaderDistribution() {
    // Only restart a broker if we currently have a good distribution.
    // Otherwise, restarting might accidentally create a good distribution and
    // `waitForBadLeaderDistribution` times out.
    if (hasGoodLeaderDistribution()) {
      final var brokerId = MemberId.from("1");
      final var stoppedBroker = cluster.brokers().get(brokerId).stop();
      LOG.debug("Broker stopped");
      waitForBadLeaderDistribution();
      LOG.debug("Bad distribution of partition: waiting for the broker to be ready");
      stoppedBroker.start().await(TestHealthProbe.READY);

      // wait until the node has rejoined the cluster before triggering rebalancing, otherwise it
      // might not be ready to be elected as it may not have caught up
      stoppedBroker.awaitCompleteTopology(
          cluster.brokers().size(),
          cluster.partitionsCount(),
          cluster.replicationFactor(),
          Duration.ofMinutes(1));
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
        .untilAsserted(
            () -> {
              final var topology = client.newTopologyRequest().send().join();
              TopologyAssert.assertThat(topology)
                  .hasLeaderForPartition(1, 0)
                  .hasLeaderForPartition(2, 1)
                  .hasLeaderForPartition(3, 2);
            });
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
