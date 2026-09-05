/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms;

import io.camunda.db.rdbms.exception.RdbmsSchemaVersionIncompatibleException;
import io.camunda.db.rdbms.exception.RdbmsSchemaVersionIndeterminateException;
import io.camunda.db.rdbms.exception.RdbmsSchemaVersionUnreadableException;
import io.camunda.zeebe.util.SemanticVersion;
import io.camunda.zeebe.util.VisibleForTesting;
import io.camunda.zeebe.util.migration.CurrentSchemaVersion;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tracks and validates the RDBMS schema version of a single schema (one data source + table prefix)
 * via the {@code RDBMS_SCHEMA_VERSION} table.
 *
 * <p>The upgrade path from the stored schema version to the running application version is
 * validated before applying migrations. Only same-minor or next-minor upgrades are permitted (e.g.
 * 8.9.x → 8.9.y or 8.9.x → 8.10.y). Skipping minor versions (e.g. 8.9.x → 8.11.y) is not supported
 * and causes startup to fail with a {@link RdbmsSchemaVersionIncompatibleException}.
 *
 * <p>Failures come in two kinds, and the caller has to be able to tell them apart: a version that
 * cannot be <em>determined</em> ({@link RdbmsSchemaVersionIndeterminateException}) needs an
 * operator, while a version that cannot be <em>read</em> ({@link
 * RdbmsSchemaVersionUnreadableException}) may well succeed on the next attempt. Both extend {@link
 * IllegalStateException}, which every one of these sites threw before they were split apart.
 */
public class RdbmsSchemaVersionStore {

  /**
   * The schema version that is inferred when the {@code RDBMS_SCHEMA_VERSION} table does not yet
   * exist but the {@code EXPORTER_POSITION} table is present. This indicates an existing database
   * that was created before version tracking was introduced (i.e. a 8.9.x database).
   */
  protected static final String INFERRED_PRE_VERSIONING_SCHEMA_VERSION = "8.9.0";

  /**
   * The table that tracks the RDBMS schema version applied by this application. An entry is
   * written/updated after every successful Liquibase migration run.
   */
  private static final String SCHEMA_VERSION_TABLE = "RDBMS_SCHEMA_VERSION";

  /**
   * Bounds every statement this class issues, so that contention for the single {@code
   * RDBMS_SCHEMA_VERSION} row fails instead of waiting forever.
   *
   * <p>Nothing serializes the nodes writing that row: {@link #recordCurrentVersion()} runs after
   * Liquibase's changelog lock and {@code LiquibaseSchemaManager}'s migration lock are both
   * released, and every broker initializes the schema itself. Unbounded, a peer holding the row in
   * an open transaction parks the caller inside the JDBC call for as long as the vendor allows —
   * indefinitely on PostgreSQL, Oracle and MySQL — throwing nothing, and so logging nothing
   * (#61405).
   *
   * <p>Matches HikariCP's {@code connectionTimeout} default, which already bounds the other half of
   * these calls. Not configurable: the failure it raises is retryable, so the value decides how
   * soon an operator sees a log line, not whether the node recovers.
   *
   * <p>Bounds it on PostgreSQL, MySQL, MariaDB and MSSQL, whose drivers cancel a waiting statement.
   * Oracle and H2 do not: measured on 23 (free) and 2.4.240, their lock waits ignore both this and
   * {@link java.sql.Statement#cancel()}. H2 self-protects with its own {@code LOCK_TIMEOUT} (~2s by
   * default); Oracle has no equivalent, so what keeps it out of an unbounded wait is {@link
   * #recordCurrentVersion()} not issuing the write at all when the version is unchanged. Bounding
   * Oracle's remaining write path would need vendor-specific SQL ({@code SELECT ... FOR UPDATE WAIT
   * n}, or a {@code MERGE}), which this class has none of.
   */
  private static final int STATEMENT_TIMEOUT_SECONDS = 30;

