/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.container.cluster;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.Topology;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.zeebe.test.util.asserts.TopologyAssert;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Network;

final class CamundaClusterTest {
  @AutoClose private final Network network = Network.newNetwork();

  @AutoClose private CamundaCluster cluster;

  @Test
  void shouldStartSingleNodeCluster() {
    // given
    cluster =
        CamundaCluster.builder()
            .withEmbeddedGateway(true)
            .withReplicationFactor(1)
            .withPartitionsCount(1)
            .withBrokersCount(1)
            .withNetwork(network)
            .withBrokerConfig(
                cfg ->
                    cfg.getConfiguration()
                        .getData()
                        .getSecondaryStorage()
                        .setType(SecondaryStorageType.none))
            .build();

    // when
    cluster.start();

    // then
    final Topology topology;
    try (final CamundaClient client = cluster.newClientBuilder().build()) {
      topology = client.newTopologyRequest().send().join();
    }

    assertThat(topology.getPartitionsCount())
        .as("there is exactly one partition as configured")
        .isOne();
    assertThat(topology.getReplicationFactor())
        .as("there is a replication factor of 1 as configured")
        .isOne();
    TopologyAssert.assertThat(topology)
        .as("the topology is complete for a one broker, one partition cluster")
        .hasBrokersCount(1)
        .isComplete(1, 1, 1);
  }

  @Test
  void shouldStartClusterWithEmbeddedGateways() {
    // given
    cluster =
        CamundaCluster.builder()
            .withEmbeddedGateway(true)
            .withReplicationFactor(2)
            .withPartitionsCount(2)
            .withBrokersCount(2)
            .withNetwork(network)
            .withBrokerConfig(
                cfg -> {
                  cfg.getConfiguration()
                      .getData()
                      .getSecondaryStorage()
                      .setType(SecondaryStorageType.none);
                })
            .build();

    // when
    cluster.start();

    // then
    for (final GatewayNode<?> gateway : cluster.getGateways().values()) {
      final Topology topology;
      try (final CamundaClient client =
          cluster
              .newClientBuilder()
              .grpcAddress(gateway.getGrpcAddress())
              .restAddress(gateway.getRestAddress())
              .build()) {
        topology = client.newTopologyRequest().send().join();
      }

      assertThat(topology.getReplicationFactor())
          .as("there is replication factor of 2 as configured")
          .isEqualTo(2);
      assertThat(topology.getPartitionsCount())
          .as("there are exactly two partitions as configured")
          .isEqualTo(2);
      TopologyAssert.assertThat(topology)
          .as("the topology is complete with 2 partitions and 2 brokers")
          .hasBrokersCount(2)
          .isComplete(2, 2, 2);
    }
  }

  @Test
  void shouldStartClusterWithStandaloneGateway() {
    // given
    cluster =
        CamundaCluster.builder()
            .withEmbeddedGateway(false)
            .withReplicationFactor(1)
            .withPartitionsCount(1)
            .withBrokersCount(1)
            .withGatewaysCount(1)
            .withNetwork(network)
            .withBrokerConfig(
                cfg ->
                    cfg.getConfiguration()
                        .getData()
                        .getSecondaryStorage()
                        .setType(SecondaryStorageType.none))
            .withGatewayConfig(
                cfg ->
                    cfg.getConfiguration()
                        .getData()
                        .getSecondaryStorage()
                        .setType(SecondaryStorageType.none))
            .build();

    // when
    cluster.start();

    // then
    final Topology topology;
    try (final CamundaClient client = cluster.newClientBuilder().build()) {
      topology = client.newTopologyRequest().send().join();
    }

    assertThat(topology.getPartitionsCount())
        .as("there is exactly one partition as configured")
        .isOne();
    assertThat(topology.getReplicationFactor())
        .as("there is a replication factor of 1 as configured")
        .isOne();
    TopologyAssert.assertThat(topology)
        .as("the topology is complete for a one broker, one partition cluster")
        .hasBrokersCount(1)
        .isComplete(1, 1, 1);
  }

  @Test
  void shouldStartClusterWithMixedGateways() {
    // given
    cluster =
        CamundaCluster.builder()
            .withEmbeddedGateway(true)
            .withReplicationFactor(1)
            .withPartitionsCount(1)
            .withBrokersCount(1)
            .withGatewaysCount(1)
            .withNetwork(network)
            .withBrokerConfig(
                cfg ->
                    cfg.getConfiguration()
                        .getData()
                        .getSecondaryStorage()
                        .setType(SecondaryStorageType.none))
            .withGatewayConfig(
                cfg ->
                    cfg.getConfiguration()
                        .getData()
                        .getSecondaryStorage()
                        .setType(SecondaryStorageType.none))
            .build();

    // when
    cluster.start();

    // then
    for (final GatewayNode<?> gateway : cluster.getGateways().values()) {
      try (final CamundaClient client =
          CamundaClient.newClientBuilder()
              .grpcAddress(gateway.getGrpcAddress())
              .restAddress(gateway.getRestAddress())
              .build()) {
        final Topology topology = client.newTopologyRequest().send().join();
        assertThat(topology.getPartitionsCount())
            .as("there is exactly one partition as configured")
            .isOne();
        assertThat(topology.getReplicationFactor())
            .as("there is a replication factor of 1 as configured")
            .isOne();
        TopologyAssert.assertThat(topology)
            .as("the topology is complete for a one broker, one partition cluster")
            .isComplete(1, 1, 1)
            .hasBrokersCount(1);
      }
    }
  }
}
