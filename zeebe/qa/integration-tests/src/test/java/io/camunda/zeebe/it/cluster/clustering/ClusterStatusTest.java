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
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.StatusResponse.Status;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.asserts.TopologyAssert;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration test to verify cluster status reporting.
 *
 * <p>Tests that the cluster status correctly reports healthy when at least one partition has a
 * healthy leader, and unhealthy when no partitions have healthy leaders.
 */
@ZeebeIntegration
public class ClusterStatusTest {

  @TestZeebe(autoStart = false, purgeAfterEach = false)
  private static final TestCluster CLUSTER =
      TestCluster.builder()
          .withEmbeddedGateway(false)
          .withGatewaysCount(1)
          .withGatewayConfig(gateway -> gateway.withCreateSchema(false).withUnauthenticatedAccess())
          .withBrokersCount(3)
          .withBrokerConfig(node -> node.withCreateSchema(false))
          .withPartitionsCount(3)
          .withReplicationFactor(1)
          .build();

  @BeforeEach
  void beforeEach() {
    CLUSTER.start().awaitCompleteTopology();
  }

  @Test
  public void shouldReportHealthyStatusWhenClusterIsUp() {
    // given - cluster is running with leaders

    // when - requesting status
    try (final var client = createCamundaClient()) {
      final var status = client.newStatusRequest().send().join();

      // then - status should be healthy
      assertThat(status.getStatus()).isEqualTo(Status.UP);
    }
  }

  @Test
  public void shouldReportUnhealthyStatusWhenNoLeadersExist() {
    // given - cluster is initially healthy
    try (final var client = createCamundaClient()) {
      final var initialResponse = client.newStatusRequest().send().join();
      assertThat(initialResponse.getStatus()).isEqualTo(Status.UP);

      // when - stopping all brokers to force no leaders for any partition
      CLUSTER.brokers().values().forEach(TestStandaloneBroker::stop);

      // then - status should eventually become unhealthy
      Awaitility.await("Status should become unhealthy when no leaders exist")
          .atMost(Duration.ofSeconds(30))
          .pollInterval(Duration.ofSeconds(1))
          .untilAsserted(
              () -> {
                final var status = client.newStatusRequest().send().join();
                assertThat(status.getStatus()).isEqualTo(Status.DOWN);
              });
    }
  }

  @Test
  public void shouldReportHealthyStatusWhenOnlyOnePartitionHasHealthyLeader() {
    // given - cluster is initially healthy
    try (final var client = createCamundaClient()) {
      final var initialResponse = client.newStatusRequest().send().join();
      assertThat(initialResponse.getStatus()).isEqualTo(Status.UP);

      // when - stopping 2 out of 3 brokers, leaving at least one partition with a leader
      stopBrokers(0, 1);
      waitForNoLeaderForPartitions(client, 0, 1);

      // then - status should remain healthy as long as one partition has a healthy leader
      Awaitility.await("Status should remain healthy with partial cluster")
          .pollInterval(Duration.ofMillis(100))
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(
              () -> {
                final var status = client.newStatusRequest().send().join();
                assertThat(status.getStatus()).isEqualTo(Status.UP);
              });
    }
  }

  private void stopBrokers(final int... brokers) {
    for (final int broker : brokers) {
      CLUSTER.brokers().get(MemberId.from(String.valueOf(broker))).stop();
    }
  }

  private void waitForNoLeaderForPartitions(final CamundaClient client, final int... partitions) {
    Awaitility.await()
        .timeout(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              for (final var partition : partitions) {
                TopologyAssert.assertThat(client.newTopologyRequest().send().join())
                    .doesNotContainLeaderForPartition(partition);
              }
            });
  }

  private static CamundaClient createCamundaClient() {
    final var gateway = CLUSTER.anyGateway();
    return CLUSTER
        .newClientBuilder()
        .restAddress(gateway.restAddress())
        .defaultRequestTimeout(Duration.ofSeconds(15))
        .build();
  }
}
