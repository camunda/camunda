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
import java.util.UUID;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;

/**
 * An in-memory H2 database of its own, so tests never see each other's tables. It stays alive
 * between connections until it's closed.
 */
public final class TestH2Database implements AutoCloseable {

  private final JdbcDataSource dataSource = new JdbcDataSource();

  public TestH2Database() {
    this("");
  }

  /**
   * @param settings more settings for every connection, like {@code ;LOCK_TIMEOUT=100}
   */
  public TestH2Database(final String settings) {
    dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1" + settings);
  }

  public DataSource dataSource() {
    return dataSource;
  }

  public String url() {
    return dataSource.getURL();
  }

  public void execute(final String sql) throws SQLException {
    try (final var connection = dataSource.getConnection();
        final var statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }

  public long queryLong(final String sql) throws SQLException {
    try (final var connection = dataSource.getConnection();
        final var statement = connection.createStatement();
        final var result = statement.executeQuery(sql)) {
      result.next();
      return result.getLong(1);
    }
  }

  public List<String> tableNames() throws SQLException {
    final var tableNames = new ArrayList<String>();
    try (final var connection = dataSource.getConnection();
        final var tables =
            connection.getMetaData().getTables(null, null, "%", new String[] {"TABLE"})) {
      while (tables.next()) {
        tableNames.add(tables.getString("TABLE_NAME"));
      }
    }
    return tableNames;
  }

  @Override
  public void close() throws SQLException {
    execute("SHUTDOWN");
  }
}
