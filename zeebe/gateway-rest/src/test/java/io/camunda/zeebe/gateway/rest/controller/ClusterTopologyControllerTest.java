/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static org.mockito.Mockito.when;

import io.atomix.cluster.BrokerMemberId;
import io.camunda.service.ClusterTopologyServices;
import io.camunda.service.ClusterTopologyServices.ClusterBroker;
import io.camunda.service.ClusterTopologyServices.ClusterTopology;
import io.camunda.service.ClusterTopologyServices.PhysicalTenantBroker;
import io.camunda.service.ClusterTopologyServices.PhysicalTenantTopology;
import io.camunda.service.TopologyServices.Health;
import io.camunda.service.TopologyServices.Partition;
import io.camunda.service.TopologyServices.Role;
import io.camunda.service.TopologyServices.State;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

@WebMvcTest(ClusterTopologyController.class)
class ClusterTopologyControllerTest extends RestControllerTest {

  static final String CLUSTER_TOPOLOGY_URL = "/cluster/v2/topology";

  @MockitoBean ClusterTopologyServices clusterTopologyServices;
  @MockitoBean ServiceRegistry serviceRegistry;

  @BeforeEach
  void setup() {
    when(serviceRegistry.clusterTopologyServices()).thenReturn(clusterTopologyServices);
  }

  @Test
  void shouldReturnOkWithTheAggregatedTopologyOfEveryPhysicalTenant() {
    // given
    final var brokerZero = BrokerMemberId.from(0);
    final var brokerOne = BrokerMemberId.from(1);
    final var partition = new Partition(1, Role.LEADER, Health.HEALTHY, State.ACTIVE);
    final var topology =
        new ClusterTopology(
            List.of(
                new ClusterBroker(brokerZero, "host-0", 26501, "8.10.0"),
                new ClusterBroker(brokerOne, "host-1", 26502, "8.10.0")),
            "cluster-1",
            2,
            "8.10.0",
            List.of(
                new PhysicalTenantTopology(
                    "default",
                    1,
                    1,
                    5L,
                    List.of(new PhysicalTenantBroker(brokerZero, List.of(partition)))),
                new PhysicalTenantTopology(
                    "tenantb",
                    1,
                    1,
                    7L,
                    List.of(new PhysicalTenantBroker(brokerOne, List.of(partition))))));
    when(clusterTopologyServices.getTopology())
        .thenReturn(CompletableFuture.completedFuture(topology));

    // when / then
    webClient
        .get()
        .uri(CLUSTER_TOPOLOGY_URL)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            """
            {
              "brokers": [
                { "brokerId": "0", "host": "host-0", "port": 26501, "version": "8.10.0" },
                { "brokerId": "1", "host": "host-1", "port": 26502, "version": "8.10.0" }
              ],
              "clusterId": "cluster-1",
              "clusterSize": 2,
              "gatewayVersion": "8.10.0",
              "physicalTenants": [
                {
                  "physicalTenantId": "default",
                  "partitionsCount": 1,
                  "replicationFactor": 1,
                  "lastCompletedChangeId": "5",
                  "brokers": [
                    {
                      "brokerId": "0",
                      "partitions": [
                        { "partitionId": 1, "role": "leader", "health": "healthy", "state": "active"}
                      ]
                    }
                  ]
                },
                {
                  "physicalTenantId": "tenantb",
                  "partitionsCount": 1,
                  "replicationFactor": 1,
                  "lastCompletedChangeId": "7",
                  "brokers": [
                    {
                      "brokerId": "1",
                      "partitions": [
                        { "partitionId": 1, "role": "leader", "health": "healthy", "state": "active"}
                      ]
                    }
                  ]
                }
              ]
            }
            """,
            JsonCompareMode.STRICT);
  }
}
