/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.persist.rdbms;

import javax.sql.DataSource;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility for running Liquibase database migrations for the auth SDK schema. Suitable for
 * standalone consumers who need to bootstrap the database schema programmatically.
 */
public class AuthDatabaseMigrationRunner {

  private static final Logger LOG = LoggerFactory.getLogger(AuthDatabaseMigrationRunner.class);

  private static final String CHANGELOG_PATH = "db/changelog/auth/auth-changelog-master.xml";

  private final DataSource dataSource;

  public AuthDatabaseMigrationRunner(final DataSource dataSource) {
    this.dataSource = dataSource;
  }

  /**
   * Runs all pending Liquibase changesets against the configured data source.
   *
   * @throws RuntimeException if the migration fails
   */
  public void run() {
    LOG.info("Running auth database migrations from changelog={}", CHANGELOG_PATH);
    try (final var connection = dataSource.getConnection()) {
      final Database database =
          DatabaseFactory.getInstance()
              .findCorrectDatabaseImplementation(new JdbcConnection(connection));
      try (final Liquibase liquibase =
          new Liquibase(CHANGELOG_PATH, new ClassLoaderResourceAccessor(), database)) {
        liquibase.update("");
      }
      LOG.info("Auth database migrations completed successfully");
    } catch (final Exception e) {
      throw new RuntimeException("Failed to run auth database migrations", e);
    }
  }
}
