/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.configuration;

import static org.mockito.Mockito.verifyNoInteractions;

import io.camunda.zeebe.gateway.rest.controller.ProcessInstanceController;
import io.camunda.zeebe.gateway.rest.controller.TopologyController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;

@WebMvcTest(
    value = {ProcessInstanceController.class, TopologyController.class},
    properties = "camunda.rest.enabled=false")
public class RestApiDisabledTest extends RestApiConfigurationTest {

  @Test
  void shouldYieldNotFoundForQueryApiRequest() {
    // when
    webClient
        .post()
        .uri(PROCESS_INSTANCES_SEARCH_URL)
        .exchange()
        // then
        .expectStatus()
        .isNotFound();

    verifyNoInteractions(processInstanceServices);
  }

  @Test
  void shouldYieldNotFoundForTopologyRequest() {
    // when
    webClient
        .get()
        .uri(TOPOLOGY_URL)
        .exchange()
        // then
        .expectStatus()
        .isNotFound();

    verifyNoInteractions(topologyServices);
  }
}
