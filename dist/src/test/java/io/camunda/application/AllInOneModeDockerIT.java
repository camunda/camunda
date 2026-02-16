/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application;

import static io.camunda.application.ModeTestUtils.assertPortOpen;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

public class AllInOneModeDockerIT extends AbstractCamundaDockerIT {

  private static final String STARTUP_LOG_MESSAGE = "Started Camunda using mode: ALL-IN-ONE";

  @Test
  void testAllInOneModeExposesCorrectPortsAndHealthEndpoint() throws Exception {
    /* create and start Elasticsearch container */

    final ElasticsearchContainer elasticsearchContainer =
        createContainer(this::createElasticsearchContainer);
    elasticsearchContainer.start();

    /* create Camunda container with ALL-IN-ONE mode configuration */

    final GenericContainer camundaContainer =
        createContainer(this::createCamundaContainer).withEnv("CAMUNDA_MODE", "all-in-one");
    startContainer(camundaContainer);
    final String camundaHost = camundaContainer.getHost();

    /* --- logs assertions --- */

    final String logs = camundaContainer.getLogs();
    org.junit.jupiter.api.Assertions.assertTrue(
        logs.contains(STARTUP_LOG_MESSAGE),
        "Expected startup log message not found in container logs.");

    /* --- ports assertions --- */

    final int exposedServicePort = camundaContainer.getMappedPort(8080);
    assertPortOpen(camundaHost, exposedServicePort);

    final int exposedBrokerPort = camundaContainer.getMappedPort(26500);
    assertPortOpen(camundaHost, exposedBrokerPort);
  }
}
