/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.it.clustering.topology;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.it.clustering.ClusteringRule;
import io.zeebe.broker.it.util.GrpcClientRule;
import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.client.api.response.PartitionInfo;
import io.zeebe.test.util.asserts.TopologyAssert;
import java.time.Duration;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import org.awaitility.Awaitility;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

/**
 * Checks that even with brokers connecting/disconnecting, the topology is eventually consistent.
 *
 * <p>NOTE: this could be a good candidate for randomized testing.
 */
public final class TopologyFaultToleranceTest {
  private final int clusterSize = 3;
  private final int partitionsCount = 3;
  private final int replicationFactor = 3;

  private final ClusteringRule clusterRule =
      new ClusteringRule(
          partitionsCount, replicationFactor, clusterSize, this::configureFastGossip);
  private final GrpcClientRule clientRule = new GrpcClientRule(clusterRule);

  @Rule public final RuleChain ruleChain = RuleChain.outerRule(clusterRule).around(clientRule);

  @Test
  public void shouldDetectTopologyChanges() {
    for (int nodeId = 0; nodeId < clusterSize; nodeId++) {
      // when
      disconnect(nodeId);
      awaitBrokerIsRemovedFromTopology(nodeId);
      connect(nodeId);

      // then
      awaitTopologyIsComplete();
    }
  }

  @Test
  public void shouldDetectIncompleteTopology() {
    // when - disconnect two nodes
    IntStream.range(0, clusterSize - 1)
        .parallel()
        .forEach(
            nodeId -> {
              disconnect(nodeId);
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

  private void disconnect(final int nodeId) {
    clusterRule.disconnect(clusterRule.getBroker(nodeId));
  }

  private void connect(final int nodeId) {
    clusterRule.connect(clusterRule.getBroker(nodeId));
  }

  private void awaitTopologyIsComplete() {
    Awaitility.await("fail over occurs and topology is complete")
        .atMost(Duration.ofSeconds(15))
        .pollInterval(Duration.ofMillis(500))
        .untilAsserted(
            () ->
                TopologyAssert.assertThat(clusterRule.getTopologyFromClient())
                    .isComplete(clusterRule.getClusterSize(), clusterRule.getPartitionCount()));
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

  private void configureFastGossip(final BrokerCfg config) {
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
