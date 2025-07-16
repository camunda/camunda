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

import io.camunda.optimize.Main;
import io.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

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

    // when - attempting to start Optimize
    final Exception exception = assertThrows(Exception.class, () -> {
      Main.main(new String[]{});
    });

    // then - startup should fail with clear error message
    assertThat(exception)
        .hasRootCauseInstanceOf(OptimizeConfigurationException.class)
        .hasMessageContaining("Optimize is not supported without secondary storage");
  }

  @Test
  void shouldAllowOptimizeStartupWithValidDatabaseType() {
    // given - system configured with database.type=elasticsearch
    System.setProperty("camunda.database.type", "elasticsearch");

    // when - starting Optimize with valid database type
    // then - should not throw exception during configuration validation
    // Note: We only test that the configuration service doesn't throw an exception,
    // we don't actually start the full application
    try {
      // This would normally start Spring, but we just want to validate configuration
      // The actual startup would require more infrastructure setup
      Main.main(new String[]{"--spring.main.web-application-type=none", "--spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration"});
    } catch (Exception e) {
      // We expect other exceptions related to missing dependencies, just not configuration errors
      assertThat(e).hasRootCauseMessageNotContaining("Optimize is not supported without secondary storage");
    }
  }
}