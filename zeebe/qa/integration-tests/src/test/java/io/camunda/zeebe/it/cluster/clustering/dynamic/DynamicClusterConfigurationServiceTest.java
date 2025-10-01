/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.clustering.dynamic;

import static io.camunda.zeebe.test.util.asserts.TopologyAssert.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.client.CamundaClient;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
final class DynamicClusterConfigurationServiceTest {
  private static final int PARTITIONS_COUNT = 3;

  @TestZeebe
  private final TestCluster cluster =
      TestCluster.builder()
          .withGatewaysCount(1)
          .withGatewayConfig(
              g ->
                  g.gatewayConfig()
                      .getCluster()
                      .getMembership()
                      // Reduce timeouts so that the test is faster
                      .setProbeInterval(Duration.ofMillis(100))
                      .setFailureTimeout(Duration.ofSeconds(2)))
          .withEmbeddedGateway(false)
          .withBrokersCount(3)
          .withPartitionsCount(PARTITIONS_COUNT)
          .withReplicationFactor(1)
          .withBrokerConfig(this::configureDynamicClusterTopology)
          .build();

  private CamundaClient client;

  @BeforeEach
  void setup() {
    client = cluster.newClientBuilder().build();
  }

  @AfterEach
  void tearDown() {
    client.close();
  }

  @Test
  void shouldStartClusterWithDynamicTopology() {
    final var topology = client.newTopologyRequest().send().join();
    assertThat(topology)
        .describedAs(
            "Expected topology to have %d partitions distributed over 3 brokers", PARTITIONS_COUNT)
        .hasLeaderForPartition(1, 0)
        .hasLeaderForPartition(2, 1)
        .hasLeaderForPartition(3, 2);
  }

  @Test
  void shouldRestartOtherBrokerWhenBroker0IsUnavailable() {
    // given
    cluster.brokers().get(MemberId.from("0")).stop();

    // when
    cluster.brokers().get(MemberId.from("1")).stop();
    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              final var topology = client.newTopologyRequest().send().join();
              assertThat(topology)
                  .describedAs("Broker 1 is removed from topology", PARTITIONS_COUNT)
                  .doesNotContainBroker(1);
            });
    cluster.brokers().get(MemberId.from("1")).start();

    // then
    Awaitility.await()
        .untilAsserted(
            () -> {
              final var topology = client.newTopologyRequest().send().join();
              assertThat(topology)
                  .describedAs(
                      "Broker 1 has restarted and added back to the topology", PARTITIONS_COUNT)
                  .hasLeaderForPartition(2, 1)
                  .hasLeaderForPartition(3, 2);
            });
  }

  private void configureDynamicClusterTopology(
      final MemberId memberId, final TestStandaloneBroker broker) {
    broker.withBrokerConfig(
        b -> {
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
