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

  private static final Logger LOG = LoggerFactory.getLogger(RdbmsSchemaVersionStore.class);

  private final DataSource dataSource;
  private final String prefix;

  /**
   * The current application version. Used to validate the upgrade path from the stored schema
   * version. Must not be {@code null}; a missing value causes startup to be aborted with an {@link
   * IllegalStateException}.
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
   *       IllegalStateException}.
   *   <li>If the data source is {@code null}, startup is aborted with an {@link
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
  public void checkCompatibility() {
    if (applicationVersion == null) {
      throw new IllegalStateException("[RDBMS Schema] applicationVersion is not configured.");
    }
    if (dataSource == null) {
      throw new IllegalStateException(
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

      final var result =
          VersionCompatibilityCheck.check(currentSchemaVersion, stableAppVersion.get());
      if (result instanceof Compatible) {
        LOG.debug(
            "[RDBMS Schema] Version check passed for prefix '{}': schema={}, app={}, result={}",
            prefix,
            currentSchemaVersion,
            stableAppVersion.get(),
            result.getClass().getSimpleName());
      } else if (result instanceof Incompatible) {
        LOG.error(
            "[RDBMS Schema] Illegal upgrade path for prefix '{}': schema={}, app={}. "
                + "Upgrade sequentially ({} → next minor). Skipping minors is not supported.",
            prefix,
            currentSchemaVersion,
            stableAppVersion.get(),
            currentSchemaVersion);
        throw new RdbmsSchemaVersionIncompatibleException(
            currentSchemaVersion, stableAppVersion.get());
      } else if (result instanceof Indeterminate) {
        LOG.error(
            "[RDBMS Schema] Cannot determine version compatibility for prefix '{}': schema={}, app={}. "
                + "The stored schema version may be invalid. Startup aborted.",
            prefix,
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
   * Upserts the current application version into {@code RDBMS_SCHEMA_VERSION} after a successful
   * Liquibase migration. The version is normalized to stable {@code major.minor.patch} before
   * storage (pre-release suffixes such as {@code -SNAPSHOT} are stripped). If the version cannot be
   * parsed as a semantic version (e.g. {@code "development"}), the write is skipped with a warning.
   * Any failure aborts startup with an {@link IllegalStateException} because a missing or incorrect
   * schema-version record would cause the next startup to perform an incorrect compatibility check.
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
          "[RDBMS Schema] Failed to update schema version in {} for prefix '{}'. Startup aborted.",
          tableName,
          prefix,
          e);
      throw new IllegalStateException(
          "[RDBMS Schema] Failed to update schema version in " + tableName + ". Startup aborted.",
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
}
