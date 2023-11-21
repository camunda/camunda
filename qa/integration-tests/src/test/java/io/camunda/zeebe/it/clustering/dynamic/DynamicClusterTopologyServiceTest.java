/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.clustering.dynamic;

import static io.camunda.zeebe.test.util.asserts.TopologyAssert.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
final class DynamicClusterTopologyServiceTest {
  private static final int PARTITIONS_COUNT = 3;

  @TestZeebe
  private final TestCluster cluster =
      TestCluster.builder()
          .withBrokersCount(3)
          .withPartitionsCount(PARTITIONS_COUNT)
          .withReplicationFactor(1)
          .withBrokerConfig(this::configureDynamicClusterTopology)
          .build();

  @Test
  void shouldStartClusterWithDynamicTopology() {
    try (final var client = cluster.newClientBuilder().build()) {
      final var topology = client.newTopologyRequest().send().join();
      assertThat(topology)
          .describedAs(
              "Expected topology to have %d partitions distributed over 3 brokers",
              PARTITIONS_COUNT)
          .hasLeaderForPartition(1, 0)
          .hasLeaderForPartition(2, 1)
          .hasLeaderForPartition(3, 2);
    }
  }

  private void configureDynamicClusterTopology(
      final MemberId memberId, final TestStandaloneBroker broker) {
    broker.withBrokerConfig(
        b -> {
          b.getExperimental().getFeatures().setEnableDynamicClusterTopology(true);
          if (!memberId.id().equals("0")) {
            // not coordinator. Give wrong configuration to verify that it is overwritten by dynamic
            // cluster topology. Note that this would not work in production because the engine
            // still reads the partition count from the static configuration, so message correlation
            // would not work.
            b.getCluster().setPartitionsCount(1);
          }
        });
  }
}
