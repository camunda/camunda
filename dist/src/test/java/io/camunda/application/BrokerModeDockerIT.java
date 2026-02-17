/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application;

import static io.camunda.application.ModeTestUtils.assertPortOpen;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

public class BrokerModeDockerIT extends AbstractCamundaDockerIT {

  private static final String STARTUP_LOG_MESSAGE = "Started Camunda using mode: BROKER";

  @Test
  void testBrokerModeExposesCorrectPortsAndHealthEndpoint() throws Exception {
    /* create and start Elasticsearch container */

    final ElasticsearchContainer elasticsearchContainer =
        createContainer(this::createElasticsearchContainer);
    elasticsearchContainer.start();

    /* create Camunda container with BROKER mode configuration */

    final GenericContainer camundaContainer =
        createContainer(this::createCamundaContainer)
            .withEnv("CAMUNDA_MODE", "broker")
            .waitingFor(
                Wait.forHttp("/actuator/health/brokerReady")
                    .forPort(9600)
                    .forStatusCode(200)
                    .forResponsePredicate(response -> response.contains("{\"status\":\"UP\"}"))
                    .withStartupTimeout(Duration.ofSeconds(60)));
    startContainer(camundaContainer);
    final String camundaHost = camundaContainer.getHost();

    /* --- logs assertions --- */

    final String logs = camundaContainer.getLogs();
    org.junit.jupiter.api.Assertions.assertTrue(
        logs.contains(STARTUP_LOG_MESSAGE),
        "Expected startup log message not found in container logs.");

    /* --- ports assertions --- */

    final int exposedServicePort = camundaContainer.getMappedPort(26500);
    assertPortOpen(camundaHost, exposedServicePort);
  }
}
