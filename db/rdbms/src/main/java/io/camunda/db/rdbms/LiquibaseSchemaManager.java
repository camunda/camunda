/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms;

import io.camunda.db.rdbms.exception.RdbmsSchemaVersionIncompatibleException;
import io.camunda.zeebe.util.SemanticVersion;
import io.camunda.zeebe.util.VisibleForTesting;
import io.camunda.zeebe.util.migration.VersionCompatibilityCheck;
import io.camunda.zeebe.util.migration.VersionCompatibilityCheck.CheckResult.Compatible;
import io.camunda.zeebe.util.migration.VersionCompatibilityCheck.CheckResult.Incompatible;
import io.camunda.zeebe.util.migration.VersionCompatibilityCheck.CheckResult.Indeterminate;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
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
 *
 * <p>When auto-DDL is enabled (i.e. this schema manager is active), the upgrade path from the
 * stored schema version to the running application version is validated before applying Liquibase
 * migrations. Only same-minor or next-minor upgrades are permitted (e.g. 8.9.x → 8.9.y or 8.9.x →
 * 8.10.y). Skipping minor versions (e.g. 8.9.x → 8.11.y) is not supported and will cause startup to
 * fail with a {@link RdbmsSchemaVersionIncompatibleException}.
 *
 * <p>When auto-DDL is disabled ({@code NoopSchemaManager}), no version check is performed.
 */
