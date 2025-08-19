/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.http;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.exporter.http.config.HttpExporterConfig;
import io.camunda.exporter.http.config.SubscriptionConfigFactory;
import io.camunda.zeebe.protocol.jackson.ZeebeProtocolModule;
import org.junit.jupiter.api.Test;

final class ConfigTest {

  @Test
  void testConfigLoading() {
    final var configFactory =
        new SubscriptionConfigFactory(new ObjectMapper().registerModule(new ZeebeProtocolModule()));
    final var exporterConfig = new HttpExporterConfig();
    exporterConfig.setConfigPath("classpath:subscription-config-complete.json");
    final var subscriptionConfig = configFactory.readConfigFrom(exporterConfig);
    assertThat(subscriptionConfig.filters()).isNotEmpty();
  }

  @Test
  void testConfigLoadingWithEmptyCollections() {
    final var configFactory =
        new SubscriptionConfigFactory(new ObjectMapper().registerModule(new ZeebeProtocolModule()));
    final var exporterConfig = new HttpExporterConfig();
    exporterConfig.setConfigPath("classpath:subscription-config-simple.json");
    final var subscriptionConfig = configFactory.readConfigFrom(exporterConfig);
    assertThat(subscriptionConfig.filters()).isEmpty();
  }
}
