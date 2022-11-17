/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.smoke;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.qa.util.testcontainers.ZeebeTestContainerDefaults;
import io.zeebe.containers.cluster.ZeebeCluster;
import java.util.concurrent.TimeUnit;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
final class ContainerClusterSmokeIT {

  @Container
  private final ZeebeCluster cluster =
      ZeebeCluster.builder()
          .withBrokersCount(1)
          .withGatewaysCount(1)
          .withPartitionsCount(1)
          .withEmbeddedGateway(false)
          .withImage(ZeebeTestContainerDefaults.defaultTestImage())
          .build();

  /** A smoke test which checks that a gateway of a cluster can be accessed. */
  @ContainerSmokeTest
  void connectSmokeTest() {
    // given
    try (final var client = cluster.newClientBuilder().build()) {
      // when
      final var topology = client.newTopologyRequest().send();

      // then
      final var result = topology.join(5L, TimeUnit.SECONDS);
      assertThat(result.getBrokers()).as("There is one connected broker").hasSize(1);
    }
  }
}