  private static final Logger LOG = LoggerFactory.getLogger(RdbmsSchemaVersionStore.class);
  private final DataSource dataSource;
  private final String prefix;

  /**
   * The current application version. Used to validate the upgrade path from the stored schema
   * version. Must not be {@code null}; a missing value causes startup to be aborted with an {@link
   * RdbmsSchemaVersionIndeterminateException}.
   */
  private final String applicationVersion;

  public RdbmsSchemaVersionStore(
      final DataSource dataSource, final String prefix, final String applicationVersion) {
    this.dataSource = dataSource;
    this.prefix = prefix;
    this.applicationVersion = applicationVersion;
  }

  /**
   * Checks the schema version stored in {@code RDBMS_SCHEMA_VERSION} against the running
   * application version and enforces that minor versions are not skipped.
   *
   * <p>Logic:
   *
   * <ol>
   *   <li>If {@link #applicationVersion} is {@code null}, startup is aborted with an {@link
   *       RdbmsSchemaVersionIndeterminateException}.
   *   <li>If the data source is {@code null}, startup is aborted with an {@link
   *       RdbmsSchemaVersionIndeterminateException}.
   *   <li>If the {@code RDBMS_SCHEMA_VERSION} table does not exist or contains no row (fresh DB or
   *       pre-versioning database):
   *       <ul>
   *         <li>If {@code EXPORTER_POSITION} table exists → infer schema version as {@link
   *             #INFERRED_PRE_VERSIONING_SCHEMA_VERSION} (an existing 8.9.x database).
   *         <li>Otherwise → fresh database; skip the check entirely.
   *       </ul>
   *   <li>Validates the transition. Only same-version, patch-upgrade, and next-minor-upgrade paths
   *       allow startup to continue. Incompatible paths throw a {@link
   *       RdbmsSchemaVersionIncompatibleException}. An indeterminate path (e.g. the stored schema
   *       version is not a valid semantic version) aborts startup with an {@link
   *       RdbmsSchemaVersionIndeterminateException}.
   *   <li>Any unexpected error (e.g. a DB connection failure) fails with an {@link
   *       RdbmsSchemaVersionUnreadableException}, which is retryable.
   * </ol>
   */
  public void checkCompatibility() {
    if (applicationVersion == null) {
      throw new RdbmsSchemaVersionIndeterminateException(
          "[RDBMS Schema] applicationVersion is not configured.");
    }
    if (dataSource == null) {
      throw new RdbmsSchemaVersionIndeterminateException(
          "[RDBMS Schema] dataSource is not configured for prefix '" + prefix + "'.");
    }

    try (final var connection = dataSource.getConnection()) {
      final var currentSchemaVersion = resolveCurrentSchemaVersion(connection, prefix);
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

      final var result = isCompatibleUpgradePath(currentSchemaVersion, stableAppVersion.get());
      if (result) {
        LOG.debug(
            "[RDBMS Schema] Version check passed for prefix '{}': schema={}, app={}",
            prefix,
            currentSchemaVersion,
            stableAppVersion.get());
      } else {
        LOG.error(
            "[RDBMS Schema] Illegal upgrade path for prefix '{}': schema={}, app={}. "
                + "Upgrade sequentially ({} → next minor). Skipping minors is not supported.",
            prefix,
            currentSchemaVersion,
            stableAppVersion.get(),
            currentSchemaVersion);
        throw new RdbmsSchemaVersionIncompatibleException(
            currentSchemaVersion, stableAppVersion.get());
      }
    } catch (final RdbmsSchemaVersionIncompatibleException
        | RdbmsSchemaVersionIndeterminateException e) {
      throw e;
    } catch (final Exception e) {
      LOG.error(
          "[RDBMS Schema] Failed to determine current schema version for prefix '{}'.", prefix, e);
      throw new RdbmsSchemaVersionUnreadableException(
          "[RDBMS Schema] Failed to determine current schema version for prefix '" + prefix + "'.",
          e);
    }
  }

