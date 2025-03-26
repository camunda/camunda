/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.clustering.topology;

import static io.camunda.zeebe.protocol.Protocol.START_PARTITION_ID;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.BrokerInfo;
import io.camunda.client.api.response.PartitionBrokerRole;
import io.camunda.client.api.response.PartitionInfo;
import io.camunda.client.api.response.Topology;
import io.camunda.zeebe.broker.Broker;
import io.camunda.zeebe.gateway.Gateway;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ZeebeIntegration
public final class TopologyClusterTest {

  @TestZeebe(autoStart = false)
  private static final TestCluster CLUSTER =
      TestCluster.builder()
          .withEmbeddedGateway(false)
          .withGatewaysCount(1)
          .withGatewayConfig(gateway -> gateway.withCreateSchema(false))
          .withBrokersCount(3)
          .withBrokerConfig(node -> node.withCreateSchema(false))
          .withPartitionsCount(3)
          .withReplicationFactor(3)
          .build();

  @BeforeAll
  static void init() {
    CLUSTER.start().awaitCompleteTopology();
  }

  @ParameterizedTest
  @MethodSource("communicationApi")
  public void shouldContainAllBrokers(final boolean useRest) throws URISyntaxException {
    try (final var client = createCamundaClient()) {
      // when
      final Topology topology = sendRequest(client, useRest);

      // then
      final List<BrokerInfo> brokers = topology.getBrokers();

      assertThat(brokers.size()).isEqualTo(3);
      assertThat(brokers).extracting(BrokerInfo::getNodeId).containsExactlyInAnyOrder(0, 1, 2);
    }
  }

  @ParameterizedTest
  @MethodSource("communicationApi")
  public void shouldContainAllPartitions(final boolean useRest) throws URISyntaxException {
    try (final var client = createCamundaClient()) {
      // when
      final Topology topology = sendRequest(client, useRest);

      // then
      final List<BrokerInfo> brokers = topology.getBrokers();

      assertThat(brokers)
          .flatExtracting(BrokerInfo::getPartitions)
          .filteredOn(PartitionInfo::isLeader)
          .extracting(PartitionInfo::getPartitionId)
          .containsExactlyInAnyOrder(
              START_PARTITION_ID, START_PARTITION_ID + 1, START_PARTITION_ID + 2);

      assertPartitionInTopology(brokers, START_PARTITION_ID);
      assertPartitionInTopology(brokers, START_PARTITION_ID + 1);
      assertPartitionInTopology(brokers, START_PARTITION_ID + 2);
    }
  }

  @ParameterizedTest
  @MethodSource("communicationApi")
  public void shouldExposeClusterSettings(final boolean useRest) throws URISyntaxException {
    try (final var client = createCamundaClient()) {
      // when
      final Topology topology = sendRequest(client, useRest);

      // then
      assertThat(topology.getClusterSize()).isEqualTo(CLUSTER.brokers().size());
      assertThat(topology.getPartitionsCount()).isEqualTo(CLUSTER.partitionsCount());
      assertThat(topology.getReplicationFactor()).isEqualTo(CLUSTER.replicationFactor());
      // NOTE: this fails in Intellij because we don't have access to the package version but it
      // works
      // when run from the CLI
      assertThat(topology.getGatewayVersion())
          .isEqualTo(Gateway.class.getPackage().getImplementationVersion());

      for (final BrokerInfo broker : topology.getBrokers()) {
        assertThat(broker.getVersion())
            .isEqualTo(Broker.class.getPackage().getImplementationVersion());
      }
    }
  }

  private void assertPartitionInTopology(final List<BrokerInfo> brokers, final int partition) {
    assertThat(brokers)
        .flatExtracting(BrokerInfo::getPartitions)
        .filteredOn(p -> p.getPartitionId() == partition)
        .extracting(PartitionInfo::getRole)
        .containsExactlyInAnyOrder(
            PartitionBrokerRole.LEADER, PartitionBrokerRole.FOLLOWER, PartitionBrokerRole.FOLLOWER);
  }

  private static CamundaClient createCamundaClient() {
    final var gateway = CLUSTER.anyGateway();
    return CLUSTER
        .newClientBuilder()
        .restAddress(gateway.restAddress())
        .defaultRequestTimeout(Duration.ofSeconds(15))
        .build();
  }

  private Topology sendRequest(final CamundaClient client, final boolean useRest) {
    var request = client.newTopologyRequest();
    if (useRest) {
      request = request.useRest();
    } else {
      request = request.useGrpc();
    }
    return request.send().join();
  }

  private static Stream<Arguments> communicationApi() {
    return Stream.of(Arguments.of(true), Arguments.of(false));
  }
}
