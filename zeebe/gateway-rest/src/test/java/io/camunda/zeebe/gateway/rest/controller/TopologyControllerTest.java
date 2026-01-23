/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.TopologyServices;
import io.camunda.service.TopologyServices.Broker;
import io.camunda.service.TopologyServices.Health;
import io.camunda.service.TopologyServices.Partition;
import io.camunda.service.TopologyServices.Role;
import io.camunda.service.TopologyServices.Topology;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.util.VersionUtil;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

@WebMvcTest(TopologyController.class)
public class TopologyControllerTest extends RestControllerTest {

  @MockitoBean TopologyServices topologyServices;
  @MockitoBean CamundaAuthenticationProvider authenticationProvider;

  @BeforeEach
  void setUp() {
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
    when(topologyServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(topologyServices);
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

    final var topology =
        new Topology(
            List.of(
                new Broker(
                    0,
                    "localhost",
                    26501,
                    List.of(new Partition(1, Role.LEADER, Health.HEALTHY)),
                    version),
                new Broker(
                    1,
                    "localhost",
                    26502,
                    List.of(new Partition(1, Role.FOLLOWER, Health.HEALTHY)),
                    version),
                new Broker(
                    2,
                    "localhost",
                    26503,
                    List.of(new Partition(1, Role.INACTIVE, Health.UNHEALTHY)),
                    version)),
            "cluster-id",
            3,
            1,
            3,
            version,
            1L);
    Mockito.when(topologyServices.getTopology())
        .thenReturn(CompletableFuture.completedFuture(topology));

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
    Mockito.when(topologyServices.getTopology())
        .thenReturn(
            CompletableFuture.completedFuture(
                new Topology(List.of(), null, null, null, null, version, null)));

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
}