  /**
   * Resolves the current schema-version facts for the upgrade-readiness endpoint, without side
   * effects — unlike {@link #checkCompatibility()}, this never throws and never writes; it only
   * reads. The caller is responsible for mapping these facts to upgrade-readiness states.
   */
  public CurrentSchemaVersion getCurrentSchemaVersion() {
    if (applicationVersion == null) {
      return CurrentSchemaVersion.readFailure(
          prefix, new IllegalStateException("applicationVersion is not configured."));
    }
    if (dataSource == null) {
      return CurrentSchemaVersion.readFailure(
          prefix,
          new IllegalStateException("dataSource is not configured for prefix '" + prefix + "'."));
    }

    try (final var connection = dataSource.getConnection()) {
      final var currentSchemaVersion = resolveCurrentSchemaVersion(connection, prefix);
      if (currentSchemaVersion == null) {
        return CurrentSchemaVersion.freshDatabase(prefix);
      }

      final var stableAppVersion = toStableVersion(applicationVersion);
      return stableAppVersion
          .map(s -> CurrentSchemaVersion.available(prefix, currentSchemaVersion, s))
          .orElseThrow(
              () ->
                  new IllegalStateException(
                      "[RDBMS Schema] cannot parse application version '"
                          + applicationVersion
                          + "' as a semantic version"));
    } catch (final Exception e) {
      LOG.warn(
          "[RDBMS Schema] Failed to determine current schema version for prefix '{}' during "
              + "upgrade-readiness check.",
          prefix,
          e);
      return CurrentSchemaVersion.readFailure(prefix, e);
    }
  }

