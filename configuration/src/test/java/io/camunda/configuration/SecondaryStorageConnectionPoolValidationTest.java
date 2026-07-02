/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for validating the Elasticsearch/OpenSearch connection-pool limits ({@code max-connections}
 * and {@code max-connections-per-route}) in document-based secondary storage databases.
 */
public class SecondaryStorageConnectionPoolValidationTest {

  /** Test implementation of DocumentBasedSecondaryStorageDatabase for unit testing. */
  private static final class TestDocumentBasedDatabase
      extends DocumentBasedSecondaryStorageDatabase {

    @Override
    public String databaseName() {
      return "TestDatabase";
    }
  }

  @Nested
  class WhenMaxConnectionsIsNotPositive {

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -10})
    void shouldThrowExceptionWhenAccessingMaxConnections(final int value) {
      // given
      final var database = new TestDocumentBasedDatabase();
      database.setMaxConnections(value);

      // when/then
      assertThatThrownBy(database::getMaxConnections)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("max-connections")
          .hasMessageContaining("must be a positive value");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -10})
    void shouldThrowExceptionWhenAccessingMaxConnectionsPerRoute(final int value) {
      // given
      final var database = new TestDocumentBasedDatabase();
      database.setMaxConnectionsPerRoute(value);

      // when/then
      assertThatThrownBy(database::getMaxConnectionsPerRoute)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("max-connections-per-route")
          .hasMessageContaining("must be a positive value");
    }
  }

  @Nested
  class WhenConnectionPoolLimitsAreValid {

    @Test
    void shouldAllowPositiveValues() {
      // given
      final var database = new TestDocumentBasedDatabase();
      database.setMaxConnections(50);
      database.setMaxConnectionsPerRoute(25);

      // when/then
      assertThatNoException().isThrownBy(database::getMaxConnections);
      assertThatNoException().isThrownBy(database::getMaxConnectionsPerRoute);
      assertThat(database.getMaxConnections()).isEqualTo(50);
      assertThat(database.getMaxConnectionsPerRoute()).isEqualTo(25);
    }

    @Test
    void shouldAllowUnsetValues() {
      // given
      final var database = new TestDocumentBasedDatabase();

      // when/then
      assertThatNoException().isThrownBy(database::getMaxConnections);
      assertThatNoException().isThrownBy(database::getMaxConnectionsPerRoute);
      assertThat(database.getMaxConnections()).isNull();
      assertThat(database.getMaxConnectionsPerRoute()).isNull();
    }
  }
}
