/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.network;

import static io.camunda.application.commons.search.SearchEngineDatabaseConfiguration.SearchEngineSchemaManagerProperties.CREATE_SCHEMA_ENV_VAR;

import io.camunda.client.CamundaClient;
import io.camunda.zeebe.qa.util.testcontainers.ZeebeTestContainerDefaults;
import io.camunda.zeebe.test.util.asserts.TopologyAssert;
import io.zeebe.containers.cluster.ZeebeCluster;
import java.net.URI;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * This test ensures that the bare Docker image will always pick up the right host bind and
 * advertised host such that the nodes can form a cluster and a client can connect to it, all
 * without special configuration.
 */
@Testcontainers
final class DefaultAdvertisedAddressIT {
  @Container
  private final ZeebeCluster cluster =
      ZeebeCluster.builder()
          .withImage(ZeebeTestContainerDefaults.defaultTestImage())
          .withGatewaysCount(1)
          .withBrokersCount(1)
          .withNodeConfig(node -> node.withAdditionalExposedPort(8080))
          // explicitly unset the (advertised) host to force computing the default
          .withBrokerConfig(
              node -> {
                node.getEnvMap().remove("ZEEBE_BROKER_NETWORK_ADVERTISEDHOST");
                node.getEnvMap().remove("ZEEBE_BROKER_NETWORK_HOST");
                node.addEnv(CREATE_SCHEMA_ENV_VAR, "false");
              })
          .withGatewayConfig(
              node -> {
                node.getEnvMap().remove("ZEEBE_GATEWAY_CLUSTER_ADVERTISEDHOST");
                node.getEnvMap().remove("ZEEBE_GATEWAY_CLUSTER_HOST");
                node.addEnv(CREATE_SCHEMA_ENV_VAR, "false");
              })
          .build();

  @Test
  void shouldFormClusterWithDefaultAdvertisedHost() {
    // given
    final var clientBuilder =
        CamundaClient.newClientBuilder()
            .usePlaintext()
            .restAddress(
                URI.create("http://localhost:" + cluster.getAvailableGateway().getMappedPort(8080)))
            .grpcAddress(
                URI.create("http://" + cluster.getAvailableGateway().getExternalGatewayAddress()));

    try (final var client = clientBuilder.build()) {
      // when - then
      Awaitility.await("until topology is complete")
          .atMost(Duration.ofSeconds(30))
          .untilAsserted(
              () ->
                  TopologyAssert.assertThat(client.newTopologyRequest().send().join())
                      .isComplete(1, 1, 1));
    }
  }
}