public class LiquibaseSchemaManager extends MultiTenantSpringLiquibase
    implements RdbmsSchemaManager {

  /**
   * The schema version that is inferred when the {@code RDBMS_SCHEMA_VERSION} table does not yet
   * exist but the {@code EXPORTER_POSITION} table is present. This indicates an existing database
   * that was created before version tracking was introduced (i.e. a 8.9.x database).
   */
  protected static final String INFERRED_PRE_VERSIONING_SCHEMA_VERSION = "8.9.0";

  private static final Logger LOG = LoggerFactory.getLogger(LiquibaseSchemaManager.class);
  private static final int DEFAULT_MIGRATION_RETRY_ATTEMPTS = 3;
  private static final Duration DEFAULT_RETRY_BACKOFF = Duration.ofMillis(200);

  /**
   * The table that tracks the RDBMS schema version applied by this application. An entry is
   * written/updated after every successful Liquibase migration run.
   */
  private static final String SCHEMA_VERSION_TABLE = "RDBMS_SCHEMA_VERSION";

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

  /**
   * The current application version, injected at startup. Used to validate the upgrade path from
   * the stored schema version. Must not be {@code null}; a missing value causes startup to be
   * aborted with an {@link IllegalStateException}.
   */
  private String applicationVersion;

  @Override
  public void afterPropertiesSet() throws Exception {
    releaseStaleLockIfPresent();
    checkSchemaVersionCompatibility();
    performMigrationWithRetry();
    updateSchemaVersion();
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

  public String getApplicationVersion() {
    return applicationVersion;
  }

  public void setApplicationVersion(final String applicationVersion) {
    this.applicationVersion = applicationVersion;
  }

  public Duration getDdlLockWaitTimeout() {
    return ddlLockWaitTimeout;
  }

  public void setDdlLockWaitTimeout(final Duration ddlLockWaitTimeout) {
    this.ddlLockWaitTimeout = ddlLockWaitTimeout;
  }

  /**
   * Checks the schema version stored in {@code RDBMS_SCHEMA_VERSION} against the running
   * application version and enforces that minor versions are not skipped.
   *
   * <p>Logic:
   *
   * <ol>
   *   <li>If {@link #applicationVersion} is {@code null}, startup is aborted with an {@link
   *       IllegalStateException}.
   *   <li>If {@link #getDataSource()} returns {@code null}, startup is aborted with an {@link
   *       IllegalStateException}.
   *   <li>If the {@code RDBMS_SCHEMA_VERSION} table does not exist or contains no row (fresh DB or
   *       pre-versioning database):
   *       <ul>
   *         <li>If {@code EXPORTER_POSITION} table exists → infer schema version as {@link
   *             #INFERRED_PRE_VERSIONING_SCHEMA_VERSION} (an existing 8.9.x database).
   *         <li>Otherwise → fresh database; skip the check entirely.
   *       </ul>
   *   <li>Validates the transition using {@link VersionCompatibilityCheck}. Only a {@link
   *       Compatible} result allows startup to continue. An {@link Incompatible} result throws a
   *       {@link RdbmsSchemaVersionIncompatibleException}. An {@link Indeterminate} result (e.g.
   *       the stored schema version is not a valid semantic version) aborts startup with an {@link
   *       IllegalStateException}.
   *   <li>Any unexpected error (e.g. a DB connection failure) causes startup to fail with an {@link
   *       IllegalStateException}.
   * </ol>
   */
  protected void checkSchemaVersionCompatibility() {
    if (applicationVersion == null) {
      throw new IllegalStateException("[RDBMS Schema] applicationVersion is not configured.");
    }
    if (getDataSource() == null) {
      throw new IllegalStateException("[RDBMS Schema] dataSource is not configured.");
    }

    try (final var connection = getDataSource().getConnection()) {
      final var currentSchemaVersion = resolveCurrentSchemaVersion(connection);
      if (currentSchemaVersion == null) {
        // Fresh database – no version check needed.
        return;
      }

      final var stableAppVersion = toStableVersion(applicationVersion);
      if (stableAppVersion.isEmpty()) {
        LOG.warn(
            "[RDBMS Schema] Cannot parse application version '{}' as a semantic version; "
                + "skipping schema version compatibility check.",
            applicationVersion);
        return;
      }

      final var result =
          VersionCompatibilityCheck.check(currentSchemaVersion, stableAppVersion.get());
      if (result instanceof Compatible) {
        LOG.debug(
            "[RDBMS Schema] Version check passed: schema={}, app={}, result={}",
            currentSchemaVersion,
            stableAppVersion.get(),
            result.getClass().getSimpleName());
      } else if (result instanceof Incompatible) {
        LOG.error(
            "[RDBMS Schema] Illegal upgrade path: schema={}, app={}. "
                + "Upgrade sequentially ({} → next minor). Skipping minors is not supported.",
            currentSchemaVersion,
            stableAppVersion.get(),
            currentSchemaVersion);
        throw new RdbmsSchemaVersionIncompatibleException(
            currentSchemaVersion, stableAppVersion.get());
      } else if (result instanceof Indeterminate) {
        LOG.error(
            "[RDBMS Schema] Cannot determine version compatibility: schema={}, app={}. "
                + "The stored schema version may be invalid. Startup aborted.",
            currentSchemaVersion,
            stableAppVersion.get());
        throw new IllegalStateException(
            "[RDBMS Schema] Cannot determine version compatibility: schema="
                + currentSchemaVersion
                + ", app="
                + stableAppVersion.get()
                + ". The stored schema version may be invalid. Startup aborted.");
      }
    } catch (final RdbmsSchemaVersionIncompatibleException | IllegalStateException e) {
      throw e;
    } catch (final Exception e) {
      LOG.error("[RDBMS Schema] Failed to determine current schema version. Startup aborted.", e);
      throw new IllegalStateException(
          "[RDBMS Schema] Failed to determine current schema version. Startup aborted.", e);
    }
  }

  /**
   * Resolves the current schema version. Returns:
   *
   * <ul>
   *   <li>The version string from {@code RDBMS_SCHEMA_VERSION} if the table exists and has a row.
   *   <li>{@link #INFERRED_PRE_VERSIONING_SCHEMA_VERSION} if the {@code EXPORTER_POSITION} table
   *       exists but {@code RDBMS_SCHEMA_VERSION} does not (existing 8.9.x database).
   *   <li>{@code null} for a completely fresh database (no known tables).
   * </ul>
   */
  @VisibleForTesting
  protected String resolveCurrentSchemaVersion(final Connection connection) throws SQLException {
    final var prefix = getPrefix();
    final var versionFromTable = readSchemaVersion(connection, prefix);
    if (versionFromTable != null) {
      return versionFromTable;
    }

    // No version in table (table may not exist yet). Check for pre-versioning database.
    if (tableExists(connection, prefix + "EXPORTER_POSITION")) {
      LOG.info(
          "[RDBMS Schema] RDBMS_SCHEMA_VERSION table not found but EXPORTER_POSITION exists. "
              + "Inferring schema version as {} (pre-versioning database).",
          INFERRED_PRE_VERSIONING_SCHEMA_VERSION);
      return INFERRED_PRE_VERSIONING_SCHEMA_VERSION;
    }

    // Fresh database.
    return null;
  }

  /**
   * Reads the schema version from {@code RDBMS_SCHEMA_VERSION}. Returns {@code null} if the table
   * does not exist or contains no rows. Propagates any unexpected {@link SQLException}.
   */
  @VisibleForTesting
  protected String readSchemaVersion(final Connection connection, final String prefix)
      throws SQLException {
    final var tableName = prefix + SCHEMA_VERSION_TABLE;
    if (!tableExists(connection, tableName)) {
      return null;
    }
    try (final var stmt = connection.prepareStatement("SELECT VERSION FROM " + tableName)) {
      stmt.setMaxRows(1);
      try (final var rs = stmt.executeQuery()) {
        return rs.next() ? rs.getString(1) : null;
      }
    }
  }

  /**
   * Checks whether the given table exists in the database. Propagates any {@link SQLException} that
   * is not a simple "table not found" condition so that unexpected errors (e.g. permission
   * failures, broken connections) abort startup instead of being silently treated as a missing
   * table.
   */
  @VisibleForTesting
  protected boolean tableExists(final Connection connection, final String tableName)
      throws SQLException {
    final var meta = connection.getMetaData();
    // Try uppercase first (most databases store identifiers in upper case), then as-is.
    try (final var rs =
        meta.getTables(null, null, tableName.toUpperCase(), new String[] {"TABLE"})) {
      if (rs.next()) {
        return true;
      }
    }
    try (final var rs = meta.getTables(null, null, tableName, new String[] {"TABLE"})) {
      return rs.next();
    }
  }

  /**
   * Upserts the current application version into {@code RDBMS_SCHEMA_VERSION} after a successful
   * Liquibase migration. The version is normalized to stable {@code major.minor.patch} before
   * storage (pre-release suffixes such as {@code -SNAPSHOT} are stripped). If the version cannot be
   * parsed as a semantic version (e.g. {@code "development"}), the write is skipped with a warning.
   * Any failure aborts startup with an {@link IllegalStateException} because a missing or incorrect
   * schema-version record would cause the next startup to perform an incorrect compatibility check.
   */
  protected void updateSchemaVersion() {
    if (applicationVersion == null || getDataSource() == null) {
      return;
    }

    final var stableVersion = toStableVersion(applicationVersion);
    if (stableVersion.isEmpty()) {
      LOG.warn(
          "[RDBMS Schema] Cannot parse application version '{}' as a semantic version; "
              + "skipping schema version storage.",
          applicationVersion);
      return;
    }

    final var prefix = getPrefix();
    final var tableName = prefix + SCHEMA_VERSION_TABLE;

    try (final var connection = getDataSource().getConnection()) {
      final var autoCommit = connection.getAutoCommit();
      connection.setAutoCommit(false);
      try {
        upsertSingleSchemaVersionRow(connection, tableName, stableVersion.get());
        connection.commit();
        LOG.debug("[RDBMS Schema] Updated schema version to {}.", stableVersion.get());
      } catch (final SQLException e) {
        connection.rollback();
        throw e;
      } finally {
        connection.setAutoCommit(autoCommit);
      }
    } catch (final Exception e) {
      LOG.error(
          "[RDBMS Schema] Failed to update schema version in {}. Startup aborted.", tableName, e);
      throw new IllegalStateException(
          "[RDBMS Schema] Failed to update schema version in " + tableName + ". Startup aborted.",
          e);
    }
  }

  private void upsertSingleSchemaVersionRow(
      final Connection connection, final String tableName, final String stableVersion)
      throws SQLException {
    if (updateSchemaVersionById(connection, tableName, stableVersion) > 0) {
      return;
    }

    try {
      insertSchemaVersionById(connection, tableName, stableVersion);
    } catch (final SQLException insertException) {
      if (updateSchemaVersionById(connection, tableName, stableVersion) == 0) {
        throw insertException;
      }
    }
  }

  private int updateSchemaVersionById(
      final Connection connection, final String tableName, final String stableVersion)
      throws SQLException {
    try (final var updateStmt =
        connection.prepareStatement("UPDATE " + tableName + " SET VERSION = ? WHERE ID = 1")) {
      updateStmt.setString(1, stableVersion);
      return updateStmt.executeUpdate();
    }
  }

  private void insertSchemaVersionById(
      final Connection connection, final String tableName, final String stableVersion)
      throws SQLException {
    try (final var insertStmt =
        connection.prepareStatement("INSERT INTO " + tableName + " (ID, VERSION) VALUES (1, ?)")) {
      insertStmt.setString(1, stableVersion);
      insertStmt.executeUpdate();
    }
  }

  private SchemaVersionState readSchemaVersionState(
      final Connection connection, final String tableName) throws SQLException {
    var rowCount = 0;
    String version = null;

    try (final var selectStmt = connection.prepareStatement("SELECT VERSION FROM " + tableName);
        final var resultSet = selectStmt.executeQuery()) {
      while (resultSet.next()) {
        rowCount++;
        if (rowCount == 1) {
          version = resultSet.getString(1);
        }
      }
    }

    return new SchemaVersionState(rowCount, version);
  }

  private void insertSchemaVersion(
      final Connection connection, final String tableName, final String stableVersion)
      throws SQLException {
    try (final var insertStmt =
        connection.prepareStatement("INSERT INTO " + tableName + " (VERSION) VALUES (?)")) {
      insertStmt.setString(1, stableVersion);
      insertStmt.executeUpdate();
    }
  }

  private String getPrefix() {
    final var params = getParameters();
    return params != null ? params.getOrDefault("prefix", "") : "";
  }

  /**
   * Normalizes {@code version} to a stable {@code major.minor.patch} string by stripping any
   * pre-release or build-metadata suffix (e.g. {@code 8.11.0-SNAPSHOT} → {@code 8.11.0}).
   *
   * @return the stable version string, or {@link Optional#empty()} if {@code version} cannot be
   *     parsed as a semantic version (e.g. {@code "development"})
   */
  @VisibleForTesting
  protected static Optional<String> toStableVersion(final String version) {
    return SemanticVersion.parse(version)
        .map(sv -> sv.major() + "." + sv.minor() + "." + sv.patch());
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

  private record SchemaVersionState(int rowCount, String version) {}
}
