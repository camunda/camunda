/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.clustering;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
final class PostgresReplicatedRdbmsPartitionStateIT extends ReplicatedRdbmsPartitionStateTestBase {

  @Container
  private static final PostgreSQLContainer SERVER = new PostgreSQLContainer("postgres:15-alpine");

  private final String databaseName =
      "zeebe_state_" + UUID.randomUUID().toString().replace("-", "");

  @Override
  StateDatabase createDatabase() {
    execute("CREATE DATABASE " + databaseName);
    return new StateDatabase(
        "jdbc:postgresql://%s:%d/%s"
            .formatted(
                SERVER.getHost(),
                SERVER.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT),
                databaseName),
        SERVER.getUsername(),
        SERVER.getPassword());
  }

  @Override
  void dropDatabase(final StateDatabase database) {
    execute("DROP DATABASE " + databaseName + " WITH (FORCE)");
  }

  private static void execute(final String sql) {
    try (final var connection =
            DriverManager.getConnection(
                SERVER.getJdbcUrl(), SERVER.getUsername(), SERVER.getPassword());
        final var statement = connection.createStatement()) {
      statement.execute(sql);
    } catch (final SQLException e) {
      throw new IllegalStateException("Failed to run: " + sql, e);
    }
  }
}
