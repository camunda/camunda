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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
import org.springframework.beans.factory.InitializingBean;

/**
 * Manages the RDBMS database schema using Liquibase, one schema per physical tenant.
 *
 * <p>For each entry in the supplied {@link PerTenantSchemaConfig} map, a dedicated Liquibase
 * migration is executed against the tenant's own {@link DataSource}, using the tenant's table
 * prefix and DDL lock-wait timeout. Tenants are processed sequentially in iteration order and a
 * failure for any single tenant aborts startup — fail-fast preserves the previous behaviour.
 *
 * <p>When {@code auto-ddl} is disabled for a tenant, the migration is skipped entirely but the
 * tenant is still marked as initialized so that {@link #isInitialized(String)} returns {@code true}
 * and the RDBMS exporter can open against the (externally managed) schema.
 *
 * <p>When auto-DDL is enabled, the upgrade path from the stored schema version to the running
 * application version is validated before applying Liquibase migrations. Only same-minor or
 * next-minor upgrades are permitted (e.g. 8.9.x → 8.9.y or 8.9.x → 8.10.y). Skipping minor versions
 * (e.g. 8.9.x → 8.11.y) is not supported and will cause startup to fail with a {@link
 * RdbmsSchemaVersionIncompatibleException}.
 */
public class LiquibaseSchemaManager implements InitializingBean, RdbmsSchemaManager {

  /**
   * The schema version that is inferred when the {@code RDBMS_SCHEMA_VERSION} table does not yet
   * exist but the {@code EXPORTER_POSITION} table is present. This indicates an existing database
   * that was created before version tracking was introduced (i.e. a 8.9.x database).
   */
  protected static final String INFERRED_PRE_VERSIONING_SCHEMA_VERSION = "8.9.0";

  private static final Logger LOG = LoggerFactory.getLogger(LiquibaseSchemaManager.class);
  private static final int DEFAULT_MIGRATION_RETRY_ATTEMPTS = 3;
  private static final Duration DEFAULT_RETRY_BACKOFF = Duration.ofMillis(200);
  private static final String CHANGE_LOG = "db/changelog/rdbms-exporter/changelog-master.xml";

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

  private final Map<String, PerTenantSchemaConfig> physicalTenantConfigs;

  /**
   * The current application version, supplied at construction time. Used to validate the upgrade
   * path from the stored schema version. Must not be {@code null}; a missing value causes startup
   * to be aborted with an {@link IllegalStateException}.
   */
  private final String applicationVersion;

  private final ConcurrentHashMap<String, Boolean> initialized = new ConcurrentHashMap<>();

  public LiquibaseSchemaManager(
      final Map<String, PerTenantSchemaConfig> physicalTenantConfigs,
      final String applicationVersion) {
    this.physicalTenantConfigs = physicalTenantConfigs;
    this.applicationVersion = applicationVersion;
  }

  @Override
  public void afterPropertiesSet() throws Exception {
    if (applicationVersion == null) {
      throw new IllegalStateException("[RDBMS Schema] applicationVersion is not configured.");
    }
    // Tenants are migrated sequentially, fail-fast on first failure. Parallel execution with
    // per-tenant failure isolation is a deferred follow-up (epic #52027).
    for (final var entry : physicalTenantConfigs.entrySet()) {
      final var physicalTenantId = entry.getKey();
      final var cfg = entry.getValue();
      if (!cfg.autoDdl()) {
        initialized.put(physicalTenantId, true);
        LOG.info(
            "[RDBMS Schema] Skipping Liquibase migration for physical tenant '{}' (auto-ddl=false).",
            physicalTenantId);
        continue;
      }
      final var tenant = buildPerTenant(physicalTenantId, cfg);
      LOG.info(
          "[RDBMS Schema] Running Liquibase migration for physical tenant '{}' with prefix '{}'.",
          physicalTenantId,
          tenant.prefix());
      releaseStaleLockIfPresent(tenant);
      checkSchemaVersionCompatibility(tenant);
      performMigrationWithRetry(tenant);
      updateSchemaVersion(tenant);
      initialized.put(physicalTenantId, true);
      LOG.debug(
          "[RDBMS Schema] Liquibase migration completed for physical tenant '{}'.",
          physicalTenantId);
    }
  }

