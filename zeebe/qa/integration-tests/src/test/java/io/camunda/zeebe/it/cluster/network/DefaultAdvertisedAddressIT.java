/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.network;

import static io.camunda.application.commons.search.SearchEngineDatabaseConfiguration.SearchEngineSchemaManagerProperties.CREATE_SCHEMA_ENV_VAR;
import static io.camunda.application.commons.security.CamundaSecurityConfiguration.AUTHORIZATION_CHECKS_ENV_VAR;
import static io.camunda.application.commons.security.CamundaSecurityConfiguration.UNPROTECTED_API_ENV_VAR;

import io.camunda.client.CamundaClient;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.container.cluster.CamundaCluster;
import io.camunda.zeebe.qa.util.testcontainers.ZeebeTestContainerDefaults;
import io.camunda.zeebe.test.util.asserts.TopologyAssert;
import io.camunda.zeebe.test.util.testcontainers.ContainerLogsDumper;
import java.time.Duration;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
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
  private final CamundaCluster cluster =
      CamundaCluster.builder()
          .withImage(ZeebeTestContainerDefaults.defaultTestImage())
          .withGatewaysCount(1)
          .withBrokersCount(1)
          .withEmbeddedGateway(false)
          .withNodeConfig(
              node -> {
                node.withUnifiedConfig(
                    c -> {
                      // explicitly unset the (advertised) host to force computing the default
                      c.getCluster().getNetwork().setAdvertisedHost(null);
                      c.getCluster().getNetwork().setHost(null);
                      c.getData().getSecondaryStorage().setType(SecondaryStorageType.none);

                      node.addEnv(CREATE_SCHEMA_ENV_VAR, "false");
                      node.addEnv(UNPROTECTED_API_ENV_VAR, "true");
                      node.addEnv(AUTHORIZATION_CHECKS_ENV_VAR, "false");
                    });
              })
          .build();

  @SuppressWarnings("unused")
  @RegisterExtension
  private final ContainerLogsDumper logsDumper = new ContainerLogsDumper(cluster::getNodes);

  @Test
  void shouldFormClusterWithDefaultAdvertisedHost() {
    // given
    final var clientBuilder =
        CamundaClient.newClientBuilder()
            .defaultRequestTimeout(Duration.ofSeconds(5))
            .restAddress(cluster.getAvailableGateway().getRestAddress())
            .grpcAddress(cluster.getAvailableGateway().getGrpcAddress())
            .preferRestOverGrpc(true);

    try (final var client = clientBuilder.build()) {
      // when - then
      Awaitility.await("until topology is complete")
          .atMost(Duration.ofMinutes(2))
          .untilAsserted(
              () ->
                  TopologyAssert.assertThat(
                          client.newTopologyRequest().send().join(5, TimeUnit.SECONDS))
                      .isComplete(1, 1, 1));

      Awaitility.await("until a client request is successful")
          .atMost(Duration.ofMinutes(1))
          .untilAsserted(
              () ->
                  Assertions.assertThat(
                          (Future<?>)
                              client
                                  .newPublishMessageCommand()
                                  .messageName("test-message")
                                  .correlationKey("test-key")
                                  .send())
                      .succeedsWithin(Duration.ofSeconds(5)));
    }
  }
}
