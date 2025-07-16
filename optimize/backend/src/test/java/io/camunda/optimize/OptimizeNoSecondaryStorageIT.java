/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;

/**
 * Integration test for Optimize startup behavior in no-secondary-storage mode.
 * This test verifies that the application fails to start when database.type=none.
 */
class OptimizeNoSecondaryStorageIT {

  @AfterEach
  void tearDown() {
    System.clearProperty("camunda.database.type");
  }

  @Test
  void shouldFailStartupWhenDatabaseTypeIsNone() {
    // given
    System.setProperty("camunda.database.type", "none");

    // when
    final Exception exception = assertThrows(Exception.class, () -> {
      Main.main(new String[]{});
    });

    // then - Spring should fail to start due to OptimizeDatabaseConfiguration
    assertThat(exception)
        .hasRootCauseInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Optimize is not supported without secondary storage");
  }

  @Test
  void shouldFailStartupWhenDatabaseTypeIsNoneUpperCase() {
    // given
    System.setProperty("camunda.database.type", "NONE");

    // when
    final Exception exception = assertThrows(Exception.class, () -> {
      Main.main(new String[]{});
    });

    // then - Spring should fail to start due to OptimizeDatabaseConfiguration
    assertThat(exception)
        .hasRootCauseInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Optimize is not supported without secondary storage");
  }
}