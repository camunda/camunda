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
import io.camunda.configuration.FixedPartition;
import io.camunda.configuration.Node;
import io.camunda.configuration.Partitioning;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
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
    broker.withUnifiedConfig(
        cfg -> {
          cfg.getCluster().getRaft().setPriorityElectionEnabled(false);
          cfg.getCluster()
              .setPartitioning(
                  new Partitioning(
                      Partitioning.Scheme.FIXED,
                      List.of(
                          new FixedPartition(1, List.of(new Node(1), new Node(2))),
                          new FixedPartition(2, List.of(new Node(0), new Node(2))),
                          new FixedPartition(3, List.of(new Node(0), new Node(1))))));
        });
  }
}
