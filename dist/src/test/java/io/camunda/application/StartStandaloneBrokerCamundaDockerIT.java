/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.dockerjava.api.command.CreateContainerCmd;
import java.time.Duration;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;

@EnabledIfSystemProperty(named = "camunda.docker.test.enabled", matches = "true")
public class StartStandaloneBrokerCamundaDockerIT extends AbstractCamundaDockerIT {

  @Test
  // Regression for https://github.com/camunda/camunda/issues/38487
  public void testStartStandaloneBrokerWithoutRestAPI() throws Exception {
    // given
    // create camunda container with only StandaloneBroker app
    final var standaloneBrokerContainer =
        new GenericContainer<>(CAMUNDA_TEST_DOCKER_IMAGE)
            .withCreateContainerCmdModifier(
                (final CreateContainerCmd cmd) ->
                    cmd.withEntrypoint("/usr/local/camunda/bin/broker"))
            .withExposedPorts(SERVER_PORT, MANAGEMENT_PORT, GATEWAY_GRPC_PORT)
            .withNetwork(network)
            .withNetworkAliases(CAMUNDA_NETWORK_ALIAS)
            .waitingFor(
                new HttpWaitStrategy()
                    .forPort(MANAGEMENT_PORT)
                    .forPath("/actuator/health")
                    .withReadTimeout(Duration.ofSeconds(120)))
            .withStartupTimeout(Duration.ofSeconds(300))
            // disable the REST API
            .withEnv("ZEEBE_BROKER_GATEWAY_ENABLE", "false");

    // when - then the container should start without errors
    startContainer(createContainer(() -> standaloneBrokerContainer));

    try (final CloseableHttpClient httpClient = HttpClients.createDefault();
        final CloseableHttpResponse healthCheckResponse =
            httpClient.execute(
                new HttpGet(
                    String.format(
                        "http://%s:%d%s",
                        standaloneBrokerContainer.getHost(),
                        standaloneBrokerContainer.getMappedPort(MANAGEMENT_PORT),
                        "/actuator/cluster")))) {

      // then - the status code should be 200 OK (instead of a 500 error as reported in #38487)
      assertThat(healthCheckResponse.getCode()).isEqualTo(200);
    }
  }
}
