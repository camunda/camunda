/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms;

import io.camunda.zeebe.util.VisibleForTesting;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.DatabaseException;
import liquibase.integration.spring.MultiTenantSpringLiquibase;
import liquibase.lockservice.LockService;
import liquibase.lockservice.LockServiceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the database schema using Liquibase for multi-tenant applications.
 *
 * <p>This class extends {@link MultiTenantSpringLiquibase} to leverage its capabilities for
 * managing database migrations in a multi-tenant environment. It also implements the {@link
 * RdbmsSchemaManager} interface to provide a method for checking if the schema has been
 * initialized.
 */
public class LiquibaseSchemaManager extends MultiTenantSpringLiquibase
    implements RdbmsSchemaManager {

  private static final Logger LOG = LoggerFactory.getLogger(LiquibaseSchemaManager.class);
  private static final int DEFAULT_MIGRATION_RETRY_ATTEMPTS = 3;
  private static final Duration DEFAULT_RETRY_BACKOFF = Duration.ofMillis(200);

  private static final Set<String> RETRYABLE_MESSAGES =
      Set.of(
          "deadlock" // MSSQL causes deadlocks in CI with parallel tests #50230
          );
  private static final Set<Integer> RETRYABLE_SQL_ERROR_CODES =
      Set.of(
          1205 // MSSQL deadlock victim #50230
          );
  private static final Set<String> RETRYABLE_SQL_STATES =
      Set.of(
          "40001" // transaction serialization failure #50230
          );

  private volatile boolean initialized = false;
  private Duration ddlLockWaitTimeout;

  @Override
  public void afterPropertiesSet() throws Exception {
    releaseStaleLockIfPresent();
    setContexts("!async");
    performMigrationWithRetry();
    initialized = true;
    LOG.debug("Liquibase migrations completed.");
  }

  /**
   * Runs the Liquibase migration with bounded retries for transient, retryable failures. In CI,
   * tests run concurrently with unique table prefixes, causing Liquibase to run multiple migrations
   * in parallel against the same database, which can trigger transient errors such as deadlocks.
   */
  protected void performMigrationWithRetry() throws Exception {
    var retryBackoff = DEFAULT_RETRY_BACKOFF;

    for (int attempt = 1; attempt <= DEFAULT_MIGRATION_RETRY_ATTEMPTS; attempt++) {
      try {
        performMigration();
        return;
      } catch (final Exception e) {
        final boolean shouldRetry =
            isRetryableException(e) && attempt < DEFAULT_MIGRATION_RETRY_ATTEMPTS;
        if (!shouldRetry) {
          throw e;
        }

        LOG.warn(
            "Liquibase migration failed due to a transient, retryable error (attempt {}/{}). Retrying in {}.",
            attempt,
            DEFAULT_MIGRATION_RETRY_ATTEMPTS,
            retryBackoff,
            e);

        waitBeforeRetry(retryBackoff);
        retryBackoff = retryBackoff.multipliedBy(2);
      }
    }
  }

  @VisibleForTesting
  protected void performMigration() throws Exception {
    super.afterPropertiesSet();
  }

  protected void waitBeforeRetry(final Duration retryBackoff) throws InterruptedException {
    try {
      Thread.sleep(retryBackoff.toMillis());
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw e;
    }
  }

  private boolean isRetryableException(final Throwable throwable) {
    var current = throwable;
    while (current != null) {
      if (current instanceof final SQLException sqlException
          && (RETRYABLE_SQL_ERROR_CODES.contains(sqlException.getErrorCode())
              || RETRYABLE_SQL_STATES.contains(sqlException.getSQLState()))) {
        return true;
      }

      final var message = current.getMessage();
      if (message != null) {
        final var normalizedMessage = message.toLowerCase();
        if (RETRYABLE_MESSAGES.stream().anyMatch(normalizedMessage::contains)) {
          return true;
        }
      }

      current = current.getCause();
    }
    return false;
  }

  @Override
  public boolean isInitialized() {
    return initialized;
  }

  @Override
  public void migrateAsync() {
    LOG.debug("Start async liquibase migrations.");
    releaseStaleLockIfPresent();
    setContexts("async");
    try {
      performMigrationWithRetry();
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
    setContexts("!async");
    LOG.debug("Async liquibase migrations completed.");
  }

  public Duration getDdlLockWaitTimeout() {
    return ddlLockWaitTimeout;
  }

  public void setDdlLockWaitTimeout(final Duration ddlLockWaitTimeout) {
    this.ddlLockWaitTimeout = ddlLockWaitTimeout;
  }

  /**
   * Checks for stale Liquibase locks and forcibly releases them if they are older than the
   * configured {@link #ddlLockWaitTimeout}. This allows recovery from container crashes that left
   * the schema locked without being properly cleaned up.
   *
   * <p>If {@link #ddlLockWaitTimeout} is {@code null}, or the lock table does not exist yet (first
   * run), this method does nothing.
   */
  protected void releaseStaleLockIfPresent() {
    if (ddlLockWaitTimeout == null || getDataSource() == null) {
      return;
    }
    try (final var connection = getDataSource().getConnection()) {
      final var database = openDatabase(connection);
      try {
        final var lockService = getLockService(database);
        final var threshold = Instant.now().minus(ddlLockWaitTimeout);
        for (final var lock : lockService.listLocks()) {
          if (lock.getLockGranted() != null
              && lock.getLockGranted().toInstant().isBefore(threshold)) {
            LOG.warn(
                "Detected stale Liquibase lock acquired at {} by '{}' (older than configured"
                    + " ddl-lock-wait-timeout of {}). Releasing lock to allow migrations to"
                    + " proceed.",
                lock.getLockGranted(),
                lock.getLockedBy(),
                ddlLockWaitTimeout);
            lockService.forceReleaseLock();
            LOG.info("Stale Liquibase lock released successfully.");
            break;
          }
        }
      } finally {
        database.close();
      }
    } catch (final Exception e) {
      LOG.warn("Failed to check or release stale Liquibase lock. Proceeding with migration.", e);
    }
  }

  /**
   * Creates a Liquibase {@link Database} from the given JDBC connection. Protected to allow
   * overriding in tests.
   */
  protected Database openDatabase(final Connection connection) throws DatabaseException {
    final var database =
        DatabaseFactory.getInstance()
            .findCorrectDatabaseImplementation(new JdbcConnection(connection));
    final var lockTableName = getDatabaseChangeLogLockTable();
    if (lockTableName != null) {
      database.setDatabaseChangeLogLockTableName(lockTableName);
    }
    return database;
  }

  /**
   * Returns the {@link LockService} for the given database. Protected to allow overriding in tests.
   */
  protected LockService getLockService(final Database database) {
    return LockServiceFactory.getInstance().getLockService(database);
  }
}
