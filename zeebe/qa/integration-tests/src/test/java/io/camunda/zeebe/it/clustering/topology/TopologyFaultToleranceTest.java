/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.clustering.topology;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.api.response.PartitionInfo;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.gateway.impl.configuration.GatewayCfg;
import io.camunda.zeebe.it.clustering.ClusteringRuleExtension;
import io.camunda.zeebe.test.util.asserts.TopologyAssert;
import java.time.Duration;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Checks that even with brokers connecting/disconnecting, the topology is eventually consistent.
 *
 * <p>NOTE: this could be a good candidate for randomized testing.
 */
final class TopologyFaultToleranceTest {
  private final int clusterSize = 3;
  private final int partitionsCount = 3;
  private final int replicationFactor = 3;

  @RegisterExtension
  private final ClusteringRuleExtension clusterRule =
      new ClusteringRuleExtension(
          partitionsCount,
          replicationFactor,
          clusterSize,
          this::configureBroker,
          this::configureGateway);

  @Test
  void shouldDetectTopologyChanges() {
    for (int nodeId = 0; nodeId < clusterSize; nodeId++) {
      // when
      final var broker = clusterRule.getBroker(nodeId);
      clusterRule.disconnect(broker);
      awaitBrokerIsRemovedFromTopology(nodeId);
      clusterRule.connect(broker);

      // then
      awaitTopologyIsComplete();
    }
  }

  @Test
  void shouldDetectIncompleteTopology() {
    // when - disconnect two nodes
    IntStream.range(0, clusterSize - 1)
        .parallel()
        .forEach(
            nodeId -> {
              clusterRule.disconnect(clusterRule.getBroker(nodeId));
              awaitBrokerIsRemovedFromTopology(nodeId);
            });

    // then
    Awaitility.await("topology is a single node, follower for all partitions")
        .atMost(Duration.ofSeconds(15))
        .pollInterval(Duration.ofMillis(250))
        .untilAsserted(
            () ->
                TopologyAssert.assertThat(clusterRule.getTopologyFromClient())
                    .hasBrokersCount(1)
                    .hasBrokerSatisfying(
                        broker -> {
                          assertThat(broker.getNodeId()).isEqualTo(clusterSize - 1);
                          assertThat(broker.getPartitions())
                              .describedAs("has %d follower partitions", partitionsCount)
                              .hasSize(partitionsCount)
                              .allMatch(Predicate.not(PartitionInfo::isLeader));
                        }));
  }

  private void awaitTopologyIsComplete() {
    Awaitility.await("fail over occurs and topology is complete")
        .atMost(Duration.ofSeconds(300))
        .pollInterval(Duration.ofMillis(500))
        .untilAsserted(
            () ->
                TopologyAssert.assertThat(clusterRule.getTopologyFromClient())
                    .isComplete(
                        clusterRule.getClusterSize(),
                        clusterRule.getPartitionCount(),
                        clusterRule.getReplicationFactor()));
  }

  private void awaitBrokerIsRemovedFromTopology(final int nodeId) {
    Awaitility.await("broker " + nodeId + " is removed from topology")
        .atMost(Duration.ofSeconds(30))
        .pollInterval(Duration.ofMillis(500))
        .untilAsserted(
            () ->
                TopologyAssert.assertThat(clusterRule.getTopologyFromClient())
                    .doesNotContainBroker(nodeId));
  }

  private void configureBroker(final BrokerCfg config) {
    // configures the broker for faster detection of failures
    config
        .getCluster()
        .getMembership()
        .setSyncInterval(Duration.ofSeconds(1))
        .setFailureTimeout(Duration.ofSeconds(1))
        .setBroadcastUpdates(true)
        .setProbeTimeout(Duration.ofMillis(100))
        .setProbeInterval(Duration.ofMillis(250))
        .setGossipInterval(Duration.ofMillis(250));
  }

  private void configureGateway(final GatewayCfg config) {
    // configures the gateway for faster detection of failures
    config
        .getCluster()
        .getMembership()
        .setSyncInterval(Duration.ofSeconds(1))
        .setFailureTimeout(Duration.ofSeconds(1))
        .setBroadcastUpdates(true)
        .setProbeTimeout(Duration.ofMillis(100))
        .setProbeInterval(Duration.ofMillis(250))
        .setGossipInterval(Duration.ofMillis(250));
  }
}
