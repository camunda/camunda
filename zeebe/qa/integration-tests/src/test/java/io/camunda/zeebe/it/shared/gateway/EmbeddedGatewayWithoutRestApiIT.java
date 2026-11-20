/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.shared.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import org.junit.jupiter.api.Test;

/**
 * The gRPC gateway next to the broker needs user services to build its authentication handlers, and
 * those only exist when an HTTP gateway (REST or MCP) is enabled.
 */
final class EmbeddedGatewayWithoutRestApiIT {

  @Test
  void shouldFailToStartWithProtectedApiWhenRestIsDisabled() {
    // given
    try (final var broker = newBrokerWithoutRest(false)) {

      // when / then
      assertThatThrownBy(broker::start)
          .hasStackTraceContaining(
              "Failed to build AuthenticationHandler for physical tenant 'default'");
    }
  }

  @Test
  void shouldStartWithUnprotectedApiWhenRestIsDisabled() {
    // given
    try (final var broker = newBrokerWithoutRest(true)) {

      // when
      broker.start();

      // then
      try (final var client = broker.newClientBuilder().preferRestOverGrpc(false).build()) {
        assertThat(client.newTopologyRequest().send().join().getBrokers()).hasSize(1);
      }
    }
  }

  private static TestStandaloneBroker newBrokerWithoutRest(final boolean unprotectedApi) {
    return new TestStandaloneBroker()
        .withUnifiedConfig(
            cfg -> cfg.getSecurity().getAuthentication().setUnprotectedApi(unprotectedApi))
        .withProperty("camunda.rest.enabled", false);
  }
}
