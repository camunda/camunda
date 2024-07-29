/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.application.commons.CommonsModuleConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
    classes = {CommonsModuleConfiguration.class, GatewayModuleConfiguration.class},
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = {"zeebe.broker.gateway.enable=false"})
@ActiveProfiles(profiles = {"gateway", "standalone"})
@DirtiesContext
class StandaloneGatewayBrokerGatewayDisabledTest {

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;

  @Test
  void shouldStartWithRestDisabled() {
    // when/then
    assertThat(restTemplate.getForObject("http://localhost:" + port + "/v2/topology", String.class))
        .contains("clusterSize");
  }

  @Test
  void shouldStartWithRestQueryDisabled() {
    // when/then
    assertThat(
            restTemplate.postForObject(
                "http://localhost:" + port + "/v2/process-instances/search", "{}", String.class))
        .contains("404");
  }
}
