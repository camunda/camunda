/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.BrokerClusterState;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.protocol.record.PartitionHealthStatus;
import io.camunda.zeebe.util.VersionUtil;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

@WebMvcTest(TopologyController.class)
public class TopologyControllerTest extends RestControllerTest {

  @MockitoBean BrokerClient brokerClient;
  @MockitoBean BrokerTopologyManager topologyManager;

  @BeforeEach
  void setUp() {
    Mockito.when(brokerClient.getTopologyManager()).thenReturn(topologyManager);
  }

  @ParameterizedTest
  @ValueSource(strings = {"/v1/topology", "/v2/topology"})
  public void shouldGetTopology(final String baseUrl) {
    // given
    final var version = VersionUtil.getVersion();
    final var clusterId = "cluster-id";
    final var expectedResponse =
        """
        {
          "clusterId": "cluster-id",
          "gatewayVersion": "%s",
          "clusterSize": 3,
          "partitionsCount": 1,
          "replicationFactor": 3,
          "lastCompletedChangeId": "1",
          "brokers": [
            {
              "nodeId": 0,
              "host": "localhost",
              "port": 26501,
              "version": "%s",
              "partitions": [
                {
                  "partitionId": 1,
                  "health": "healthy",
                  "role": "leader"
                }
              ]
            },
            {
              "nodeId": 1,
              "host": "localhost",
              "port": 26502,
              "version": "%s",
              "partitions": [
                {
                  "partitionId": 1,
                  "health": "healthy",
                  "role": "follower"
                }
              ]
            },
            {
              "nodeId": 2,
              "host": "localhost",
              "port": 26503,
              "version": "%s",
              "partitions": [
                {
                  "partitionId": 1,
                  "health": "unhealthy",
                  "role": "inactive"
                }
              ]
            }
          ]
        }
        """
            .formatted(version, version, version, version);
    final var brokerClusterState = new TestBrokerClusterState(version, clusterId);
    final ClusterConfiguration clusterConfiguration = Mockito.mock(ClusterConfiguration.class);
    Mockito.when(topologyManager.getClusterConfiguration()).thenReturn(clusterConfiguration);
    Mockito.when(topologyManager.getTopology()).thenReturn(brokerClusterState);

    // when / then
    webClient
        .get()
        .uri(baseUrl)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(expectedResponse, JsonCompareMode.STRICT);
  }

  @ParameterizedTest
  @ValueSource(strings = {"/v1/topology", "/v2/topology"})
  void shouldReturnEmptyTopology(final String baseUrl) {
    // given
    final var version = VersionUtil.getVersion();
    final var expectedResponse =
        """
        {
          "brokers":[],
          "gatewayVersion": "%s"
        }
        """
            .formatted(version);
    Mockito.when(topologyManager.getTopology()).thenReturn(null);

    // when / then
    webClient
        .get()
        .uri(baseUrl)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(expectedResponse, JsonCompareMode.STRICT);
  }

  /**
   * Topology stub which returns a static topology with 3 brokers, 1 partition, replication factor
   * 3, where 0 is the leader (healthy), 1 is the follower (healthy), and 2 is inactive (unhealthy).
   */
  private record TestBrokerClusterState(String version, String clusterId)
      implements BrokerClusterState {

    @Override
    public boolean isInitialized() {
      return true;
    }

    @Override
    public int getClusterSize() {
      return 3;
    }

    @Override
    public int getPartitionsCount() {
      return 1;
    }

    @Override
    public int getReplicationFactor() {
      return 3;
    }

    @Override
    public int getLeaderForPartition(final int partition) {
      return 0;
    }

    @Override
    public Set<Integer> getFollowersForPartition(final int partition) {
      return Set.of(1);
    }

    @Override
    public Set<Integer> getInactiveNodesForPartition(final int partition) {
      return Set.of(2);
    }

    @Override
    public int getRandomBroker() {
      return ThreadLocalRandom.current().nextInt(0, 3);
    }

    @Override
    public List<Integer> getPartitions() {
      return List.of(1);
    }

    @Override
    public List<Integer> getBrokers() {
      return List.of(0, 1, 2);
    }

    @Override
    public String getBrokerAddress(final int brokerId) {
      return "localhost:" + (26501 + brokerId);
    }

    @Override
    public String getBrokerVersion(final int brokerId) {
      return version;
    }

    @Override
    public PartitionHealthStatus getPartitionHealth(final int brokerId, final int partition) {
      if (partition != 1) {
        return PartitionHealthStatus.NULL_VAL;
      }

      return switch (brokerId) {
        case 0, 1 -> PartitionHealthStatus.HEALTHY;
        case 2 -> PartitionHealthStatus.UNHEALTHY;
        default -> PartitionHealthStatus.NULL_VAL;
      };
    }

    @Override
    public long getLastCompletedChangeId() {
      return 1;
    }

    @Override
    public String getClusterId() {
      return clusterId;
    }
  }
}
