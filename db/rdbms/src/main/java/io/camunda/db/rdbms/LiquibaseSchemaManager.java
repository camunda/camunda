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
import io.camunda.db.rdbms.exception.RdbmsSchemaVersionIndeterminateException;
import io.camunda.zeebe.util.VisibleForTesting;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import javax.sql.DataSource;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.DatabaseException;
import liquibase.integration.spring.SpringLiquibase;
import liquibase.lockservice.LockService;
import liquibase.lockservice.LockServiceFactory;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the RDBMS schema of a single physical tenant using Liquibase.
 *
 * <p>The migration runs against the configured {@link DataSource}, using the configured table
 * prefix and DDL lock-wait timeout.
 *
 * <p>Before applying the migration the schema upgrade path is validated against the running
 * application version by {@link RdbmsSchemaVersionStore}; an illegal upgrade path fails with a
 * {@link RdbmsSchemaVersionIncompatibleException}.
 *
 * <p>{@link #initialize()} is safely re-runnable, which is what lets the caller retry a failed
 * attempt: the runner is rebuilt per call, the stale-lock release is best-effort, the changelog is
 * idempotent and the version record is upserted.
 */
public class LiquibaseSchemaManager implements RdbmsSchemaManager {

  private static final Logger LOG = LoggerFactory.getLogger(LiquibaseSchemaManager.class);
  private static final int DEFAULT_MIGRATION_RETRY_ATTEMPTS = 3;
  private static final Duration DEFAULT_RETRY_BACKOFF = Duration.ofMillis(200);
  private static final String CHANGE_LOG = "db/changelog/rdbms-exporter/changelog-master.xml";

  /**
   * Forces Liquibase runs in this JVM to execute one at a time, because two of them overlapping
   * corrupts each other's lock bookkeeping — even against entirely separate databases.
   *
   * <p>Liquibase ends each update with {@code LockServiceFactory.getInstance().resetAll()} ({@code
   * AbstractUpdateCommandStep#cleanUp}), which discards that factory's JVM-wide singleton along
   * with every database's {@code LockService}. A run that finishes while another is still between
   * acquiring and releasing its lock therefore leaves the second run looking up a factory that no
   * longer knows it holds one: {@code hasChangeLogLock()} reads false, the release is skipped
   * without so much as a log line, and that tenant's {@code DATABASECHANGELOGLOCK} row stays set.
   * The next start then blocks for Liquibase's full changelog-lock wait, fails, and keeps retrying
   * until {@link #releaseStaleLockIfPresent()} force-releases the row a {@code
   * ddl-lock-wait-timeout} later. Liquibase applies the per-database fix this needs to the sibling
   * {@code ChangeLogHistoryServiceFactory} on the very next line of that same method, and documents
   * the hazard in its javadoc; {@code LockServiceFactory} has not been given it (5.0.4).
   *
   * <p>Serializing costs the physical tenants their overlap, not their independence: each still
   * migrates on its own thread, with its own retry loop, and one tenant's failure still degrades
   * only itself. Held interruptibly so that a shutdown does not have to wait out a peer's
   * migration.
   *
   * <p>Meant to be temporary: it can go as soon as Liquibase discards that factory per database
   * rather than JVM-wide. See #61009 for the analysis and the upstream state.
   */
  private static final ReentrantLock MIGRATION_LOCK = new ReentrantLock(true);

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
   * missing value causes startup to be aborted with an {@link
   * RdbmsSchemaVersionIndeterminateException}.
   */
  private final String applicationVersion;

  private final RdbmsSchemaVersionStore versionStore;

  /** Reads "now"; replaced in tests so probe spacing can be asserted without waiting for it. */
  private final Supplier<Instant> clock;

  /**
   * The earliest instant the stale-lock probe may run again, or null before the first probe, which
   * always runs. Derived from the locks the last probe saw rather than from when it ran, so a lock
   * that has just become stale is not made to wait another {@code ddl-lock-wait-timeout}.
   */
  private volatile @Nullable Instant nextStaleLockProbe;

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
    this(config, applicationVersion, versionStore, Instant::now);
  }

  @VisibleForTesting
  LiquibaseSchemaManager(
      final PerTenantSchemaConfig config,
      final String applicationVersion,
      final RdbmsSchemaVersionStore versionStore,
      final Supplier<Instant> clock) {
    this.clock = clock;
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
      throw new RdbmsSchemaVersionIndeterminateException(
          "[RDBMS Schema] applicationVersion is not configured.");
    }
    LOG.info("[RDBMS Schema] Running Liquibase migration with prefix '{}'.", prefix);
    final var runner = buildRunner();
    releaseStaleLockIfPresent();
    versionStore.checkCompatibility();
    performMigrationWithRetry(runner);
    versionStore.recordCurrentVersion();
    LOG.debug("[RDBMS Schema] Liquibase migration completed for prefix '{}'.", prefix);
  }

  /**
   * Whether the stale-lock probe may run again. Lock age is only a proxy for "the holder died": a
   * peer whose migration legitimately runs longer than {@code ddl-lock-wait-timeout} looks exactly
   * like a crashed one. That was tolerable while {@link #initialize()} ran once per node start, but
   * it is now also driven by a per-tenant retry loop that calls it every few seconds — which would
   * force-release a live peer's lock over and over and let two changelog runs execute against one
   * schema.
   */
  private boolean staleLockProbeIsDue() {
    final var nextProbe = nextStaleLockProbe;
    return nextProbe == null || !nextProbe.isAfter(clock.get());
  }

  /**
   * Spaces the next probe by what the last one saw, not by when it ran. A lock that was seen and is
   * not yet stale becomes stale at {@code lockGranted + ddl-lock-wait-timeout}, and that is when it
   * is worth looking again — spacing from the probe instead would leave a peer that crashed just
   * after being observed holding its lock for up to twice the timeout. With no lock left to watch,
   * nothing acquired from now on can be stale before a full timeout has passed either way.
   *
   * <p>A probe that could not reach the database does not call this at all: it released nothing, so
   * spacing it would hold off the release for a tenant whose database has just come back and whose
   * peer left a stale lock behind — the recovery this whole path exists for.
   *
   * @param oldestLiveLock the oldest lock seen that was not stale, or null if none was left behind
   */
  private void scheduleNextStaleLockProbe(
      final Instant probedAt, final @Nullable Instant oldestLiveLock) {
    nextStaleLockProbe =
        (oldestLiveLock == null ? probedAt : oldestLiveLock).plus(ddlLockWaitTimeout);
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
    MIGRATION_LOCK.lockInterruptibly();
    try {
      runner.afterPropertiesSet();
    } finally {
      MIGRATION_LOCK.unlock();
    }
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
    if (!staleLockProbeIsDue()) {
      return;
    }
    try (final var connection = dataSource.getConnection()) {
      final var database = openDatabase(connection, prefix + "DATABASECHANGELOGLOCK");
      try {
        final var lockService = getLockService(database);
        final var probedAt = clock.get();
        final var threshold = probedAt.minus(ddlLockWaitTimeout);
        Instant oldestLiveLock = null;
        for (final var lock : lockService.listLocks()) {
          if (lock.getLockGranted() == null) {
            continue;
          }
          final var grantedAt = lock.getLockGranted().toInstant();
          if (grantedAt.isBefore(threshold)) {
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
            oldestLiveLock = null;
            break;
          }
          if (oldestLiveLock == null || grantedAt.isBefore(oldestLiveLock)) {
            oldestLiveLock = grantedAt;
          }
        }
        scheduleNextStaleLockProbe(probedAt, oldestLiveLock);
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
