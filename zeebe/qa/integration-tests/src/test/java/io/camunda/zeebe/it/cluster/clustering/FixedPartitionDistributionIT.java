/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.clustering;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.broker.system.configuration.partitioning.FixedPartitionCfg;
import io.camunda.zeebe.broker.system.configuration.partitioning.FixedPartitionCfg.NodeCfg;
import io.camunda.zeebe.broker.system.configuration.partitioning.Scheme;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;

final class FixedPartitionDistributionIT {
  @AutoClose private TestCluster cluster;

  @Test
  void shouldDistributePartitions() {
    // given
    cluster =
        TestCluster.builder()
            .withBrokersCount(3)
            .withEmbeddedGateway(true)
            .withPartitionsCount(3)
            .withReplicationFactor(2)
            .withBrokerConfig(this::configureBroker)
            .build();
    cluster.start().awaitCompleteTopology();

    // when - then
    final var brokerZeroPartitions = List.of(2, 3);
    final var brokerOnePartitions = List.of(1, 3);
    final var brokerTwoPartitions = List.of(1, 2);

    assertPartitionsDistributedPerBroker(brokerZeroPartitions, 0);
    assertPartitionsDistributedPerBroker(brokerOnePartitions, 1);
    assertPartitionsDistributedPerBroker(brokerTwoPartitions, 2);
  }

  private void assertPartitionsDistributedPerBroker(
      final List<Integer> expectedPartitionIds, final Integer nodeId) {
    final var broker = cluster.brokers().get(MemberId.from(String.valueOf(nodeId)));
    final var client = PartitionsActuator.of(broker);
    final var partitions = client.query();

    assertThat(partitions)
        .as("broker %d should report entries for the expected partitions", nodeId)
        .containsOnlyKeys(expectedPartitionIds);
  }

  private void configureBroker(final TestStandaloneBroker broker) {
    broker.withBrokerConfig(
        cfg -> {
          cfg.getCluster().getRaft().setEnablePriorityElection(false);

          final var partitioning = cfg.getExperimental().getPartitioning();
          partitioning.setScheme(Scheme.FIXED);
          partitioning.setFixed(
              List.of(
                  createPartitionCfg(1, 1, 2),
                  createPartitionCfg(2, 0, 2),
                  createPartitionCfg(3, 0, 1)));
        });
  }

  private FixedPartitionCfg createPartitionCfg(final int partitionId, final int... nodeIds) {
    final var config = new FixedPartitionCfg();
    final var nodes = new ArrayList<NodeCfg>();
    config.setPartitionId(partitionId);
    config.setNodes(nodes);

    for (final var nodeId : nodeIds) {
      final var node = new NodeCfg();
      node.setNodeId(nodeId);
      nodes.add(node);
    }

    return config;
  }
}
