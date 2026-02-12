/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.eventhub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AzureEventHubExporterConfigurationTest {

  @Test
  void shouldValidateValidConfiguration() {
    // given
    final var config = new AzureEventHubExporterConfiguration()
        .setConnectionString("Endpoint=sb://test.servicebus.windows.net/;SharedAccessKeyName=test;SharedAccessKey=test")
        .setEventHubName("test-hub")
        .setMaxBatchSize(100)
        .setBatchIntervalMs(1000L);

    // when / then - should not throw
    config.validate();
  }

  @Test
  void shouldRejectMissingConnectionString() {
    // given
    final var config = new AzureEventHubExporterConfiguration()
        .setEventHubName("test-hub");

    // when / then
    assertThatThrownBy(config::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("connection string must be configured");
  }

  @Test
  void shouldRejectEmptyConnectionString() {
    // given
    final var config = new AzureEventHubExporterConfiguration()
        .setConnectionString("")
        .setEventHubName("test-hub");

    // when / then
    assertThatThrownBy(config::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("connection string must be configured");
  }

  @Test
  void shouldRejectMissingEventHubName() {
    // given
    final var config = new AzureEventHubExporterConfiguration()
        .setConnectionString("Endpoint=sb://test.servicebus.windows.net/;SharedAccessKeyName=test;SharedAccessKey=test");

    // when / then
    assertThatThrownBy(config::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Event Hub name must be configured");
  }

  @Test
  void shouldRejectEmptyEventHubName() {
    // given
    final var config = new AzureEventHubExporterConfiguration()
        .setConnectionString("Endpoint=sb://test.servicebus.windows.net/;SharedAccessKeyName=test;SharedAccessKey=test")
        .setEventHubName("");

    // when / then
    assertThatThrownBy(config::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Event Hub name must be configured");
  }

  @Test
  void shouldRejectInvalidMaxBatchSize() {
    // given
    final var config = new AzureEventHubExporterConfiguration()
        .setConnectionString("Endpoint=sb://test.servicebus.windows.net/;SharedAccessKeyName=test;SharedAccessKey=test")
        .setEventHubName("test-hub")
        .setMaxBatchSize(0);

    // when / then
    assertThatThrownBy(config::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Max batch size must be greater than 0");
  }

  @Test
  void shouldRejectInvalidBatchInterval() {
    // given
    final var config = new AzureEventHubExporterConfiguration()
        .setConnectionString("Endpoint=sb://test.servicebus.windows.net/;SharedAccessKeyName=test;SharedAccessKey=test")
        .setEventHubName("test-hub")
        .setBatchIntervalMs(0L);

    // when / then
    assertThatThrownBy(config::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Batch interval must be greater than 0");
  }

  @Test
  void shouldUseDefaultValues() {
    // given
    final var config = new AzureEventHubExporterConfiguration();

    // when / then
    assertThat(config.getMaxBatchSize()).isEqualTo(100);
    assertThat(config.getBatchIntervalMs()).isEqualTo(1000L);
  }
}
