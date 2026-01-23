/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.configuration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.zeebe.gateway.rest.controller.ProcessInstanceController;
import io.camunda.zeebe.gateway.rest.controller.TopologyController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(value = {ProcessInstanceController.class, TopologyController.class})
public class RestApiDefaultConfigurationTest extends RestApiConfigurationTest {

  @MockitoBean private CamundaAuthenticationProvider authenticationProvider;

  @BeforeEach
  void setUpServices() {
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
  }

  @Test
  void shouldYieldOkForQueryApiRequest() {
    // when
    webClient
        .post()
        .uri(PROCESS_INSTANCES_SEARCH_URL)
        .exchange()
        // then
        .expectStatus()
        .isOk();

    verify(processInstanceServices).search(any(ProcessInstanceQuery.class));
  }

  @Test
  void shouldYieldOkForTopologyRequest() {
    // when
    webClient
        .get()
        .uri(TOPOLOGY_URL)
        .exchange()
        // then
        .expectStatus()
        .isOk();

    verify(topologyServices).getTopology();
  }
}
