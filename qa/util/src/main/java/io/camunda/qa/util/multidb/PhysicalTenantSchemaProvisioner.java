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
import org.jspecify.annotations.Nullable;
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
 *   <li>Oracle: a dedicated <em>schema</em>, which on Oracle == a dedicated <em>user</em>. Each PT
 *       gets a {@code CREATE USER <namespace>} (with {@code CONNECT}/{@code RESOURCE} and an
 *       unlimited tablespace quota) and connects as that user, so its tables live in its own schema
 *       and no table prefix is needed. The PT's {@code database-vendor-id} is pinned to {@code
 *       oracle} so the production isolation check ({@code SecondaryStorageIsolationValidation})
 *       recognizes distinct Oracle users on the same JDBC URL as distinct locations (see <a
 *       href="https://github.com/camunda/camunda/issues/56402">#56402</a>). This exercises the same
 *       schema-per-user topology as the other dialects; the {@code CREATE USER}/{@code DROP USER}
 *       bootstrap requires the base connection to be a privileged (DBA-grade) Oracle user.
 * </ul>
 *
 * <p>The namespace name is {@code <basePrefix>_<tenantId>}, where {@code basePrefix} is the
 * run-unique 10-character random token generated for the current test run (so concurrent CI matrix
 * runs targeting the same server cannot collide). For the schema/database dialects it names the
 * schema/database; for Oracle it names the per-PT user/schema.
 */
@NullMarked
final class PhysicalTenantSchemaProvisioner {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(PhysicalTenantSchemaProvisioner.class);

  /**
   * Vendor id pinned on Oracle PTs so the production isolation check keys on the connecting user.
   */
  private static final String ORACLE_VENDOR_ID = "oracle";

  /** ORA-01918: "user '…' does not exist" — the expected DROP USER outcome when none is present. */
  private static final int ORA_USER_DOES_NOT_EXIST = 1918;

  private PhysicalTenantSchemaProvisioner() {}

  /**
   * Builds the namespace name for a physical tenant: {@code <basePrefix>_<tenantId>} — the schema
   * or database name on the schema/database dialects, or the user/schema name on Oracle.
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
            derivePostgresUrl(baseUrl, namespace), baseUsername, basePassword, "", null);
      }
      case RDBMS_MYSQL, RDBMS_MARIADB -> {
        createMysqlDatabase(baseUrl, baseUsername, basePassword, namespace);
        yield new PtStorageConfig(
            deriveMysqlUrl(baseUrl, namespace), baseUsername, basePassword, "", null);
      }
      case RDBMS_MSSQL -> {
        createMssqlDatabase(baseUrl, baseUsername, basePassword, namespace);
        yield new PtStorageConfig(
            deriveMssqlUrl(baseUrl, namespace), baseUsername, basePassword, "", null);
      }
      case RDBMS_ORACLE -> {
        // Oracle isolates by schema-per-user: create a dedicated user (== schema) and connect as it
        // (see class javadoc). No table prefix; the vendor id is pinned so the production isolation
        // check keys the location on the distinct user rather than rejecting the shared URL.
        createOracleUser(baseUrl, baseUsername, basePassword, namespace, basePassword);
        yield new PtStorageConfig(baseUrl, namespace, basePassword, "", ORACLE_VENDOR_ID);
      }
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
   * Creates a dedicated Oracle user (== schema) for a physical tenant and grants it the privileges
   * needed to own its own tables ({@code CONNECT}, {@code RESOURCE}, and an unlimited tablespace
   * quota). Oracle has no {@code CREATE USER IF NOT EXISTS}, so any leftover user from a prior
   * same-JVM rerun is dropped first (best-effort) to guarantee a fresh schema per run. Requires the
   * bootstrap connection to be a privileged (DBA-grade) Oracle user.
   */
  private static void createOracleUser(
      final String url,
      final String adminUser,
      final String adminPass,
      final String namespace,
      final String userPassword) {
    try {
      executeDdl(url, adminUser, adminPass, "DROP USER " + namespace + " CASCADE");
    } catch (final RuntimeException e) {
      // Only ignore "user does not exist" (ORA-01918) — the expected case when there is no leftover
      // user from a prior same-JVM rerun. Any other failure (privileges, connectivity) is real and
      // must surface here rather than masquerading as a later CREATE USER failure.
      if (isOracleUserDoesNotExist(e)) {
        LOGGER.debug("No pre-existing Oracle user '{}' to drop before create", namespace);
      } else {
        throw e;
      }
    }
    executeDdl(
        url,
        adminUser,
        adminPass,
        "CREATE USER "
            + namespace
            + " IDENTIFIED BY \""
            + escapeOracleQuotedLiteral(userPassword)
            + "\"",
        "GRANT CONNECT, RESOURCE TO " + namespace,
        "GRANT UNLIMITED TABLESPACE TO " + namespace);
  }

  private static boolean isOracleUserDoesNotExist(final RuntimeException e) {
    return e.getCause() instanceof final SQLException sqlException
        && sqlException.getErrorCode() == ORA_USER_DOES_NOT_EXIST;
  }

  /** Escapes a double-quoted Oracle literal by doubling any embedded double quotes. */
  private static String escapeOracleQuotedLiteral(final String value) {
    return value.replace("\"", "\"\"");
  }

  /**
   * Best-effort drop of a previously provisioned namespace, used for per-run cleanup so persistent
   * shared instances (notably Aurora) don't accumulate orphaned schemas/databases across CI runs.
   *
   * <p>Failures are logged and swallowed: cleanup must never fail a test run, and the {@code IF
   * EXISTS} guards make repeated invocations harmless.
   *
   * <p>H2 has no namespace object to drop and is a no-op: it uses a throwaway in-memory database
   * per PT. Oracle drops the per-PT user with {@code DROP USER ... CASCADE} (which removes the
   * schema and all its objects), matching the schema/database dialects.
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
          case RDBMS_ORACLE -> "DROP USER " + namespace + " CASCADE";
          // H2: nothing to drop (per-PT in-memory DB).
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
      final String url, final String user, final String pass, final String... ddls) {
    try (final var conn = DriverManager.getConnection(url, user, pass);
        final var stmt = conn.createStatement()) {
      for (final String ddl : ddls) {
        LOGGER.debug("Executing bootstrap DDL: {}", ddl);
        stmt.execute(ddl);
      }
    } catch (final SQLException e) {
      throw new RuntimeException("Failed to execute bootstrap DDL: " + String.join("; ", ddls), e);
    }
  }

  /**
   * Holds the derived per-physical-tenant JDBC connection parameters. {@code prefix} is the table
   * prefix to apply: empty for every RDBMS dialect (isolation is at the schema/database/user
   * level). {@code databaseVendorId}, when non-null, pins the PT's {@code database-vendor-id} — set
   * to {@code oracle} so the production isolation check keys the storage location on the connecting
   * user.
   */
  record PtStorageConfig(
      String url,
      String username,
      String password,
      String prefix,
      @Nullable String databaseVendorId) {}
}
