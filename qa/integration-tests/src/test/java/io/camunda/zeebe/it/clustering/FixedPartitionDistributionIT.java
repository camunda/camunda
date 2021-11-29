/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.clustering;

import io.camunda.zeebe.test.util.actuator.PartitionsActuatorClient;
import io.camunda.zeebe.test.util.actuator.PartitionsActuatorClient.PartitionStatus;
import io.camunda.zeebe.test.util.asserts.EitherAssert;
import io.camunda.zeebe.test.util.testcontainers.ContainerLogsDumper;
import io.camunda.zeebe.test.util.testcontainers.ZeebeTestContainerDefaults;
import io.camunda.zeebe.util.Either;
import io.zeebe.containers.ZeebeBrokerNode;
import io.zeebe.containers.cluster.ZeebeCluster;
import java.util.List;
import org.agrona.CloseHelper;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Network;

final class FixedPartitionDistributionIT {
  private static final Logger LOGGER = LoggerFactory.getLogger(FixedPartitionDistributionIT.class);

  private Network network;
  private ZeebeCluster cluster;

  @SuppressWarnings("unused")
  @RegisterExtension
  final ContainerLogsDumper logsWatcher =
      new ContainerLogsDumper(() -> cluster.getBrokers(), LOGGER);

  @BeforeEach
  void beforeEach() {
    network = Network.newNetwork();
  }

  @AfterEach
  void afterEach() {
    CloseHelper.quietCloseAll(cluster, network);
  }

  @Test
  void shouldDistributePartitions() {
    // given
    cluster =
        ZeebeCluster.builder()
            .withBrokersCount(3)
            .withEmbeddedGateway(true)
            .withPartitionsCount(3)
            .withReplicationFactor(2)
            .build();
    cluster.getBrokers().forEach((nodeId, broker) -> configureBroker(broker));
    cluster.start();

    // when - then
    final var brokerZeroPartitions = List.of("2", "3");
    final var brokerOnePartitions = List.of("1", "3");
    final var brokerTwoPartitions = List.of("1", "2");

    assertPartitionsDistributedPerBroker(brokerZeroPartitions, 0);
    assertPartitionsDistributedPerBroker(brokerOnePartitions, 1);
    assertPartitionsDistributedPerBroker(brokerTwoPartitions, 2);
  }

  private void assertPartitionsDistributedPerBroker(
      final List<String> expectedPartitionIds, final Integer nodeId) {
    final var broker = cluster.getBrokers().get(nodeId);
    final var client = new PartitionsActuatorClient(broker.getExternalMonitoringAddress());
    final var partitionsResult = client.queryPartitions();

    EitherAssert.assertThat(partitionsResult)
        .as("partitions status request should have succeeded")
        .isRight()
        .extracting(Either::get)
        .asInstanceOf(InstanceOfAssertFactories.map(String.class, PartitionStatus.class))
        .as("broker %d should report entries for the expected partitions", nodeId)
        .containsOnlyKeys(expectedPartitionIds);
  }

  private void configureBroker(final ZeebeBrokerNode<?> broker) {
    broker.setDockerImageName(
        ZeebeTestContainerDefaults.defaultTestImage().asCanonicalNameString());
    broker
        .withEnv("ZEEBE_BROKER_EXPERIMENTAL_PARTITIONING_SCHEME", "FIXED")
        .withEnv("ZEEBE_BROKER_EXPERIMENTAL_ENABLE_PRIORITY_ELECTION", "false")
        .withEnv("ZEEBE_BROKER_EXPERIMENTAL_PARTITIONING_FIXED_0_PARTITIONID", "1")
        .withEnv("ZEEBE_BROKER_EXPERIMENTAL_PARTITIONING_FIXED_0_NODES_0_NODEID", "1")
        .withEnv("ZEEBE_BROKER_EXPERIMENTAL_PARTITIONING_FIXED_0_NODES_1_NODEID", "2")
        .withEnv("ZEEBE_BROKER_EXPERIMENTAL_PARTITIONING_FIXED_1_PARTITIONID", "2")
        .withEnv("ZEEBE_BROKER_EXPERIMENTAL_PARTITIONING_FIXED_1_NODES_0_NODEID", "0")
        .withEnv("ZEEBE_BROKER_EXPERIMENTAL_PARTITIONING_FIXED_1_NODES_1_NODEID", "2")
        .withEnv("ZEEBE_BROKER_EXPERIMENTAL_PARTITIONING_FIXED_2_PARTITIONID", "3")
        .withEnv("ZEEBE_BROKER_EXPERIMENTAL_PARTITIONING_FIXED_2_NODES_0_NODEID", "0")
        .withEnv("ZEEBE_BROKER_EXPERIMENTAL_PARTITIONING_FIXED_2_NODES_1_NODEID", "1");
  }
}
