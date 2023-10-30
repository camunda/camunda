/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.management;

import static org.assertj.core.api.Assertions.*;

import io.camunda.zeebe.management.cluster.Operation;
import io.camunda.zeebe.management.cluster.Operation.OperationEnum;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@ZeebeIntegration
@Execution(ExecutionMode.CONCURRENT)
final class ClusterEndpointIT {
  private static final int BROKER_COUNT = 2;
  private static final int PARTITION_COUNT = 2;

  @Test
  void shouldQueryCurrentClusterTopology() {
    final ClusterActuator actuator;
    try (final var cluster = createCluster(2)) {
      // given
      cluster.awaitCompleteTopology();
      actuator = ClusterActuator.of(cluster.availableGateway());

      // when
      final var response = actuator.getTopology();

      // then - topology is as expected
      assertThat(response.getBrokers())
          .hasSize(BROKER_COUNT)
          .allSatisfy(
              b ->
                  assertThat(b.getPartitions())
                      .describedAs("All brokers have two partitions each")
                      .hasSize(PARTITION_COUNT));
      assertThat(response.getChange().getId())
          .describedAs("Initial topology has no completed or pending changes")
          .isNull();
    }
  }

  @Test
  void shouldRequestPartitionLeave() {
    final ClusterActuator actuator;
    try (final var cluster = createCluster(2)) {
      // given
      cluster.awaitCompleteTopology();

      actuator = ClusterActuator.of(cluster.availableGateway());
      // when -- request a leave
      final var response = actuator.leavePartition(1, 2);
      // then
      assertThat(response.getPlannedChanges())
          .singleElement()
          .asInstanceOf(InstanceOfAssertFactories.type(Operation.class))
          .returns(OperationEnum.PARTITION_LEAVE, Operation::getOperation)
          .returns(1, Operation::getBrokerId)
          .returns(2, Operation::getPartitionId);
    }
  }

  @Test
  void shouldRequestPartitionJoin() {
    try (final var cluster = createCluster(1)) {
      // given
      cluster.awaitCompleteTopology();
      final var actuator = ClusterActuator.of(cluster.availableGateway());
      // when -- request a join
      final var response = actuator.joinPartition(0, 2, 3);
      // then
      assertThat(response.getPlannedChanges())
          .singleElement()
          .asInstanceOf(InstanceOfAssertFactories.type(Operation.class))
          .returns(OperationEnum.PARTITION_JOIN, Operation::getOperation)
          .returns(0, Operation::getBrokerId)
          .returns(2, Operation::getPartitionId)
          .returns(3, Operation::getPriority);
    }
  }

  private static TestCluster createCluster(final int replicationFactor) {
    return TestCluster.builder()
        .withEmbeddedGateway(true)
        .withBrokersCount(BROKER_COUNT)
        .withPartitionsCount(PARTITION_COUNT)
        .withReplicationFactor(replicationFactor)
        .withBrokerConfig(
            broker ->
                broker
                    .brokerConfig()
                    .getExperimental()
                    .getFeatures()
                    .setEnableDynamicClusterTopology(true))
        .build()
        .start();
  }
}
