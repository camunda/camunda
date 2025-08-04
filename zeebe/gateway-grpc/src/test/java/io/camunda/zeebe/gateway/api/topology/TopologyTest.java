/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.api.topology;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.gateway.api.util.GatewayTest;
import io.camunda.zeebe.gateway.api.util.StubbedTopologyManager;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.Partition;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.Partition.PartitionBrokerHealth;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.Partition.PartitionBrokerRole;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.TopologyRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.TopologyResponse;
import io.camunda.zeebe.protocol.record.PartitionHealthStatus;
import java.util.Optional;
import org.junit.Test;

public final class TopologyTest extends GatewayTest {

  @Test
  public void shouldResponseWithInitialUnhealthyPartitions() {
    // when
    final var response = client.topology(TopologyRequest.newBuilder().build());

    // then
    assertThat(response.getBrokersList())
        .isNotEmpty()
        .allSatisfy(
            brokerInfo ->
                assertThat(brokerInfo.getPartitionsList())
                    .isNotEmpty()
                    .allSatisfy(
                        partitionPerBroker ->
                            assertThat(partitionPerBroker.getHealth())
                                .isEqualTo(PartitionBrokerHealth.UNHEALTHY)));
  }

  @Test
  public void shouldUpdatePartitionHealthHealthy() {
    // given
    final var topologyManager = (StubbedTopologyManager) brokerClient.getTopologyManager();
    topologyManager.setPartitionHealthStatus(0, 1, PartitionHealthStatus.HEALTHY);

    // when
    final var response = client.topology(TopologyRequest.newBuilder().build());

    // then
    final var health = response.getBrokers(0).getPartitions(0).getHealth();
    assertThat(health).isEqualTo(PartitionBrokerHealth.HEALTHY);
  }

  @Test
  public void shouldUpdatePartitionHealth() {
    // given
    final var topologyManager = (StubbedTopologyManager) brokerClient.getTopologyManager();
    topologyManager.setPartitionHealthStatus(0, 1, PartitionHealthStatus.UNHEALTHY);

    // when
    final var response = client.topology(TopologyRequest.newBuilder().build());

    // then
    final var health = response.getBrokers(0).getPartitions(0).getHealth();
    assertThat(health).isEqualTo(PartitionBrokerHealth.UNHEALTHY);
  }

  @Test
  public void shouldUpdateMultiplePartitionHealths() {
    // given
    final var topologyManager = (StubbedTopologyManager) brokerClient.getTopologyManager();
    topologyManager.setPartitionHealthStatus(0, 1, PartitionHealthStatus.UNHEALTHY);
    topologyManager.setPartitionHealthStatus(0, 6, PartitionHealthStatus.HEALTHY);

    // when
    final var response = client.topology(TopologyRequest.newBuilder().build());

    // then
    final var brokerInfo = response.getBrokers(0);
    assertThat(brokerInfo.getPartitions(0).getHealth()).isEqualTo(PartitionBrokerHealth.UNHEALTHY);
    assertThat(brokerInfo.getPartitions(5).getHealth()).isEqualTo(PartitionBrokerHealth.HEALTHY);
  }

  @Test
  public void shouldUpdateInactiveBroker() {
    // given
    final int partitionId = 3;

    final var topologyManager = (StubbedTopologyManager) brokerClient.getTopologyManager();
    topologyManager.addPartitionInactive(partitionId, 0);

    // when
    final TopologyResponse response = client.topology(TopologyRequest.newBuilder().build());

    // then
    assertThat(response.getBrokersList()).isNotEmpty();
    assertThat(response.getBrokers(0).getPartitionsList()).isNotEmpty();
    final Optional<Partition> partition =
        response.getBrokers(0).getPartitionsList().stream()
            .filter(p -> p.getPartitionId() == partitionId)
            .findFirst();
    assertThat(partition).isPresent();
    assertThat(partition.get().getRole()).isEqualTo(PartitionBrokerRole.INACTIVE);
  }
}
