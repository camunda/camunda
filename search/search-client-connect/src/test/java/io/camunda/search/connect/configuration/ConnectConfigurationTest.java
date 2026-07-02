/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.connect.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for validating the Elasticsearch/OpenSearch connection-pool limits ({@code maxConnections}
 * and {@code maxConnectionsPerRoute}) on {@link ConnectConfiguration}.
 */
public class ConnectConfigurationTest {

  @Nested
  class WhenMaxConnectionsIsNotPositive {

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -10})
    void shouldThrowExceptionWhenAccessingMaxConnections(final int value) {
      // given
      final var configuration = new ConnectConfiguration();
      configuration.setMaxConnections(value);

      // when/then
      assertThatThrownBy(configuration::getMaxConnections)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("maxConnections")
          .hasMessageContaining("must be a positive value");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -10})
    void shouldThrowExceptionWhenAccessingMaxConnectionsPerRoute(final int value) {
      // given
      final var configuration = new ConnectConfiguration();
      configuration.setMaxConnectionsPerRoute(value);

      // when/then
      assertThatThrownBy(configuration::getMaxConnectionsPerRoute)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("maxConnectionsPerRoute")
          .hasMessageContaining("must be a positive value");
    }
  }

  @Nested
  class WhenConnectionPoolLimitsAreValid {

    @Test
    void shouldAllowPositiveValues() {
      // given
      final var configuration = new ConnectConfiguration();
      configuration.setMaxConnections(50);
      configuration.setMaxConnectionsPerRoute(25);

      // when/then
      assertThatNoException().isThrownBy(configuration::getMaxConnections);
      assertThatNoException().isThrownBy(configuration::getMaxConnectionsPerRoute);
      assertThat(configuration.getMaxConnections()).isEqualTo(50);
      assertThat(configuration.getMaxConnectionsPerRoute()).isEqualTo(25);
    }

    @Test
    void shouldAllowUnsetValues() {
      // given
      final var configuration = new ConnectConfiguration();

      // when/then
      assertThatNoException().isThrownBy(configuration::getMaxConnections);
      assertThatNoException().isThrownBy(configuration::getMaxConnectionsPerRoute);
      assertThat(configuration.getMaxConnections()).isNull();
      assertThat(configuration.getMaxConnectionsPerRoute()).isNull();
    }
  }
}
