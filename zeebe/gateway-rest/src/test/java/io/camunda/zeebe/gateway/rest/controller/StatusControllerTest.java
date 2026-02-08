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
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.TopologyServices;
import io.camunda.service.TopologyServices.ClusterStatus;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(StatusController.class)
class StatusControllerTest extends RestControllerTest {

  static final String STATUS_URL = "/v2/status";

  @MockitoBean TopologyServices topologyServices;
  @MockitoBean CamundaAuthenticationProvider authenticationProvider;

  @BeforeEach
  void setUp() {
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
    when(topologyServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(topologyServices);
  }

  @Test
  void shouldReturnNoContentWhenHealthy() {
    // given
    when(topologyServices.getStatus())
        .thenReturn(CompletableFuture.completedFuture(ClusterStatus.HEALTHY));

    // when / then
    webClient
        .get()
        .uri(STATUS_URL)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNoContent()
        .expectBody()
        .isEmpty();
  }

  @Test
  void shouldReturnServiceUnavailableWhenNoPartitionHasHealthyLeader() {
    // given
    when(topologyServices.getStatus())
        .thenReturn(CompletableFuture.completedFuture(ClusterStatus.UNHEALTHY));

    // when / then
    webClient
        .get()
        .uri(STATUS_URL)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isEqualTo(SERVICE_UNAVAILABLE)
        .expectBody()
        .isEmpty();
  }
}
