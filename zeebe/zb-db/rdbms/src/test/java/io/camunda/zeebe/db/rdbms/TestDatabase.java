/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.rdbms;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.sql.DataSource;

/** An empty database of a test's own, so tests never see each other's tables. */
public interface TestDatabase extends AutoCloseable {

  DataSource dataSource();

  /** The id MyBatis picks the statements by, like {@code h2} or {@code postgresql}. */
  String databaseId();

  default void execute(final String sql) throws SQLException {
    try (final var connection = dataSource().getConnection();
        final var statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }

  default long queryLong(final String sql) throws SQLException {
    try (final var connection = dataSource().getConnection();
        final var statement = connection.createStatement();
        final var result = statement.executeQuery(sql)) {
      result.next();
      return result.getLong(1);
    }
  }

  /**
   * In upper case, as PostgreSQL stores the names in lower case while H2 stores them in upper case.
   */
  default List<String> tableNames() throws SQLException {
    final var tableNames = new ArrayList<String>();
    try (final var connection = dataSource().getConnection();
        final var tables =
            connection.getMetaData().getTables(null, null, "%", new String[] {"TABLE"})) {
      while (tables.next()) {
        tableNames.add(tables.getString("TABLE_NAME").toUpperCase(Locale.ROOT));
      }
    }
    return tableNames;
  }

  @Override
  void close() throws SQLException;
}
