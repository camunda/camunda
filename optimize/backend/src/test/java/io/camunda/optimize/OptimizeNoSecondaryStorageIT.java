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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.security.Permission;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration test for Optimize startup behavior in no-secondary-storage mode.
 * This test verifies the complete startup failure scenario when database.type=none.
 */
class OptimizeNoSecondaryStorageIT {

  private PrintStream originalErr;
  private ByteArrayOutputStream errorOutput;
  private SecurityManager originalSecurityManager;

  @BeforeEach
  void setUp() {
    originalErr = System.err;
    errorOutput = new ByteArrayOutputStream();
    System.setErr(new PrintStream(errorOutput));
    
    // Install a custom security manager to catch System.exit calls
    originalSecurityManager = System.getSecurityManager();
    System.setSecurityManager(new TestSecurityManager());
  }

  @AfterEach
  void tearDown() {
    System.setErr(originalErr);
    System.setSecurityManager(originalSecurityManager);
    System.clearProperty("camunda.database.type");
  }

  @Test
  void shouldFailStartupWithClearErrorWhenDatabaseTypeIsNone() {
    // given
    System.setProperty("camunda.database.type", "none");

    // when
    final SecurityException exitException = assertThrows(SecurityException.class, () -> {
      Main.main(new String[]{});
    });

    // then
    assertThat(exitException.getMessage()).contains("System.exit(1)");
    
    final String errorMessage = errorOutput.toString();
    assertThat(errorMessage).contains("ERROR: Optimize is not supported without secondary storage");
    assertThat(errorMessage).contains("configured as 'none'");
    assertThat(errorMessage).contains("requires a secondary storage backend");
    assertThat(errorMessage).contains("Elasticsearch or OpenSearch");
    assertThat(errorMessage).contains("camunda.database.type");
    assertThat(errorMessage).contains("remove Optimize from your deployment");
  }

  @Test
  void shouldFailStartupWithClearErrorWhenDatabaseTypeIsNoneUpperCase() {
    // given
    System.setProperty("camunda.database.type", "NONE");

    // when
    final SecurityException exitException = assertThrows(SecurityException.class, () -> {
      Main.main(new String[]{});
    });

    // then
    assertThat(exitException.getMessage()).contains("System.exit(1)");
    
    final String errorMessage = errorOutput.toString();
    assertThat(errorMessage).contains("ERROR: Optimize is not supported without secondary storage");
  }

  /**
   * Custom SecurityManager that prevents System.exit() calls and throws SecurityException instead.
   * This allows us to test the System.exit() behavior in unit tests.
   */
  private static class TestSecurityManager extends SecurityManager {
    @Override
    public void checkExit(int status) {
      throw new SecurityException("System.exit(" + status + ")");
    }
    
    @Override
    public void checkPermission(Permission perm) {
      // Allow all other permissions
    }
  }
}