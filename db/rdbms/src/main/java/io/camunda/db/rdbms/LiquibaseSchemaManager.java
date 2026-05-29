/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms;

import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.exception.RdbmsSchemaVersionIncompatibleException;
import io.camunda.zeebe.util.VisibleForTesting;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.DatabaseException;
import liquibase.integration.spring.SpringLiquibase;
import liquibase.lockservice.LockService;
import liquibase.lockservice.LockServiceFactory;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the RDBMS schema of a single physical tenant using Liquibase.
 *
 * <p>The migration runs against the configured {@link DataSource}, using the configured table
 * prefix and DDL lock-wait timeout.
 *
 * <p>Before applying the migration the schema upgrade path is validated against the running
 * application version by {@link RdbmsSchemaVersionStore}; an illegal upgrade path causes startup to
 * fail with a {@link RdbmsSchemaVersionIncompatibleException}.
 */
public class LiquibaseSchemaManager implements RdbmsSchemaManager {

  private static final Logger LOG = LoggerFactory.getLogger(LiquibaseSchemaManager.class);
  private static final int DEFAULT_MIGRATION_RETRY_ATTEMPTS = 3;
  private static final Duration DEFAULT_RETRY_BACKOFF = Duration.ofMillis(200);
  private static final String CHANGE_LOG = "db/changelog/rdbms-exporter/changelog-master.xml";

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

  private final DataSource dataSource;
  private final VendorDatabaseProperties vendorDatabaseProperties;
  private final String prefix;
  private final Duration ddlLockWaitTimeout;

  /**
   * The current application version, supplied at construction time. Must not be {@code null}; a
   * missing value causes startup to be aborted with an {@link IllegalStateException}.
   */
  private final String applicationVersion;

  private final RdbmsSchemaVersionStore versionStore;

  private volatile boolean initialized = false;

  public LiquibaseSchemaManager(
      final PerTenantSchemaConfig config, final String applicationVersion) {
    this(
        config,
        applicationVersion,
        new RdbmsSchemaVersionStore(
            config.dataSource(), StringUtils.trimToEmpty(config.prefix()), applicationVersion));
  }

  @VisibleForTesting
  LiquibaseSchemaManager(
      final PerTenantSchemaConfig config,
      final String applicationVersion,
      final RdbmsSchemaVersionStore versionStore) {
    dataSource = config.dataSource();
    vendorDatabaseProperties = config.vendorDatabaseProperties();
    prefix = StringUtils.trimToEmpty(config.prefix());
    ddlLockWaitTimeout = config.ddlLockWaitTimeout();
    this.applicationVersion = applicationVersion;
    this.versionStore = versionStore;
  }

  @Override
  public void initialize() throws Exception {
    if (applicationVersion == null) {
      throw new IllegalStateException("[RDBMS Schema] applicationVersion is not configured.");
    }
    LOG.info("[RDBMS Schema] Running Liquibase migration with prefix '{}'.", prefix);
    final var runner = buildRunner();
    releaseStaleLockIfPresent();
    versionStore.checkCompatibility();
    performMigrationWithRetry(runner);
    versionStore.recordCurrentVersion();
    initialized = true;
    LOG.debug("[RDBMS Schema] Liquibase migration completed for prefix '{}'.", prefix);
  }

  @Override
  public boolean isInitialized() {
    return initialized;
  }

  @VisibleForTesting
  protected SpringLiquibase buildRunner() {
    final var runner = new SpringLiquibase();
    runner.setDataSource(dataSource);
    runner.setChangeLog(CHANGE_LOG);
    runner.setDatabaseChangeLogTable(prefix + "DATABASECHANGELOG");
    runner.setDatabaseChangeLogLockTable(prefix + "DATABASECHANGELOGLOCK");
    runner.setChangeLogParameters(
        Map.of(
            "prefix", prefix,
            "userCharColumnSize", Integer.toString(vendorDatabaseProperties.userCharColumnSize()),
            "errorMessageSize", Integer.toString(vendorDatabaseProperties.errorMessageSize()),
            "treePathSize", Integer.toString(vendorDatabaseProperties.treePathSize())));
    return runner;
  }

  /**
   * Runs the Liquibase migration with bounded retries for transient, retryable failures. In CI,
   * tests run concurrently with unique table prefixes, causing Liquibase to run multiple migrations
   * in parallel against the same database, which can trigger transient errors such as deadlocks.
   */
  protected void performMigrationWithRetry(final SpringLiquibase runner) throws Exception {
    var retryBackoff = DEFAULT_RETRY_BACKOFF;

    for (int attempt = 1; attempt <= DEFAULT_MIGRATION_RETRY_ATTEMPTS; attempt++) {
      try {
        performMigration(runner);
        return;
      } catch (final Exception e) {
        final boolean shouldRetry =
            isRetryableException(e) && attempt < DEFAULT_MIGRATION_RETRY_ATTEMPTS;
        if (!shouldRetry) {
          throw e;
        }

        LOG.warn(
            "[RDBMS Schema] Liquibase migration for prefix '{}' failed due to a transient, retryable "
                + "error (attempt {}/{}). Retrying in {}.",
            prefix,
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
  protected void performMigration(final SpringLiquibase runner) throws Exception {
    runner.afterPropertiesSet();
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

  /**
   * Checks for a stale Liquibase lock and forcibly releases it if it is older than the configured
   * DDL lock wait timeout. This allows recovery from container crashes that left the schema locked
   * without being properly cleaned up.
   *
   * <p>If the DDL lock wait timeout is {@code null}, the data source is {@code null}, or the lock
   * table does not exist yet (first run), this method does nothing.
   */
  protected void releaseStaleLockIfPresent() {
    if (ddlLockWaitTimeout == null || dataSource == null) {
      return;
    }
    try (final var connection = dataSource.getConnection()) {
      final var database = openDatabase(connection, prefix + "DATABASECHANGELOGLOCK");
      try {
        final var lockService = getLockService(database);
        final var threshold = Instant.now().minus(ddlLockWaitTimeout);
        for (final var lock : lockService.listLocks()) {
          if (lock.getLockGranted() != null
              && lock.getLockGranted().toInstant().isBefore(threshold)) {
            LOG.warn(
                "[RDBMS Schema] Detected stale Liquibase lock for prefix '{}' acquired at {} by '{}' "
                    + "(older than configured ddl-lock-wait-timeout of {}). Releasing lock to allow "
                    + "migrations to proceed.",
                prefix,
                lock.getLockGranted(),
                lock.getLockedBy(),
                ddlLockWaitTimeout);
            lockService.forceReleaseLock();
            LOG.info(
                "[RDBMS Schema] Stale Liquibase lock released successfully for prefix '{}'.",
                prefix);
            break;
          }
        }
      } finally {
        database.close();
      }
    } catch (final Exception e) {
      LOG.warn(
          "[RDBMS Schema] Failed to check or release stale Liquibase lock for prefix '{}'. "
              + "Proceeding with migration.",
          prefix,
          e);
    }
  }

  /**
   * Creates a Liquibase {@link Database} from the given JDBC connection. Protected to allow
   * overriding in tests.
   */
  protected Database openDatabase(final Connection connection, final String lockTableName)
      throws DatabaseException {
    final var database =
        DatabaseFactory.getInstance()
            .findCorrectDatabaseImplementation(new JdbcConnection(connection));
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
