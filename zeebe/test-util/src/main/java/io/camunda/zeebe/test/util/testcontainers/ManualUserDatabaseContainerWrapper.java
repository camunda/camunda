/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.testcontainers;

import static io.camunda.zeebe.test.util.testcontainers.TestSearchContainers.CAMUNDA_MANUAL_DATABASE;
import static io.camunda.zeebe.test.util.testcontainers.TestSearchContainers.CAMUNDA_MANUAL_PASSWORD;
import static io.camunda.zeebe.test.util.testcontainers.TestSearchContainers.CAMUNDA_MANUAL_USER;

import org.testcontainers.containers.JdbcDatabaseContainer;

/**
 * Wrapper for JDBC database containers that provides manual user credentials. This is used to test
 * Camunda with database users that have restricted privileges, simulating production-like setups.
 *
 * <p>Note: This class uses string matching on container class names to avoid adding dependencies
 * on all testcontainers database modules. The passwords are hardcoded as this is only for testing
 * purposes.
 */
public final class ManualUserDatabaseContainerWrapper {

  private ManualUserDatabaseContainerWrapper() {}

  /**
   * Returns the JDBC URL for connecting with the manual user. This connects to the camunda_manual
   * database.
   */
  public static String getJdbcUrl(final JdbcDatabaseContainer<?> container) {
    final String className = container.getClass().getName();
    final String originalUrl = container.getJdbcUrl();

    if (className.contains("PostgreSQL")) {
      // Replace /test or /camunda with /camunda_manual
      return originalUrl.replaceFirst("/[^/]+$", "/" + CAMUNDA_MANUAL_DATABASE);
    } else if (className.contains("MySQL") || className.contains("MariaDB")) {
      // Replace /test or /camunda with /camunda_manual
      return originalUrl.replaceFirst("/[^?]+", "/" + CAMUNDA_MANUAL_DATABASE);
    } else if (className.contains("Oracle")) {
      // Oracle: connect to the camunda_user schema
      return originalUrl;
    } else if (className.contains("MSSQL")) {
      // For MSSQL, replace the database parameter
      if (originalUrl.contains("database=")) {
        return originalUrl.replaceFirst("database=[^;]+", "database=" + CAMUNDA_MANUAL_DATABASE);
      } else {
        return originalUrl + ";database=" + CAMUNDA_MANUAL_DATABASE;
      }
    }
    return originalUrl;
  }

  /**
   * Returns the username for the manual user with restricted privileges.
   */
  public static String getUsername(final JdbcDatabaseContainer<?> container) {
    return CAMUNDA_MANUAL_USER;
  }

  /**
   * Returns the password for the manual user with restricted privileges.
   */
  public static String getPassword(final JdbcDatabaseContainer<?> container) {
    return CAMUNDA_MANUAL_PASSWORD;
  }
}