  @Override
  public boolean isInitialized(final String physicalTenantId) {
    return Boolean.TRUE.equals(initialized.get(physicalTenantId));
  }

  @VisibleForTesting
  protected PerTenantLiquibase buildPerTenant(
      final String physicalTenantId, final PerTenantSchemaConfig cfg) {
    final var prefix = StringUtils.trimToEmpty(cfg.prefix());
    final var vendor = cfg.vendorDatabaseProperties();

    final var runner = new SpringLiquibase();
    runner.setDataSource(cfg.dataSource());
    runner.setChangeLog(CHANGE_LOG);
    runner.setDatabaseChangeLogTable(prefix + "DATABASECHANGELOG");
    runner.setDatabaseChangeLogLockTable(prefix + "DATABASECHANGELOGLOCK");
    runner.setChangeLogParameters(
        Map.of(
            "prefix", prefix,
            "userCharColumnSize", Integer.toString(vendor.userCharColumnSize()),
            "errorMessageSize", Integer.toString(vendor.errorMessageSize()),
            "treePathSize", Integer.toString(vendor.treePathSize())));

    return new PerTenantLiquibase(
        physicalTenantId, runner, cfg.dataSource(), prefix, cfg.ddlLockWaitTimeout());
  }

  /**
   * Runs the Liquibase migration with bounded retries for transient, retryable failures. In CI,
   * tests run concurrently with unique table prefixes, causing Liquibase to run multiple migrations
   * in parallel against the same database, which can trigger transient errors such as deadlocks.
   */
  protected void performMigrationWithRetry(final PerTenantLiquibase tenant) throws Exception {
    var retryBackoff = DEFAULT_RETRY_BACKOFF;

    for (int attempt = 1; attempt <= DEFAULT_MIGRATION_RETRY_ATTEMPTS; attempt++) {
      try {
        performMigration(tenant);
        return;
      } catch (final Exception e) {
        final boolean shouldRetry =
            isRetryableException(e) && attempt < DEFAULT_MIGRATION_RETRY_ATTEMPTS;
        if (!shouldRetry) {
          throw e;
        }

        LOG.warn(
            "[RDBMS Schema] Liquibase migration for tenant '{}' failed due to a transient, retryable error "
                + "(attempt {}/{}). Retrying in {}.",
            tenant.physicalTenantId(),
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
  protected void performMigration(final PerTenantLiquibase tenant) throws Exception {
    tenant.runner().afterPropertiesSet();
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

  public String getApplicationVersion() {
    return applicationVersion;
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
   *   <li>If the tenant's data source is {@code null}, startup is aborted with an {@link
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
  protected void checkSchemaVersionCompatibility(final PerTenantLiquibase tenant) {
    if (applicationVersion == null) {
      throw new IllegalStateException("[RDBMS Schema] applicationVersion is not configured.");
    }
    if (tenant.dataSource() == null) {
      throw new IllegalStateException(
          "[RDBMS Schema] dataSource is not configured for tenant '"
              + tenant.physicalTenantId()
              + "'.");
    }

    try (final var connection = tenant.dataSource().getConnection()) {
      final var currentSchemaVersion = resolveCurrentSchemaVersion(connection, tenant.prefix());
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
            "[RDBMS Schema] Version check passed for tenant '{}': schema={}, app={}, result={}",
            tenant.physicalTenantId(),
            currentSchemaVersion,
            stableAppVersion.get(),
            result.getClass().getSimpleName());
      } else if (result instanceof Incompatible) {
        LOG.error(
            "[RDBMS Schema] Illegal upgrade path for tenant '{}': schema={}, app={}. "
                + "Upgrade sequentially ({} → next minor). Skipping minors is not supported.",
            tenant.physicalTenantId(),
            currentSchemaVersion,
            stableAppVersion.get(),
            currentSchemaVersion);
        throw new RdbmsSchemaVersionIncompatibleException(
            currentSchemaVersion, stableAppVersion.get());
      } else if (result instanceof Indeterminate) {
        LOG.error(
            "[RDBMS Schema] Cannot determine version compatibility for tenant '{}': schema={}, app={}. "
                + "The stored schema version may be invalid. Startup aborted.",
            tenant.physicalTenantId(),
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
  protected String resolveCurrentSchemaVersion(final Connection connection, final String prefix)
      throws SQLException {
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
  protected void updateSchemaVersion(final PerTenantLiquibase tenant) {
    if (applicationVersion == null || tenant.dataSource() == null) {
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

    final var tableName = tenant.prefix() + SCHEMA_VERSION_TABLE;

    try (final var connection = tenant.dataSource().getConnection()) {
      final var autoCommit = connection.getAutoCommit();
      connection.setAutoCommit(false);
      try {
        upsertSingleSchemaVersionRow(connection, tableName, stableVersion.get());
        connection.commit();
        LOG.debug(
            "[RDBMS Schema] Updated schema version to {} for tenant '{}'.",
            stableVersion.get(),
            tenant.physicalTenantId());
      } catch (final SQLException e) {
        connection.rollback();
        throw e;
      } finally {
        connection.setAutoCommit(autoCommit);
      }
    } catch (final Exception e) {
      LOG.error(
          "[RDBMS Schema] Failed to update schema version in {} for tenant '{}'. Startup aborted.",
          tableName,
          tenant.physicalTenantId(),
          e);
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
   * Checks for stale Liquibase locks for the given tenant and forcibly releases them if they are
   * older than the configured DDL lock wait timeout. This allows recovery from container crashes
   * that left the schema locked without being properly cleaned up.
   *
   * <p>If the tenant's DDL lock wait timeout is {@code null}, or the lock table does not exist yet
   * (first run), this method does nothing.
   */
  protected void releaseStaleLockIfPresent(final PerTenantLiquibase tenant) {
    if (tenant.ddlLockWaitTimeout() == null || tenant.dataSource() == null) {
      return;
    }
    try (final var connection = tenant.dataSource().getConnection()) {
      final var database = openDatabase(connection, tenant.prefix() + "DATABASECHANGELOGLOCK");
      try {
        final var lockService = getLockService(database);
        final var threshold = Instant.now().minus(tenant.ddlLockWaitTimeout());
        for (final var lock : lockService.listLocks()) {
          if (lock.getLockGranted() != null
              && lock.getLockGranted().toInstant().isBefore(threshold)) {
            LOG.warn(
                "[RDBMS Schema] Detected stale Liquibase lock for tenant '{}' acquired at {} by '{}' "
                    + "(older than configured ddl-lock-wait-timeout of {}). Releasing lock to allow "
                    + "migrations to proceed.",
                tenant.physicalTenantId(),
                lock.getLockGranted(),
                lock.getLockedBy(),
                tenant.ddlLockWaitTimeout());
            lockService.forceReleaseLock();
            LOG.info(
                "[RDBMS Schema] Stale Liquibase lock released successfully for tenant '{}'.",
                tenant.physicalTenantId());
            break;
          }
        }
      } finally {
        database.close();
      }
    } catch (final Exception e) {
      LOG.warn(
          "[RDBMS Schema] Failed to check or release stale Liquibase lock for tenant '{}'. "
              + "Proceeding with migration.",
          tenant.physicalTenantId(),
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

  /**
   * Bundles the per-physical-tenant Liquibase runner with the resolved tenant facts needed by the
   * helper methods. Created via {@link #buildPerTenant(String, PerTenantSchemaConfig)} once per
   * tenant.
   */
  protected record PerTenantLiquibase(
      String physicalTenantId,
      SpringLiquibase runner,
      DataSource dataSource,
      String prefix,
      Duration ddlLockWaitTimeout) {}
}
