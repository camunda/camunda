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
 * Provisions a dedicated schema/database/user for a physical tenant in the matrix-provided RDBMS
 * and derives the per-PT JDBC connection parameters.
 *
 * <p>Each non-H2 RDBMS dialect uses a different namespace primitive:
 *
 * <ul>
 *   <li>PostgreSQL / Aurora: a dedicated <em>schema</em> inside the base database, selected via
 *       {@code currentSchema=<ns>} in the JDBC URL.
 *   <li>MySQL / MariaDB: a dedicated <em>database</em> (MySQL's schema == database); the database
 *       path segment in the JDBC URL is replaced.
 *   <li>Oracle: a dedicated <em>user</em> (Oracle's schema == user); the PT username is set to the
 *       namespace name with a fixed throwaway password. The JDBC URL is unchanged.
 *   <li>SQL Server: a dedicated <em>database</em>; the {@code databaseName} property in the
 *       semicolon-delimited JDBC URL is replaced.
 * </ul>
 *
 * <p>The namespace name is {@code <basePrefix>_<tenantId>}, where {@code basePrefix} is the
 * run-unique 10-character random token generated for the current test run (so concurrent CI matrix
 * runs targeting the same server cannot collide). Oracle imposes a 30-character identifier limit,
 * so the namespace {@code basePrefix (10) + "_" + tenantId} must not exceed 30 characters; tenant
 * IDs used in integration tests are kept short (e.g. {@code tenanta}, 7 chars) to stay within this
 * constraint.
 */
@NullMarked
final class PhysicalTenantSchemaProvisioner {

  /** Fixed throwaway password used when provisioning an Oracle schema-user. */
  static final String ORACLE_SCHEMA_PASSWORD = "Pt4dmin#1";

  private static final Logger LOGGER =
      LoggerFactory.getLogger(PhysicalTenantSchemaProvisioner.class);

  private PhysicalTenantSchemaProvisioner() {}

  /**
   * Builds the namespace name for a physical tenant: {@code <basePrefix>_<tenantId>}.
   *
   * <p>Oracle imposes a 30-character maximum on user/schema names. Callers must ensure that {@code
   * basePrefix.length() + 1 + tenantId.length() <= 30}.
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
   */
  static String deriveMysqlUrl(final String baseUrl, final String namespace) {
    final int queryStart = baseUrl.indexOf('?');
    final String urlWithoutQuery = queryStart >= 0 ? baseUrl.substring(0, queryStart) : baseUrl;
    final String query = queryStart >= 0 ? baseUrl.substring(queryStart) : "";
    final int lastSlash = urlWithoutQuery.lastIndexOf('/');
    return urlWithoutQuery.substring(0, lastSlash + 1) + namespace + query;
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
            derivePostgresUrl(baseUrl, namespace), baseUsername, basePassword);
      }
      case RDBMS_MYSQL, RDBMS_MARIADB -> {
        createMysqlDatabase(baseUrl, baseUsername, basePassword, namespace);
        yield new PtStorageConfig(deriveMysqlUrl(baseUrl, namespace), baseUsername, basePassword);
      }
      case RDBMS_ORACLE -> {
        createOracleUser(baseUrl, baseUsername, basePassword, namespace);
        yield new PtStorageConfig(baseUrl, namespace, ORACLE_SCHEMA_PASSWORD);
      }
      case RDBMS_MSSQL -> {
        createMssqlDatabase(baseUrl, baseUsername, basePassword, namespace);
        yield new PtStorageConfig(deriveMssqlUrl(baseUrl, namespace), baseUsername, basePassword);
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

  private static void createOracleUser(
      final String url, final String user, final String pass, final String namespace) {
    executeDdl(
        url,
        user,
        pass,
        "CREATE USER " + namespace + " IDENTIFIED BY \"" + ORACLE_SCHEMA_PASSWORD + "\"");
    executeDdl(url, user, pass, "GRANT CONNECT, RESOURCE TO " + namespace);
    executeDdl(url, user, pass, "ALTER USER " + namespace + " QUOTA UNLIMITED ON USERS");
  }

  private static void createMssqlDatabase(
      final String url, final String user, final String pass, final String namespace) {
    executeDdl(
        url,
        user,
        pass,
        "IF DB_ID('" + namespace + "') IS NULL CREATE DATABASE [" + namespace + "]");
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
   * Holds the derived JDBC connection parameters for a provisioned physical-tenant schema. The
   * table prefix is always empty because isolation is achieved at the schema level.
   */
  record PtStorageConfig(String url, String username, String password) {}
}
