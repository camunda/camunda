/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.rdbms;

import java.sql.SQLException;
import java.time.Duration;
import java.util.UUID;
import javax.sql.DataSource;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * A database of its own in a PostgreSQL server that all tests of the JVM share. The server starts
 * with the first database, and Testcontainers removes it when the JVM ends.
 */
public final class TestPostgresDatabase implements TestDatabase {

  private final String name = "zeebe_" + UUID.randomUUID().toString().replace("-", "");
  private final PGSimpleDataSource dataSource;

  public TestPostgresDatabase() {
    this(Duration.ZERO);
  }

  /**
   * @param lockTimeout how long a statement waits for a row lock before it fails; zero waits for
   *     ever
   */
  public TestPostgresDatabase(final Duration lockTimeout) {
    try {
      Server.execute("CREATE DATABASE " + name);
    } catch (final SQLException e) {
      throw new IllegalStateException("Failed to create the database " + name, e);
    }
    dataSource = Server.dataSource(name);
    dataSource.setOptions("-c lock_timeout=" + lockTimeout.toMillis());
  }

  @Override
  public DataSource dataSource() {
    return dataSource;
  }

  @Override
  public String databaseId() {
    return "postgresql";
  }

  @Override
  public void close() throws SQLException {
    Server.execute("DROP DATABASE " + name + " WITH (FORCE)");
  }

  private static final class Server {

    private static final PostgreSQLContainer CONTAINER = start();

    private static PostgreSQLContainer start() {
      final var container = new PostgreSQLContainer("postgres:15-alpine");
      container.start();
      return container;
    }

    private static PGSimpleDataSource dataSource(final String databaseName) {
      final var dataSource = new PGSimpleDataSource();
      dataSource.setServerNames(new String[] {CONTAINER.getHost()});
      dataSource.setPortNumbers(
          new int[] {CONTAINER.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT)});
      dataSource.setDatabaseName(databaseName);
      dataSource.setUser(CONTAINER.getUsername());
      dataSource.setPassword(CONTAINER.getPassword());
      return dataSource;
    }

    private static void execute(final String sql) throws SQLException {
      try (final var connection = dataSource(CONTAINER.getDatabaseName()).getConnection();
          final var statement = connection.createStatement()) {
        statement.execute(sql);
      }
    }
  }
}
