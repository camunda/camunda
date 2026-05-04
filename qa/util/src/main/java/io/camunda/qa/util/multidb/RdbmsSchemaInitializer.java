/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.multidb;

import io.camunda.db.rdbms.LiquibaseScriptGenerator;
import io.camunda.db.rdbms.config.VendorDatabasePropertiesLoader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Initializes an RDBMS schema for {@code @MultiDbTest} tests without running Liquibase migrations.
 *
 * <p>On Oracle, evaluating Liquibase {@code tableExists}/{@code indexExists} preconditions is
 * expensive and can add ~120 s of startup overhead per test class. More broadly, running Liquibase
 * for every test class adds unnecessary overhead for all RDBMS databases. This class avoids that
 * cost by generating the complete DDL script at the start of each test class's execution (using
 * {@link LiquibaseScriptGenerator}) and executing it directly over JDBC, bypassing all Liquibase
 * precondition evaluation.
 *
 * <p>By generating the SQL with the actual per-test prefix at runtime, each test class gets its own
 * isolated set of tables, which preserves parallel test execution safety.
 */
public class RdbmsSchemaInitializer {

  private static final Logger LOG = LoggerFactory.getLogger(RdbmsSchemaInitializer.class);

  private final String databaseType;
  private final String jdbcUrl;
  private final String username;
  private final String password;

  /**
   * @param databaseType the Liquibase database type string (e.g. {@code "oracle"}, {@code
   *     "postgresql"}, {@code "h2"}, {@code "mariadb"}, {@code "mysql"}, {@code "mssql"})
   * @param jdbcUrl JDBC connection URL
   * @param username database username
   * @param password database password
   */
  public RdbmsSchemaInitializer(
      final String databaseType,
      final String jdbcUrl,
      final String username,
      final String password) {
    this.databaseType = databaseType;
    this.jdbcUrl = jdbcUrl;
    this.username = username;
    this.password = password;
  }

  /**
   * Generates and executes all schema DDL (tables, indexes, constraints) for the given prefix
   * directly over JDBC, without running Liquibase.
   *
   * @param tablePrefix the per-test table prefix (e.g. {@code "ABCDEFGHIJ"})
   * @throws Exception if SQL generation or execution fails
   */
  public void initializeSchema(final String tablePrefix) throws Exception {
    LOG.info("Initializing {} schema with prefix '{}'", databaseType, tablePrefix);
    final var props = VendorDatabasePropertiesLoader.load(databaseType);
    final String sql =
        LiquibaseScriptGenerator.generateSqlScript(
            databaseType,
            LiquibaseScriptGenerator.CHANGELOG_PATH + "changelog-master.xml",
            tablePrefix,
            props.userCharColumnSize(),
            props.errorMessageSize(),
            props.treePathSize());
    executeSql(sql);
    LOG.info("{} schema initialization complete for prefix '{}'", databaseType, tablePrefix);
  }

  private void executeSql(final String sql) throws SQLException {
    try (final Connection conn = DriverManager.getConnection(jdbcUrl, username, password);
        final Statement stmt = conn.createStatement()) {
      for (final String rawStatement : sql.split(";")) {
        // Each block may start with a "-- <changeset-id>" comment line produced by the generator.
        // Strip those leading comment lines so that only the actual DDL is sent to the database.
        // Some JDBC drivers (notably Oracle's thin driver) reject statements that begin with a
        // comment.
        final String ddl = stripLeadingComments(rawStatement).trim();
        if (!ddl.isEmpty()) {
          LOG.debug("Executing DDL: {}", ddl);
          stmt.execute(ddl);
        }
      }
    }
  }

  /**
   * Removes leading lines that consist solely of a SQL line comment ({@code --}).
   *
   * <p>The SQL generator prefixes each change-set block with a comment of the form {@code --
   * <changeset-id>}. These comments must be stripped before sending the DDL to the database via
   * JDBC because some drivers (notably Oracle's thin driver) reject statements that begin with a
   * comment.
   */
  static String stripLeadingComments(final String statement) {
    final String[] lines = statement.split("\n");
    final StringBuilder result = new StringBuilder();
    boolean seenNonComment = false;
    for (final String line : lines) {
      if (!seenNonComment && line.trim().startsWith("--")) {
        continue;
      }
      seenNonComment = true;
      if (result.length() > 0) {
        result.append('\n');
      }
      result.append(line);
    }
    return result.toString();
  }
}
