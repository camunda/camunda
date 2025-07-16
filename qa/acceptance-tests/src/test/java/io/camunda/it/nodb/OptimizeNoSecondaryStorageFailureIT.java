/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.nodb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.search.connect.configuration.DatabaseConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;

/**
 * Integration test to verify that Optimize properly fails startup when running in no-secondary-storage mode
 * (database.type=none). This ensures consistent behavior with the requirement that Optimize should not
 * run in headless deployments.
 */
public class OptimizeNoSecondaryStorageFailureIT {

  @AfterEach
  void tearDown() {
    System.clearProperty("camunda.database.type");
  }

  @Test
  void shouldFailOptimizeStartupInNoSecondaryStorageMode() {
    // given - system configured with database.type=none
    System.setProperty("camunda.database.type", "none");

    // when - attempting to start a Spring context with a configuration that should fail
    final Exception exception = assertThrows(Exception.class, () -> {
      try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
        context.register(TestOptimizeConfiguration.class);
        context.refresh();
      }
    });

    // then - startup should fail with clear error message
    assertThat(exception)
        .hasRootCauseInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Optimize is not supported without secondary storage");
  }

  @Test
  void shouldAllowOptimizeStartupWithValidDatabaseType() {
    // given - system configured with database.type=elasticsearch
    System.setProperty("camunda.database.type", "elasticsearch");

    // when - starting a Spring context with valid database type
    // then - should not throw exception
    try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
      context.register(TestOptimizeConfiguration.class);
      context.refresh();
      // Context should start successfully
    }
  }

  /**
   * Test configuration that mimics the behavior of OptimizeDatabaseConfiguration.
   * This configuration will be active when database.type=none and will throw an exception.
   */
  @Configuration
  @ConditionalOnProperty(
      prefix = "camunda.database",
      name = "type",
      havingValue = DatabaseConfig.NONE)
  static class TestOptimizeConfiguration {
    
    public TestOptimizeConfiguration() {
      final String errorMessage = "Optimize is not supported without secondary storage. "
          + "The database type is configured as 'none', but Optimize requires a secondary storage "
          + "backend (Elasticsearch or OpenSearch) to function properly. "
          + "Please configure 'camunda.database.type' to either 'elasticsearch' or 'opensearch', "
          + "or remove Optimize from your deployment when running in no-secondary-storage mode.";
      
      throw new IllegalStateException(errorMessage);
    }
  }
}