/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.beans.GatewayBasedProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.context.LifecycleProperties;

final class GatewayBasedConfigurationTest {

  @Test
  void shouldCarryUdpEnabledIntoTheMessagingConfig() {
    // given
    final var properties = new GatewayBasedProperties();
    properties.getCluster().setUdpEnabled(false);

    // when
    final var clusterConfig = configuration(properties).clusterConfig();

    // then - the standalone gateway has its own path to MessagingConfig, separate from the broker's
    assertThat(clusterConfig.getMessagingConfig().isUdpEnabled()).isFalse();
  }

  @Test
  void shouldEnableUdpInTheMessagingConfigByDefault() {
    // given
    final var properties = new GatewayBasedProperties();

    // when
    final var clusterConfig = configuration(properties).clusterConfig();

    // then
    assertThat(clusterConfig.getMessagingConfig().isUdpEnabled()).isTrue();
  }

  private static GatewayBasedConfiguration configuration(final GatewayBasedProperties properties) {
    return new GatewayBasedConfiguration(properties, new LifecycleProperties());
  }
}
