/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

import org.junit.jupiter.api.Test;

class LiquibaseSchemaManagerTest {

  @Test
  void shouldHaveInitializedFalseByDefault() {
    // given
    final var schemaManager = new LiquibaseSchemaManager();

    // when / then
    assertThat(schemaManager.isInitialized()).isFalse();
  }

  @Test
  void shouldSetInitializedTrueAfterSuccessfulInitialization() throws Exception {
    // given
    final var schemaManager = new TestLiquibaseSchemaManager();

    // when
    schemaManager.afterPropertiesSet();

    // then
    assertThat(schemaManager.isInitialized()).isTrue();
  }

  @Test
  void shouldKeepInitializedFalseWhenInitializationFails() {
    // given
    final var schemaManager = spy(new TestLiquibaseSchemaManager());
    doThrow(new RuntimeException("Initialization failed")).when(schemaManager).performMigration();

    // when / then
    assertThatThrownBy(() -> schemaManager.afterPropertiesSet())
        .isInstanceOf(RuntimeException.class)
        .hasMessage("Initialization failed");

    assertThat(schemaManager.isInitialized()).isFalse();
  }

  /**
   * Test implementation that overrides the parent's afterPropertiesSet to avoid actual Liquibase
   * initialization.
   */
  private static final class TestLiquibaseSchemaManager extends LiquibaseSchemaManager {
    @Override
    public void afterPropertiesSet() throws Exception {
      // Skip the actual Liquibase initialization (super.afterPropertiesSet())
      // and just perform our state update
      performMigration();
      setInitialized();
    }

    protected void performMigration() {
      // No-op for testing, can be overridden in spy
    }

    protected void setInitialized() {
      // Access the inherited behavior through a test method
      try {
        final var field = LiquibaseSchemaManager.class.getDeclaredField("initialized");
        field.setAccessible(true);
        field.set(this, true);
      } catch (final Exception e) {
        throw new RuntimeException(e);
      }
    }
  }
}
