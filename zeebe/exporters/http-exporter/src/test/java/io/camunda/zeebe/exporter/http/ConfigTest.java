/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.http;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.exporter.http.config.HttpExporterConfiguration;
import io.camunda.zeebe.exporter.http.config.SubscriptionConfigFactory;
import io.camunda.zeebe.protocol.jackson.ZeebeProtocolModule;
import org.junit.jupiter.api.Test;

final class ConfigTest {

  @Test
  void testConfigLoading() throws Throwable {
    final var configFactory =
        new SubscriptionConfigFactory(new ObjectMapper().registerModule(new ZeebeProtocolModule()));
    final var exporterConfig = new HttpExporterConfiguration();
    exporterConfig.setConfigPath("classpath:subscription-config-complete.json");
    final var subscriptionConfig = configFactory.readConfigFrom(exporterConfig);
    assertThat(subscriptionConfig.filters()).isNotEmpty();
    assertThat(subscriptionConfig.rules()).isNotEmpty();
  }

  @Test
  void testConfigLoadingWithEmptyCollections() throws Throwable {
    final var configFactory =
        new SubscriptionConfigFactory(new ObjectMapper().registerModule(new ZeebeProtocolModule()));
    final var exporterConfig = new HttpExporterConfiguration();
    exporterConfig.setConfigPath("classpath:subscription-config-simple.json");
    final var subscriptionConfig = configFactory.readConfigFrom(exporterConfig);
    assertThat(subscriptionConfig.filters()).isEmpty();
    assertThat(subscriptionConfig.rules()).isEmpty();
  }
}
