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
   * The table that tracks the RDBMS schema version applied by this application. An entry is
   * written/updated after every successful Liquibase migration run.
   */
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
   *   <li>If the {@code RDBMS_SCHEMA_VERSION} table does not exist or contains no row, the schema
   *       is new and has no upgrade path to validate; skip the check entirely. A schema that
   *       predates version tracking is not one of those cases: it arrives here with 8.9.0 already
   *       recorded by {@code schema-version-seed.xml}. See {@link #readSchemaVersion}.
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
      final var currentSchemaVersion = readSchemaVersion(connection, prefix);
      if (currentSchemaVersion == null) {
        // A new schema, with no recorded version and so no upgrade path to validate.
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
      final var currentSchemaVersion = readSchemaVersion(connection, prefix);
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
   * Any failure fails with an {@link RdbmsSchemaVersionUnreadableException} because a missing or
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

    final var tableName = prefix + RdbmsTableNames.SCHEMA_VERSION;

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
   * Reads the schema version from {@code RDBMS_SCHEMA_VERSION}, which is the only thing this class
   * will believe about a schema's version. Returns {@code null} if the table does not exist or
   * contains no rows, and the caller skips the check either way, because neither shape belongs to a
   * schema whose version is knowable: no table means nothing has migrated this schema yet, and an
   * empty table means {@code LiquibaseSchemaManager} has created it but the first migration has not
   * finished recording a version. Both are new schemas, and a new schema has no upgrade path to
   * refuse. Propagates any unexpected {@link SQLException}.
   *
   * <p>A schema that predates version tracking does have a knowable version, and it is knowable
   * here because {@code schema-version-seed.xml} records 8.9.0 for it before this is ever read.
   * Deducing it here instead — from {@code EXPORTER_POSITION} being present while {@code
   * RDBMS_SCHEMA_VERSION} was not — is what #62554 was: on a fresh database that is also, for
   * almost the whole of a first migration, precisely what a peer node sees.
   */
  @VisibleForTesting
  protected String readSchemaVersion(final Connection connection, final String prefix)
      throws SQLException {
    final var tableName = prefix + RdbmsTableNames.SCHEMA_VERSION;
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
