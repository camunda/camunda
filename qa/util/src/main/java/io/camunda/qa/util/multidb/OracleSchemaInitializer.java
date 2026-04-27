/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.multidb;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Initializes the Oracle database schema for {@code @MultiDbTest} tests without running Liquibase
 * migrations.
 *
 * <p>On Oracle, evaluating Liquibase {@code tableExists}/{@code indexExists} preconditions is
 * expensive and can add ~120 s of startup overhead per test class. This class avoids that cost by
 * loading a pre-generated SQL template (produced during the {@code db/rdbms-schema} Maven build)
 * and executing it directly over JDBC.
 *
 * <p>The template is stored at {@code liquibase/sql/create-template/oracle/oracle_master.sql} on
 * the classpath and contains {@code __PREFIX__} as a sentinel in every table, index and constraint
 * name. Before execution the sentinel is replaced with the caller-supplied per-test prefix,
 * preserving test isolation for parallel test classes.
 */
public class OracleSchemaInitializer {

  /**
   * Sentinel value used in the SQL template in place of the real per-test prefix. Must match the
   * value used when generating the template in {@code LiquibaseScriptGenerator.main}.
   */
  public static final String PREFIX_SENTINEL = "__PREFIX__";

  static final String TEMPLATE_CLASSPATH_RESOURCE =
      "liquibase/sql/create-template/oracle/oracle_master.sql";

  private static final Logger LOG = LoggerFactory.getLogger(OracleSchemaInitializer.class);

  private final String jdbcUrl;
  private final String username;
  private final String password;

  public OracleSchemaInitializer(
      final String jdbcUrl, final String username, final String password) {
    this.jdbcUrl = jdbcUrl;
    this.username = username;
    this.password = password;
  }

  /**
   * Creates all schema objects (tables, indexes, constraints) for the given prefix by loading the
   * Oracle SQL template, substituting {@link #PREFIX_SENTINEL} with {@code tablePrefix} and
   * executing each statement over a fresh JDBC connection.
   *
   * @param tablePrefix the per-test prefix to substitute for {@link #PREFIX_SENTINEL}
   * @throws Exception if the template cannot be loaded or any SQL statement fails
   */
  public void initializeSchema(final String tablePrefix) throws Exception {
    LOG.info("Initializing Oracle schema with prefix '{}'", tablePrefix);
    final String sql = loadTemplate().replace(PREFIX_SENTINEL, tablePrefix);
    executeSql(sql);
    LOG.info("Oracle schema initialization complete for prefix '{}'", tablePrefix);
  }

  // ---- private helpers ----

  private String loadTemplate() throws Exception {
    try (final InputStream is =
        getClass().getClassLoader().getResourceAsStream(TEMPLATE_CLASSPATH_RESOURCE)) {
      if (is == null) {
        throw new IllegalStateException(
            "Oracle SQL template not found on classpath: "
                + TEMPLATE_CLASSPATH_RESOURCE
                + ". Make sure camunda-db-rdbms-schema is on the test classpath.");
      }
      return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private void executeSql(final String sql) throws SQLException {
    try (final Connection conn = DriverManager.getConnection(jdbcUrl, username, password);
        final Statement stmt = conn.createStatement()) {
      for (final String rawStatement : sql.split(";\\s*\n")) {
        // Each block may start with a "-- <changeset-id>" comment line produced by the generator.
        // Strip those leading comment lines so that only the actual DDL is sent to Oracle.
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
   * <p>The template generator prefixes each change-set block with a comment of the form {@code --
   * <changeset-id>}. These comments must be stripped before sending the DDL to Oracle via JDBC
   * because Oracle's thin driver rejects statements that begin with a comment (the driver does not
   * perform any pre-processing of the SQL string).
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
