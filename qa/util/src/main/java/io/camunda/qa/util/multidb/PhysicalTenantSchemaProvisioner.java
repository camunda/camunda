/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.multidb;

import io.camunda.qa.util.multidb.CamundaMultiDBExtension.DatabaseType;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provisions the per-physical-tenant secondary storage in the matrix-provided RDBMS and derives the
 * per-PT JDBC connection parameters.
 *
 * <p>Each non-H2 RDBMS dialect isolates a tenant differently:
 *
 * <ul>
 *   <li>PostgreSQL / Aurora: a dedicated <em>schema</em> inside the base database, selected via
 *       {@code currentSchema=<ns>} in the JDBC URL.
 *   <li>MySQL / MariaDB: a dedicated <em>database</em> (MySQL's schema == database); the database
 *       path segment in the JDBC URL is replaced.
 *   <li>SQL Server: a dedicated <em>database</em>; the {@code databaseName} property in the
 *       semicolon-delimited JDBC URL is replaced.
 *   <li>Oracle: a per-tenant <em>table prefix</em> in the shared {@code camunda} schema — not a
 *       dedicated schema. Oracle's schema == user, so a schema-per-PT would need {@code CREATE
 *       USER} (DBA-only); more fundamentally, the production secondary-storage isolation check
 *       ({@code SecondaryStorageIsolationValidation}) keys a location on (type, connection url,
 *       table prefix) and deliberately ignores the user, so two Oracle users on the same URL are
 *       treated as the same location and rejected. A distinct table prefix is the validator's
 *       explicitly-allowed "shared connection, distinct prefix" mode and needs no privileges, so
 *       Oracle uses it instead of a per-PT schema.
 * </ul>
 *
 * <p>The namespace name is {@code <basePrefix>_<tenantId>}, where {@code basePrefix} is the
 * run-unique 10-character random token generated for the current test run (so concurrent CI matrix
 * runs targeting the same server cannot collide). For the schema/database dialects it names the
 * schema/database; for Oracle it is the per-PT table prefix.
 */
@NullMarked
final class PhysicalTenantSchemaProvisioner {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(PhysicalTenantSchemaProvisioner.class);

  private PhysicalTenantSchemaProvisioner() {}

  /**
   * Builds the namespace name for a physical tenant: {@code <basePrefix>_<tenantId>} — the schema
   * or database name on the schema/database dialects, or the table prefix on Oracle.
   */
  static String buildNamespace(final String basePrefix, final String tenantId) {
    return basePrefix + "_" + tenantId;
  }

  /**
   * Returns a PostgreSQL JDBC URL with {@code currentSchema=<namespace>} appended to the query
   * string. If the URL already contains a {@code ?}, the parameter is appended with {@code &};
   * otherwise {@code ?} is added first.
   */
  static String derivePostgresUrl(final String baseUrl, final String namespace) {
    final String param = "currentSchema=" + namespace;
    return baseUrl.contains("?") ? baseUrl + "&" + param : baseUrl + "?" + param;
  }

  /**
   * Returns a MySQL or MariaDB JDBC URL with the database path segment replaced by {@code
   * namespace}. The segment is the portion between the last {@code /} before any {@code ?} and the
   * query string (if present).
   *
   * <p>Example: {@code jdbc:mysql://host:3306/camunda?charset=utf8} → {@code
   * jdbc:mysql://host:3306/<namespace>?charset=utf8}.
   *
   * @throws IllegalArgumentException if the URL has no database segment after the host (e.g. {@code
   *     jdbc:mysql://host:3306}); rewriting such a URL would otherwise corrupt the host portion.
   */
  static String deriveMysqlUrl(final String baseUrl, final String namespace) {
    final int queryStart = baseUrl.indexOf('?');
    final String urlWithoutQuery = queryStart >= 0 ? baseUrl.substring(0, queryStart) : baseUrl;
    final String query = queryStart >= 0 ? baseUrl.substring(queryStart) : "";
    // The database segment is the first '/' after the authority (the '//host:port' part); using the
    // last '/' would land on the scheme's '//' for URLs without a database and rewrite the host.
    final int authorityStart = urlWithoutQuery.indexOf("//");
    final int dbSlash =
        authorityStart >= 0
            ? urlWithoutQuery.indexOf('/', authorityStart + 2)
            : urlWithoutQuery.indexOf('/');
    if (dbSlash < 0) {
      throw new IllegalArgumentException(
          "Cannot derive a per-physical-tenant MySQL/MariaDB URL from '"
              + baseUrl
              + "': expected a database segment after the host (e.g. jdbc:mysql://host:port/db)");
    }
    return urlWithoutQuery.substring(0, dbSlash + 1) + namespace + query;
  }

  /**
   * Returns a SQL Server JDBC URL with the {@code databaseName} property replaced by {@code
   * namespace}. SQL Server JDBC URLs use semicolon-delimited key=value properties after the host.
   * If {@code databaseName} is already present it is replaced in place; otherwise it is appended.
   *
   * <p>Example: {@code jdbc:sqlserver://host:1433;Encrypt=false} → {@code
   * jdbc:sqlserver://host:1433;Encrypt=false;databaseName=<namespace>}.
   */
  static String deriveMssqlUrl(final String baseUrl, final String namespace) {
    // Replace existing databaseName=... property (case-insensitive key match)
    final String replaced =
        baseUrl.replaceFirst("(?i)(;databaseName=)[^;]*", ";databaseName=" + namespace);
    if (!replaced.equals(baseUrl)) {
      return replaced;
    }
    // Property was not present — append it
    return baseUrl + ";databaseName=" + namespace;
  }

