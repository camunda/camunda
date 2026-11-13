/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.rdbms;

import java.sql.SQLException;
import java.util.Map;
import javax.sql.DataSource;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.Scope;
import liquibase.analytics.configuration.AnalyticsArgs;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.DatabaseException;
import liquibase.exception.LiquibaseException;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.ui.LoggerUIService;

/**
 * Opens the storage in tests, after creating the state's tables like {@code LiquibaseSchemaManager}
 * does in the broker: the same changelog, tracking tables and prefix context. That manager isn't
 * used here because it would also create every table of the history.
 */
final class TestStorage {

  private static final String CHANGE_LOG = "db/changelog/zeebe-state/changelog-master.xml";

  private TestStorage() {}

  static RdbmsStorage open(final DataSource dataSource, final String prefix) {
    createTables(dataSource, prefix);
    return RdbmsStorage.open(dataSource, prefix);
  }

  /**
   * Creates the tables that the database doesn't have yet. Synchronized, as Liquibase runs that
   * overlap in one JVM can leave a lock row set (#61009).
   */
  static synchronized void createTables(final DataSource dataSource, final String prefix) {
    try {
      Scope.child(
          Map.of(
              AnalyticsArgs.ENABLED.getKey(), false, Scope.Attr.ui.name(), new LoggerUIService()),
          () -> update(dataSource, prefix));
    } catch (final Exception e) {
      throw new IllegalStateException("Failed to create the state tables", e);
    }
  }

  private static void update(final DataSource dataSource, final String prefix)
      throws LiquibaseException, SQLException {
    final var database = openDatabase(dataSource);
    database.setDatabaseChangeLogTableName(prefix + "DATABASECHANGELOG");
    database.setDatabaseChangeLogLockTableName(prefix + "DATABASECHANGELOGLOCK");
    try (final var liquibase =
        new Liquibase(
            CHANGE_LOG,
            new ClassLoaderResourceAccessor(TestStorage.class.getClassLoader()),
            database)) {
      liquibase.setChangeLogParameter("prefix", prefix);
      liquibase.update(
          prefix.isBlank() ? new Contexts() : new Contexts(prefix + "CONTEXT"),
          new LabelExpression());
    }
  }

  private static Database openDatabase(final DataSource dataSource)
      throws SQLException, DatabaseException {
    final var connection = dataSource.getConnection();
    try {
      return DatabaseFactory.getInstance()
          .findCorrectDatabaseImplementation(new JdbcConnection(connection));
    } catch (final DatabaseException | RuntimeException e) {
      connection.close();
      throw e;
    }
  }
}
