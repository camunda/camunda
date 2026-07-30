/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

import io.camunda.service.ClusterStatusServices;
import io.camunda.service.ClusterStatusServices.AggregatedStatus;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;

@WebMvcTest(ClusterStatusController.class)
class ClusterStatusControllerTest extends RestControllerTest {

  static final String CLUSTER_STATUS_URL = "/cluster/v2/status";

  @MockitoBean ClusterStatusServices clusterStatusServices;
  @MockitoBean ServiceRegistry serviceRegistry;

  @BeforeEach
  void setup() {
    when(serviceRegistry.clusterStatusServices()).thenReturn(clusterStatusServices);
  }

  @Test
  void shouldReturnOkWhenEveryPhysicalTenantIsHealthy() {
    // given
    givenStatus(AggregatedStatus.HEALTHY);

    // when / then
    getClusterStatus()
        .expectStatus()
        .isOk()
        .expectBody()
        .json("{\"status\":\"HEALTHY\"}", JsonCompareMode.STRICT);
  }

  @Test
  void shouldReturnOkWhenDegraded() {
    // given — a degraded cluster still processes work, so it must not be reported as unavailable
    givenStatus(AggregatedStatus.DEGRADED);

    // when / then
    getClusterStatus()
        .expectStatus()
        .isOk()
        .expectBody()
        .json("{\"status\":\"DEGRADED\"}", JsonCompareMode.STRICT);
  }

  @Test
  void shouldReturnServiceUnavailableWhenNoPhysicalTenantCanProcessWork() {
    // given
    givenStatus(AggregatedStatus.DOWN);

    // when / then
    getClusterStatus()
        .expectStatus()
        .isEqualTo(SERVICE_UNAVAILABLE)
        .expectBody()
        .json("{\"status\":\"DOWN\"}", JsonCompareMode.STRICT);
  }

  private void givenStatus(final AggregatedStatus status) {
    when(clusterStatusServices.getStatus()).thenReturn(CompletableFuture.completedFuture(status));
  }

  private ResponseSpec getClusterStatus() {
    return webClient.get().uri(CLUSTER_STATUS_URL).accept(MediaType.APPLICATION_JSON).exchange();
  }
}
