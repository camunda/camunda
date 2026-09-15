/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

final class SchemaManagerRetryTest {

  private static final String LEGACY_MAX_RETRIES =
      "camunda.database.schema-manager.retry.maxRetries";

  private MockEnvironment mockEnvironment;

  @BeforeEach
  void setUp() {
    mockEnvironment = new MockEnvironment();
    UnifiedConfigurationHelper.setCustomEnvironment(mockEnvironment);
  }

  @AfterEach
  void tearDown() {
    UnifiedConfigurationHelper.setCustomEnvironment(null);
  }

  @Test
  void shouldDefaultToUnboundedRetryForEverySecondaryStorage() {
    // given
    final var elasticsearch = new Elasticsearch().getRetry();
    final var rdbms = new Rdbms().getRetry();

    // then
    for (final var retry : new SchemaManagerRetry[] {elasticsearch, rdbms}) {
      assertThat(retry.getMaxRetries()).isEqualTo(Integer.MAX_VALUE);
      assertThat(retry.getMinRetryDelay()).isEqualTo(Duration.ofMillis(500));
      assertThat(retry.getMaxRetryDelay()).isEqualTo(Duration.ofSeconds(10));
    }
  }

  @Test
  void shouldNotApplyTheSchemaManagerLegacyPropertyToRdbms() {
    // given - the legacy prefix only ever configured the document-based schema manager
    mockEnvironment.setProperty(LEGACY_MAX_RETRIES, "5");

    // then
    assertThat(new Rdbms().getRetry().getMaxRetries()).isEqualTo(Integer.MAX_VALUE);
    assertThat(new Elasticsearch().getRetry().getMaxRetries()).isEqualTo(5);
  }

  @Test
  void shouldUseConfiguredValuesForRdbms() {
    // given
    final var retry = new Rdbms().getRetry();
    retry.setMaxRetries(7);
    retry.setMinRetryDelay(Duration.ofSeconds(1));
    retry.setMaxRetryDelay(Duration.ofSeconds(20));

    // then
    assertThat(retry.getMaxRetries()).isEqualTo(7);
    assertThat(retry.getMinRetryDelay()).isEqualTo(Duration.ofSeconds(1));
    assertThat(retry.getMaxRetryDelay()).isEqualTo(Duration.ofSeconds(20));
  }
}
