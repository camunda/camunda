/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.management;

import io.camunda.zeebe.management.cluster.Operation;
import io.camunda.zeebe.management.cluster.Operation.OperationEnum;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@ZeebeIntegration
@Execution(ExecutionMode.CONCURRENT)
final class ClusterEndpointIT {

  @Test
  void shouldRequestPartitionLeave() {
    final ClusterActuator actuator;
    try (final var cluster =
        TestCluster.builder()
            .withEmbeddedGateway(true)
            .withBrokersCount(2)
            .withPartitionsCount(2)
            .withReplicationFactor(2)
            .withBrokerConfig(
                broker ->
                    broker
                        .brokerConfig()
                        .getExperimental()
                        .getFeatures()
                        .setEnableDynamicClusterTopology(true))
            .build()
            .start()) {
      // given - a process instance
      cluster.awaitCompleteTopology();

      actuator = ClusterActuator.of(cluster.availableGateway());
      // when -- request a leave
      final var response = actuator.leavePartition(1, 2);
      // then
      Assertions.assertThat(response.getPlannedChanges())
          .singleElement()
          .asInstanceOf(InstanceOfAssertFactories.type(Operation.class))
          .returns(OperationEnum.PARTITION_LEAVE, Operation::getOperation)
          .returns(1, Operation::getBrokerId)
          .returns(2, Operation::getPartitionId);
    }
  }

  @Test
  void shouldRequestPartitionJoin() {
    try (final var cluster =
        TestCluster.builder()
            .withEmbeddedGateway(true)
            .withBrokersCount(2)
            .withPartitionsCount(2)
            .withReplicationFactor(1)
            .withBrokerConfig(
                broker ->
                    broker
                        .brokerConfig()
                        .getExperimental()
                        .getFeatures()
                        .setEnableDynamicClusterTopology(true))
            .build()
            .start()) {
      // given - a process instance
      cluster.awaitCompleteTopology();
      final var actuator = ClusterActuator.of(cluster.availableGateway());
      // when -- request a leave
      final var response = actuator.joinPartition(0, 2, 3);
      // then
      Assertions.assertThat(response.getPlannedChanges())
          .singleElement()
          .asInstanceOf(InstanceOfAssertFactories.type(Operation.class))
          .returns(OperationEnum.PARTITION_JOIN, Operation::getOperation)
          .returns(0, Operation::getBrokerId)
          .returns(2, Operation::getPartitionId)
          .returns(3, Operation::getPriority);
    }
  }
}
