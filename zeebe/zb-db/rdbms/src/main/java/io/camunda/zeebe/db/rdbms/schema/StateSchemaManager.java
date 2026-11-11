/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.rdbms.schema;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates and updates the tables that hold the partition state, by running the Liquibase changelog
 * {@link #CHANGE_LOG}.
 *
 * <p>Every table name starts with the configured prefix, so several clusters can share a database
 * schema. This includes Liquibase's own tracking tables, {@code
 * <prefix>ZEEBE_STATE_DATABASECHANGELOG} and {@code <prefix>ZEEBE_STATE_DATABASECHANGELOGLOCK}, so
 * they never mix with the history schema's, which may live in the same database schema.
 *
 * <p>It uses Liquibase directly rather than through Spring, the same way {@code SpringLiquibase}
 * does, because the broker doesn't run this module in a Spring context.
 */
public final class StateSchemaManager {

  public static final String CHANGE_LOG = "db/changelog/zeebe-state/changelog-master.xml";

  private static final Logger LOG = LoggerFactory.getLogger(StateSchemaManager.class);

  /**
   * Makes our migrations in this JVM run one at a time. Liquibase's lock bookkeeping is shared by
   * the whole JVM, so two runs that overlap can leave a lock row set, even against different
   * databases. See {@code LiquibaseSchemaManager#MIGRATION_LOCK} in {@code db/rdbms} and #61009.
   *
   * <p>This doesn't stop our migration from overlapping with the history schema's, which takes a
   * lock of its own.
   */
  private static final ReentrantLock MIGRATION_LOCK = new ReentrantLock(true);

  private final DataSource dataSource;
  private final String prefix;

  public StateSchemaManager(final DataSource dataSource, final String prefix) {
    this.dataSource = Objects.requireNonNull(dataSource, "must specify a data source");
    this.prefix = Objects.requireNonNull(prefix, "must specify a prefix, which may be empty");
  }

  /**
   * Applies every change set of {@link #CHANGE_LOG} that this database doesn't have yet. Change
   * sets that were applied before are skipped, so it's safe to call on every start.
   *
   * @throws StateSchemaMigrationException if the migration fails, or the thread is interrupted
   *     while waiting for another migration to finish
   */
  public void migrate() {
    try {
      MIGRATION_LOCK.lockInterruptibly();
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new StateSchemaMigrationException(
          "Interrupted while waiting to migrate the state schema with prefix '%s'"
              .formatted(prefix),
          e);
    }

    try {
      LOG.info("Migrating the state schema with prefix '{}'", prefix);
      Scope.child(scopeValues(), this::update);
    } catch (final Exception e) {
      throw new StateSchemaMigrationException(
          "Failed to migrate the state schema with prefix '%s'".formatted(prefix), e);
    } finally {
      MIGRATION_LOCK.unlock();
    }
  }

  private void update() throws LiquibaseException, SQLException {
    final var database = openDatabase();
    database.setDatabaseChangeLogTableName(prefix + "ZEEBE_STATE_DATABASECHANGELOG");
    database.setDatabaseChangeLogLockTableName(prefix + "ZEEBE_STATE_DATABASECHANGELOGLOCK");

    // closing it closes the database and its connection
    try (final var liquibase =
        new Liquibase(
            CHANGE_LOG,
            new ClassLoaderResourceAccessor(StateSchemaManager.class.getClassLoader()),
            database)) {
      liquibase.setChangeLogParameter("prefix", prefix);
      liquibase.update(contexts(), new LabelExpression());
    }
  }

  private Database openDatabase() throws SQLException, DatabaseException {
    final Connection connection = dataSource.getConnection();
    try {
      return DatabaseFactory.getInstance()
          .findCorrectDatabaseImplementation(new JdbcConnection(connection));
    } catch (final DatabaseException | RuntimeException e) {
      connection.close();
      throw e;
    }
  }

  /**
   * Liquibase remembers, for the whole JVM, which changelogs are up to date on which database URL
   * and contexts, but not which tables. Without a context per prefix, once one prefix is found up
   * to date, Liquibase would skip updating another prefix of the same database that is behind, for
   * example after an upgrade.
   */
  private Contexts contexts() {
    return prefix.isBlank() ? new Contexts() : new Contexts(prefix + "CONTEXT");
  }

  /**
   * Turns off Liquibase's usage analytics, which otherwise loads its settings from Liquibase's
   * servers, and sends Liquibase's console output to its log instead.
   */
  private static Map<String, Object> scopeValues() {
    return Map.of(
        AnalyticsArgs.ENABLED.getKey(), false, Scope.Attr.ui.name(), new LoggerUIService());
  }
}
