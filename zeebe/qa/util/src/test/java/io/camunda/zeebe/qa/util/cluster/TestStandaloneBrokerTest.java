/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.qa.util.cluster;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

final class TestStandaloneBrokerTest {

  @Test
  void shouldFallBackToLocalhostWhenNetworkHostIsNotConfigured() {
    // given
    final var broker = new TestStandaloneBroker();

    // when - no camunda.cluster.network.host is configured

    // then - host() must return a connectable address (matching TestApplication's own default),
    // not the broker's bind-any address, since it is used to build actuator/monitoring probe URIs
    assertThat(broker.host()).isEqualTo("localhost");
  }

  @Test
  void shouldUseConfiguredNetworkHost() {
    // given
    final var broker = new TestStandaloneBroker();
    broker.withClusterConfig(cluster -> cluster.getNetwork().setHost("192.168.1.1"));

    // when
    final var host = broker.host();

    // then
    assertThat(host).isEqualTo("192.168.1.1");
  }
}
