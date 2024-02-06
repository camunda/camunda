/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.rest;

import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.BrokerClusterState;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.broker.client.impl.BrokerClusterStateImpl;
import io.camunda.zeebe.gateway.protocol.rest.BrokerInfo;
import io.camunda.zeebe.gateway.protocol.rest.Partition;
import io.camunda.zeebe.gateway.protocol.rest.Partition.HealthEnum;
import io.camunda.zeebe.gateway.protocol.rest.Partition.RoleEnum;
import io.camunda.zeebe.gateway.protocol.rest.TopologyResponse;
import io.camunda.zeebe.gateway.rest.util.TestApplication;
import io.camunda.zeebe.protocol.record.PartitionHealthStatus;
import io.camunda.zeebe.util.VersionUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
    classes = {TestApplication.class, TopologyController.class},
    webEnvironment = WebEnvironment.RANDOM_PORT)
public class TopologyControllerTest {

  @MockBean BrokerClient brokerClient;
  @MockBean BrokerTopologyManager topologyManager;

  @Autowired private WebTestClient webClient;

  @BeforeEach
  void setUp() {
    Mockito.when(brokerClient.getTopologyManager()).thenReturn(topologyManager);
  }

  @Test
  public void shouldGetTopology() {
    // given
    final String gatewayVersion = VersionUtil.getVersion();
    final TopologyResponse topologyResponse = getTopologyResponse(gatewayVersion);
    final BrokerClusterState brokerClusterState = getBrokerClusterState(gatewayVersion);
    Mockito.when(brokerClient.getTopologyManager().getTopology()).thenReturn(brokerClusterState);

    // when / then
    webClient
        .get()
        .uri("api/v1/topology")
        .header(HttpHeaders.ACCEPT, "application/json")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(TopologyResponse.class)
        .isEqualTo(topologyResponse);
  }

  private TopologyResponse getTopologyResponse(final String gatewayVersion) {
    final Partition partition = new Partition();
    partition.setPartitionId(1);
    partition.setRole(RoleEnum.LEADER);
    partition.setHealth(HealthEnum.HEALTHY);

    final BrokerInfo brokerInfo = new BrokerInfo();
    brokerInfo.setNodeId(0);
    brokerInfo.setHost("localhost");
    brokerInfo.setPort(26501);
    brokerInfo.addPartitionsItem(partition);
    brokerInfo.setVersion(gatewayVersion);

    final TopologyResponse topologyResponse = new TopologyResponse();
    topologyResponse.setGatewayVersion(gatewayVersion);
    topologyResponse.setClusterSize(1);
    topologyResponse.setPartitionsCount(1);
    topologyResponse.setReplicationFactor(3);
    topologyResponse.addBrokersItem(brokerInfo);

    return topologyResponse;
  }

  private BrokerClusterState getBrokerClusterState(final String gatewayVersion) {
    final BrokerClusterStateImpl brokerClusterState = new BrokerClusterStateImpl();
    brokerClusterState.setClusterSize(1);
    brokerClusterState.setPartitionsCount(1);
    brokerClusterState.setReplicationFactor(3);

    brokerClusterState.addBrokerIfAbsent(0);
    brokerClusterState.setBrokerAddressIfPresent(0, "localhost:26501");
    brokerClusterState.setBrokerVersionIfPresent(0, gatewayVersion);
    brokerClusterState.setPartitionLeader(1, 0, 1);
    brokerClusterState.setPartitionHealthStatus(0, 1, PartitionHealthStatus.HEALTHY);
    brokerClusterState.addPartitionIfAbsent(1);

    return brokerClusterState;
  }
}
