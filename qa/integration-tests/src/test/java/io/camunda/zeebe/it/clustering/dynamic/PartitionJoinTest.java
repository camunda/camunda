/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.clustering.dynamic;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.management.cluster.PostOperationResponse;
import io.camunda.zeebe.management.cluster.TopologyChange.StatusEnum;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import java.time.OffsetDateTime;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.shaded.org.awaitility.Awaitility;

final class PartitionJoinTest {
  @ParameterizedTest
  @MethodSource("testScenarios")
  void canJoinPartition(final Scenario scenario) {
    // given
    try (final var cluster = setupCluster(scenario.initialClusterState())) {
      // when
      final var response = runOperation(cluster, scenario.operation());
      // then
      assertChangeIsPlanned(response);
      Awaitility.await("Requested change is completed in time")
          .untilAsserted(() -> assertChangeIsCompleted(cluster, response));
      assertChangeIsApplied(cluster, response);
    }
  }

  private void assertChangeIsPlanned(final PostOperationResponse response) {
    assertThat(response.getPlannedChanges()).isNotEmpty();
    assertThat(response.getExpectedTopology())
        .usingRecursiveComparison()
        .ignoringFieldsOfTypes(OffsetDateTime.class)
        .isNotEqualTo(response.getCurrentTopology());
  }

  private void assertChangeIsApplied(
      final TestCluster cluster, final PostOperationResponse response) {
    final var actuator = ClusterActuator.of(cluster.availableGateway());
    final var expectedTopology = response.getExpectedTopology();
    final var currentTopology = actuator.getTopology().getBrokers();
    assertThat(currentTopology)
        .usingRecursiveComparison()
        .ignoringFields("lastUpdatedAt")
        .isEqualTo(expectedTopology);
  }

  private void assertChangeIsCompleted(
      final TestCluster cluster, final PostOperationResponse response) {
    final var actuator = ClusterActuator.of(cluster.availableGateway());
    final var currentChange = actuator.getTopology().getChange();
    assertThat(currentChange).isNotNull();
    assertThat(currentChange.getId()).isEqualTo(response.getChangeId());
    assertThat(currentChange.getStatus()).isEqualTo(StatusEnum.COMPLETED);
  }

  static Stream<Scenario> testScenarios() {
    return Stream.of(
        new Scenario(new InitialClusterState(3, 3, 1), new Operation(1, 1, 2)),
        new Scenario(new InitialClusterState(2, 2, 1), new Operation(1, 1, 2)));
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

  PostOperationResponse runOperation(final TestCluster cluster, final Operation operation) {
    final var actuator = ClusterActuator.of(cluster.availableGateway());
    return actuator.joinPartition(
        operation.brokerId(), operation.partitionId(), operation.priority());
  }

  record Scenario(InitialClusterState initialClusterState, Operation operation) {}

  record InitialClusterState(int clusterSize, int partitionCount, int replicationFactor) {}

  record Operation(int brokerId, int partitionId, int priority) {}
}
