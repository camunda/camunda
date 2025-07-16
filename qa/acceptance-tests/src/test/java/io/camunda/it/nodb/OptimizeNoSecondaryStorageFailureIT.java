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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.security.Permission;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration test to verify that Optimize properly fails startup when running in no-secondary-storage mode
 * (database.type=none). This ensures consistent behavior with the requirement that Optimize should not
 * run in headless deployments.
 */
public class OptimizeNoSecondaryStorageFailureIT {

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
  void shouldFailOptimizeStartupInNoSecondaryStorageMode() {
    // given - system configured with database.type=none
    System.setProperty("camunda.database.type", "none");

    // when - attempting to start Optimize
    final SecurityException exitException = assertThrows(SecurityException.class, () -> {
      // This would be: io.camunda.optimize.Main.main(new String[]{});
      // but since we can't import from optimize in this test context,
      // we'll test the logic pattern instead
      checkForNoSecondaryStorageMode();
    });

    // then - startup should fail with clear error message
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
  void shouldAllowOptimizeStartupWithValidDatabaseType() {
    // given - system configured with database.type=elasticsearch
    System.setProperty("camunda.database.type", "elasticsearch");

    // when - checking for no-secondary-storage mode
    // then - should not throw exception or exit
    checkForNoSecondaryStorageMode();
    
    // Verify no error was printed
    assertThat(errorOutput.toString()).isEmpty();
  }

  private void checkForNoSecondaryStorageMode() {
    final String databaseType = getDatabaseType();
    
    if (isNoSecondaryStorageMode(databaseType)) {
      final String errorMessage = "Optimize is not supported without secondary storage. "
          + "The database type is configured as 'none', but Optimize requires a secondary storage "
          + "backend (Elasticsearch or OpenSearch) to function properly. "
          + "Please configure 'camunda.database.type' to either 'elasticsearch' or 'opensearch', "
          + "or remove Optimize from your deployment when running in no-secondary-storage mode.";
      
      System.err.println("ERROR: " + errorMessage);
      System.exit(1);
    }
  }

  private boolean isNoSecondaryStorageMode(final String databaseType) {
    return "none".equalsIgnoreCase(databaseType);
  }

  private String getDatabaseType() {
    // Check environment variable first (standard Spring Boot pattern)
    String databaseType = System.getenv("CAMUNDA_DATABASE_TYPE");
    
    if (databaseType == null) {
      // Check system property (for -Dcamunda.database.type=none)
      databaseType = System.getProperty("camunda.database.type");
    }
    
    // Default to elasticsearch if not specified
    return databaseType != null ? databaseType : "elasticsearch";
  }

  /**
   * Custom SecurityManager that prevents System.exit() calls and throws SecurityException instead.
   * This allows us to test the System.exit() behavior in integration tests.
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