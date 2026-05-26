/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.replication;

import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import java.sql.SQLException;
import javax.sql.DataSource;

/**
 * Detects whether the configured database is Aurora, using vendor-family-specific capability
 * probes.
 *
 * <p>Aurora PostgreSQL and Aurora MySQL expose different Aurora-specific functions. We probe using
 * {@code aurora_version()} on PostgreSQL and {@code AURORA_VERSION()} on MySQL, treating the
 * vendor-specific "function does not exist" error as a negative result and surfacing all other SQL
 * failures.
 */
public final class AuroraDatabaseDetector {

  static final String POSTGRESQL_DATABASE_ID = "postgresql";
  static final String MYSQL_DATABASE_ID = "mysql";

  private static final String POSTGRESQL_AURORA_DETECTION_SQL = "SELECT aurora_version()";
  private static final String MYSQL_AURORA_DETECTION_SQL = "SELECT AURORA_VERSION()";
  private static final String POSTGRESQL_UNDEFINED_FUNCTION_SQL_STATE = "42883";
  private static final String MYSQL_FUNCTION_DOES_NOT_EXIST_SQL_STATE = "42000";
  private static final int MYSQL_FUNCTION_DOES_NOT_EXIST_ERROR_CODE = 1305;

  private final DataSource dataSource;
  private final VendorDatabaseProperties vendorDatabaseProperties;

  public AuroraDatabaseDetector(
      final DataSource dataSource, final VendorDatabaseProperties vendorDatabaseProperties) {
    this.dataSource = dataSource;
    this.vendorDatabaseProperties = vendorDatabaseProperties;
  }

  public boolean isAurora() {
    final var databaseId = vendorDatabaseProperties.databaseId();
    return switch (databaseId) {
      case POSTGRESQL_DATABASE_ID -> executeProbe(POSTGRESQL_AURORA_DETECTION_SQL);
      case MYSQL_DATABASE_ID -> executeProbe(MYSQL_AURORA_DETECTION_SQL);
      case null ->
          throw new IllegalArgumentException("Cannot detect Aurora database for null database id");
      default -> false;
    };
  }

  private boolean executeProbe(final String sql) {
    try (final var connection = dataSource.getConnection();
        final var statement = connection.createStatement();
        final var resultSet = statement.executeQuery(sql)) {
      return resultSet.next();
    } catch (final SQLException e) {
      if (isFunctionDoesNotExist(vendorDatabaseProperties.databaseId(), e)) {
        return false;
      }

      throw new IllegalStateException(
          "Failed to detect Aurora database for database id "
              + vendorDatabaseProperties.databaseId(),
          e);
    }
  }

  private boolean isFunctionDoesNotExist(final String databaseId, final SQLException exception) {
    return switch (databaseId) {
      case POSTGRESQL_DATABASE_ID ->
          POSTGRESQL_UNDEFINED_FUNCTION_SQL_STATE.equals(exception.getSQLState());
      case MYSQL_DATABASE_ID ->
          MYSQL_FUNCTION_DOES_NOT_EXIST_SQL_STATE.equals(exception.getSQLState())
              && exception.getErrorCode() == MYSQL_FUNCTION_DOES_NOT_EXIST_ERROR_CODE;
      default -> false;
    };
  }
}
