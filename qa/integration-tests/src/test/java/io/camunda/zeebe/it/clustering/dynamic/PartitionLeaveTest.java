/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.clustering.dynamic;

import static io.camunda.zeebe.it.clustering.dynamic.Utils.assertBrokerDoesNotHavePartition;
import static io.camunda.zeebe.it.clustering.dynamic.Utils.assertChangeIsApplied;
import static io.camunda.zeebe.it.clustering.dynamic.Utils.assertChangeIsCompleted;
import static io.camunda.zeebe.it.clustering.dynamic.Utils.assertChangeIsPlanned;

import io.camunda.zeebe.management.cluster.PostOperationResponse;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.test.util.asserts.TopologyAssert;
import java.time.Duration;
import java.util.stream.Stream;
import org.awaitility.Awaitility;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

final class PartitionLeaveTest {
  @ParameterizedTest
  @MethodSource("testScenarios")
  void canLeavePartition(final Scenario scenario) {
    // given
    try (final var cluster = setupCluster(scenario.initialClusterState())) {
      // when
      final var response = runOperation(cluster, scenario.operation());
      // then
      assertChangeIsPlanned(response);
      Awaitility.await("Requested change is completed in time")
          .untilAsserted(() -> assertChangeIsCompleted(cluster, response));
      assertChangeIsApplied(cluster, response);
      assertBrokerDoesNotHavePartition(
          cluster, scenario.operation().brokerId(), scenario.operation().partitionId());
    }
  }

  @ParameterizedTest
  @MethodSource("testScenarios")
  void canJoinPartitionAfterLeaving(final Scenario scenario) throws InterruptedException {
    // given
    try (final var cluster = setupCluster(scenario.initialClusterState())) {
      final var leave = runOperation(cluster, scenario.operation());
      assertChangeIsPlanned(leave);
      Awaitility.await("Leaving is completed in time")
          .untilAsserted(() -> assertChangeIsCompleted(cluster, leave));
      assertChangeIsApplied(cluster, leave);
      try (final var client = cluster.newClientBuilder().build()) {
        Awaitility.await("All partition have a leader")
            .pollDelay(Duration.ofSeconds(10))
            .timeout(Duration.ofMinutes(1))
            .untilAsserted(
                () ->
                    TopologyAssert.assertThat(client.newTopologyRequest().send().join())
                        .hasLeaderForEachPartition(
                            scenario.initialClusterState().partitionCount()));
      }
      // when
      final var join = revertOperation(cluster, scenario.operation());
      // then
      assertChangeIsPlanned(join);
      Awaitility.await("Rejoining is completed in time")
          .untilAsserted(() -> assertChangeIsCompleted(cluster, join));
      assertChangeIsApplied(cluster, join);
    }
  }

  PostOperationResponse runOperation(final TestCluster cluster, final Operation operation) {
    final var actuator = ClusterActuator.of(cluster.availableGateway());
    return actuator.leavePartition(operation.brokerId(), operation.partitionId());
  }

  private PostOperationResponse revertOperation(
      final TestCluster cluster, final Operation operation) {
    final var actuator = ClusterActuator.of(cluster.availableGateway());
    return actuator.joinPartition(operation.brokerId(), operation.partitionId(), 1);
  }

  static Stream<Scenario> testScenarios() {
    return Stream.of(
        new Scenario(new InitialClusterState(2, 2, 2), new Operation(1, 1)),
        new Scenario(new InitialClusterState(3, 3, 3), new Operation(0, 1)),
        new Scenario(new InitialClusterState(3, 3, 3), new Operation(0, 3)));
  }

  TestCluster setupCluster(final InitialClusterState conf) {
    return TestCluster.builder()
        .withBrokerConfig(
            broker ->
                broker
                    .brokerConfig()
                    .getExperimental()
                    .getFeatures()
                    .setEnableDynamicClusterTopology(true))
        .withBrokersCount(conf.clusterSize())
        .withReplicationFactor(conf.replicationFactor())
        .withPartitionsCount(conf.partitionCount())
        .withGatewaysCount(1)
        .build()
        .start()
        .awaitCompleteTopology();
  }

  record Scenario(InitialClusterState initialClusterState, Operation operation) {}

  record InitialClusterState(int clusterSize, int partitionCount, int replicationFactor) {}

  record Operation(int brokerId, int partitionId) {}
}