  /**
   * Upserts the current application version into {@code RDBMS_SCHEMA_VERSION} after a successful
   * Liquibase migration. The version is normalized to stable {@code major.minor.patch} before
   * storage (pre-release suffixes such as {@code -SNAPSHOT} are stripped). If the version cannot be
   * parsed as a semantic version (e.g. {@code "development"}), the write is skipped with a warning.
   *
   * <p>The write is also skipped when the row already holds that version, which is what every start
   * after the first finds. That is not only an avoided round trip: the write contends with every
   * other node recording the same version, and not issuing it is the only way to keep a vendor
   * whose lock wait cannot be bounded — Oracle, H2 — out of that contention altogether. See {@link
   * #STATEMENT_TIMEOUT_SECONDS}.
   *
   * <p>Any failure fails with an {@link RdbmsSchemaVersionUnreadableException} because a missing or
   * incorrect schema-version record would cause the next startup to perform an incorrect
   * compatibility check; it is retryable, since re-running the whole initialization writes it
   * again.
   */
  public void recordCurrentVersion() {
    if (applicationVersion == null || dataSource == null) {
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

    final var tableName = prefix + SCHEMA_VERSION_TABLE;

    try (final var connection = dataSource.getConnection()) {
      if (stableVersion.get().equals(readSchemaVersion(connection, prefix))) {
        // Nothing to write, and so nothing to contend for. Every node records the same version,
        // and the row already holds it on every start after the first — which is the state a
        // restart finds, and the one the peers whose uncommitted row could otherwise be waited on
        // are themselves recording. Reads do not block behind an uncommitted write on any vendor
        // whose reads are snapshot-based, so this is also the only step Oracle can take safely:
        // its row-lock wait cannot be bounded from here at all.
        LOG.debug(
            "[RDBMS Schema] Schema version {} is already recorded for prefix '{}'; nothing to"
                + " write.",
            stableVersion.get(),
            prefix);
        return;
      }

      final var autoCommit = connection.getAutoCommit();
      connection.setAutoCommit(false);
      try {
        upsertSingleSchemaVersionRow(connection, tableName, stableVersion.get());
        connection.commit();
        LOG.debug(
            "[RDBMS Schema] Updated schema version to {} for prefix '{}'.",
            stableVersion.get(),
            prefix);
      } catch (final SQLException e) {
        connection.rollback();
        throw e;
      } finally {
        connection.setAutoCommit(autoCommit);
      }
    } catch (final Exception e) {
      LOG.error(
          "[RDBMS Schema] Failed to update schema version in {} for prefix '{}'.",
          tableName,
          prefix,
          e);
      throw new RdbmsSchemaVersionUnreadableException(
          "[RDBMS Schema] Failed to update schema version in "
              + tableName
              + " for prefix '"
              + prefix
              + "'.",
          e);
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
    try (final var stmt = boundedStatement(connection, "SELECT VERSION FROM " + tableName)) {
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
   *
   * <p>Unquoted identifiers fold differently per vendor: H2 stores them upper case, while
   * PostgreSQL stores them lower case. {@link java.sql.DatabaseMetaData#getTables} matches the
   * stored identifier exactly, so every plausible casing is tried in turn rather than assuming one
   * vendor's convention.
   */
  @VisibleForTesting
  protected boolean tableExists(final Connection connection, final String tableName)
      throws SQLException {
    final var meta = connection.getMetaData();
    final var catalog = connection.getCatalog();
    final var schema = connection.getSchema();
    for (final var candidate :
        new LinkedHashSet<>(List.of(tableName.toUpperCase(), tableName.toLowerCase(), tableName))) {
      try (final var rs = meta.getTables(catalog, schema, candidate, new String[] {"TABLE"})) {
        if (rs.next()) {
          return true;
        }
      }
    }
    return false;
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

  private boolean isCompatibleUpgradePath(
      final String currentSchemaVersion, final String stableAppVersion) {
    final var parsedSchemaVersion = SemanticVersion.parse(currentSchemaVersion);
    final var parsedAppVersion = SemanticVersion.parse(stableAppVersion);

    if (parsedSchemaVersion.isEmpty() || parsedAppVersion.isEmpty()) {
      LOG.error(
          "[RDBMS Schema] Cannot determine version compatibility for prefix '{}': schema={}, app={}. "
              + "The stored schema version may be invalid.",
          prefix,
          currentSchemaVersion,
          stableAppVersion);
      throw new RdbmsSchemaVersionIndeterminateException(
          "[RDBMS Schema] Cannot determine version compatibility: schema="
              + currentSchemaVersion
              + ", app="
              + stableAppVersion
              + ". The stored schema version may be invalid.");
    }

    final var schemaVersion = parsedSchemaVersion.get();
    final var appVersion = parsedAppVersion.get();
    if (schemaVersion.compareTo(appVersion) == 0) {
      return true;
    }
    if (schemaVersion.preRelease() != null || appVersion.preRelease() != null) {
      return false;
    }
    if (schemaVersion.compareTo(appVersion) > 0) {
      return false;
    }

    return schemaVersion.major() == appVersion.major()
        && schemaVersion.minor() - appVersion.minor() >= -1;
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
        boundedStatement(connection, "UPDATE " + tableName + " SET VERSION = ? WHERE ID = 1")) {
      updateStmt.setString(1, stableVersion);
      return updateStmt.executeUpdate();
    }
  }

  private void insertSchemaVersionById(
      final Connection connection, final String tableName, final String stableVersion)
      throws SQLException {
    try (final var insertStmt =
        boundedStatement(connection, "INSERT INTO " + tableName + " (ID, VERSION) VALUES (1, ?)")) {
      insertStmt.setString(1, stableVersion);
      insertStmt.executeUpdate();
    }
  }

  /**
   * Prepares a statement that gives up rather than waiting indefinitely. See {@link
   * #STATEMENT_TIMEOUT_SECONDS} for why every statement in this class needs that.
   */
  private static PreparedStatement boundedStatement(final Connection connection, final String sql)
      throws SQLException {
    final var statement = connection.prepareStatement(sql);
    try {
      statement.setQueryTimeout(STATEMENT_TIMEOUT_SECONDS);
    } catch (final SQLException cannotBeBounded) {
      // an unbounded statement is what this exists to prevent, so it is not run instead
      statement.close();
      throw cannotBeBounded;
    }
    return statement;
  }
}
