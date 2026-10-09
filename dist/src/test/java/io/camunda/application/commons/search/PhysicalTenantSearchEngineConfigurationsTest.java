/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.search;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.configuration.UnifiedConfigurationHelper;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * The per-tenant path builds its schema-manager properties directly rather than through the {@code
 * SearchEngineSchemaManagerProperties} bean, so it is the path that has to carry retry settings to
 * {@link SearchEngineSchemaInitializer}.
 */
final class PhysicalTenantSearchEngineConfigurationsTest {

  @BeforeAll
  static void setUpEnvironment() {
    UnifiedConfigurationHelper.setCustomEnvironment(new MockEnvironment());
  }

  @AfterAll
  static void tearDownEnvironment() {
    UnifiedConfigurationHelper.setCustomEnvironment(null);
  }

  @Test
  void shouldCarryRetryConfigurationToTheTenantConfiguration() {
    // given
    final var camunda = new Camunda();
    final var secondaryStorage = camunda.getData().getSecondaryStorage();
    secondaryStorage.setType(SecondaryStorageType.elasticsearch);
    secondaryStorage.getElasticsearch().getRetry().setMaxRetries(7);
    secondaryStorage.getElasticsearch().getRetry().setMinRetryDelay(Duration.ofSeconds(1));
    secondaryStorage.getElasticsearch().getRetry().setMaxRetryDelay(Duration.ofSeconds(20));

    // when
    final var configuration = PhysicalTenantSearchEngineConfigurations.convert(camunda);

    // then
    final var retry = configuration.schemaManager().getRetry();
    assertThat(retry.getMaxRetries()).isEqualTo(7);
    assertThat(retry.getMinRetryDelay()).isEqualTo(Duration.ofSeconds(1));
    assertThat(retry.getMaxRetryDelay()).isEqualTo(Duration.ofSeconds(20));
  }

  @Test
  void shouldDefaultToUnboundedRetry() {
    // given
    final var camunda = new Camunda();
    camunda.getData().getSecondaryStorage().setType(SecondaryStorageType.elasticsearch);

    // when
    final var configuration = PhysicalTenantSearchEngineConfigurations.convert(camunda);

    // then
    final var retry = configuration.schemaManager().getRetry();
    assertThat(retry.getMaxRetries()).isEqualTo(Integer.MAX_VALUE);
    assertThat(retry.getMinRetryDelay()).isEqualTo(Duration.ofMillis(500));
    assertThat(retry.getMaxRetryDelay()).isEqualTo(Duration.ofSeconds(10));
  }
}