  /**
   * Provisions the namespace in the target RDBMS and returns the derived {@link PtStorageConfig}
   * for the given physical tenant.
   *
   * <p>For H2 the caller manages provisioning itself; this method must not be called for {@link
   * DatabaseType#RDBMS_H2}.
   */
  static PtStorageConfig provisionAndDerive(
      final DatabaseType databaseType,
      final String baseUrl,
      final String baseUsername,
      final String basePassword,
      final String basePrefix,
      final String tenantId) {

    final String namespace = buildNamespace(basePrefix, tenantId);

    return switch (databaseType) {
      case RDBMS_POSTGRES, RDBMS_AURORA -> {
        createPostgresSchema(baseUrl, baseUsername, basePassword, namespace);
        yield new PtStorageConfig(
            derivePostgresUrl(baseUrl, namespace), baseUsername, basePassword, "");
      }
      case RDBMS_MYSQL, RDBMS_MARIADB -> {
        createMysqlDatabase(baseUrl, baseUsername, basePassword, namespace);
        yield new PtStorageConfig(
            deriveMysqlUrl(baseUrl, namespace), baseUsername, basePassword, "");
      }
      case RDBMS_MSSQL -> {
        createMssqlDatabase(baseUrl, baseUsername, basePassword, namespace);
        yield new PtStorageConfig(
            deriveMssqlUrl(baseUrl, namespace), baseUsername, basePassword, "");
      }
      case RDBMS_ORACLE ->
          // Oracle isolates by table prefix in the shared schema (see class javadoc): no DDL, the
          // base connection is reused and the namespace becomes the per-PT table prefix.
          new PtStorageConfig(baseUrl, baseUsername, basePassword, namespace);
      default ->
          throw new IllegalArgumentException(
              "provisionAndDerive called for unsupported database type: " + databaseType);
    };
  }

  private static void createPostgresSchema(
      final String url, final String user, final String pass, final String namespace) {
    executeDdl(url, user, pass, "CREATE SCHEMA IF NOT EXISTS " + namespace);
  }

  private static void createMysqlDatabase(
      final String url, final String user, final String pass, final String namespace) {
    executeDdl(url, user, pass, "CREATE DATABASE IF NOT EXISTS `" + namespace + "`");
  }

  private static void createMssqlDatabase(
      final String url, final String user, final String pass, final String namespace) {
    executeDdl(
        url,
        user,
        pass,
        "IF DB_ID('" + namespace + "') IS NULL CREATE DATABASE [" + namespace + "]");
  }

  /**
   * Best-effort drop of a previously provisioned namespace, used for per-run cleanup so persistent
   * shared instances (notably Aurora) don't accumulate orphaned schemas/databases across CI runs.
   *
   * <p>Failures are logged and swallowed: cleanup must never fail a test run, and the {@code IF
   * EXISTS} guards make repeated invocations harmless.
   *
   * <p>Oracle and H2 have no namespace object to drop and are no-ops: H2 uses a throwaway in-memory
   * database per PT, and Oracle isolates by table prefix in the shared schema. Oracle's per-run
   * prefixed tables are therefore <em>not</em> dropped here — the run-unique prefix keeps separate
   * runs from colliding, and the Oracle matrix runs against a fresh container per run, so they
   * don't accumulate on a long-lived shared instance the way schema/database namespaces would.
   */
  static void dropNamespace(
      final DatabaseType databaseType,
      final String baseUrl,
      final String baseUsername,
      final String basePassword,
      final String namespace) {
    final String ddl =
        switch (databaseType) {
          case RDBMS_POSTGRES, RDBMS_AURORA -> "DROP SCHEMA IF EXISTS " + namespace + " CASCADE";
          case RDBMS_MYSQL, RDBMS_MARIADB -> "DROP DATABASE IF EXISTS `" + namespace + "`";
          case RDBMS_MSSQL ->
              "IF DB_ID('" + namespace + "') IS NOT NULL DROP DATABASE [" + namespace + "]";
          // Oracle / H2: nothing to drop (table-prefix isolation / per-PT in-memory DB).
          default -> null;
        };
    if (ddl == null) {
      return;
    }
    try {
      executeDdl(baseUrl, baseUsername, basePassword, ddl);
    } catch (final RuntimeException e) {
      LOGGER.warn("Best-effort cleanup of physical-tenant namespace '{}' failed", namespace, e);
    }
  }

  private static void executeDdl(
      final String url, final String user, final String pass, final String ddl) {
    LOGGER.debug("Executing bootstrap DDL: {}", ddl);
    try (final var conn = DriverManager.getConnection(url, user, pass);
        final var stmt = conn.createStatement()) {
      stmt.execute(ddl);
    } catch (final SQLException e) {
      throw new RuntimeException("Failed to execute bootstrap DDL: " + ddl, e);
    }
  }

  /**
   * Holds the derived per-physical-tenant JDBC connection parameters. {@code prefix} is the table
   * prefix to apply: empty for the schema/database dialects (isolation is at the schema/database
   * level), or the namespace for Oracle (isolation is by table prefix in the shared schema).
   */
  record PtStorageConfig(String url, String username, String password, String prefix) {}
}
