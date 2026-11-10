/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.db.rdbms.config.VendorDatabasePropertiesLoader;
import java.sql.SQLException;
import java.util.Random;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Verifies that schema initialization creates the RDBMS primary storage tables, in both H2 modes
 * that are in use: the default one and {@code MODE=PostgreSQL}.
 */
class LiquibaseSchemaManagerStorageH2Test {

  private static final String PREFIX = "STORAGE_";
  private static final String INSERT_BLOCK =
      "INSERT INTO STORAGE_LOG_BLOCK (PARTITION_ID, LOW_POSITION, HIGH_POSITION, TERM, DATA)"
          + " VALUES (?, ?, ?, ?, ?)";

  @Nested
  class DefaultMode extends StorageSchemaTests {
    DefaultMode() {
      super("");
    }
  }

  @Nested
  class PostgresMode extends StorageSchemaTests {
    PostgresMode() {
      super(";MODE=PostgreSQL");
    }
  }

  abstract static class StorageSchemaTests {

    private final String urlOptions;
    private JdbcDataSource dataSource;

    StorageSchemaTests(final String urlOptions) {
      this.urlOptions = urlOptions;
    }

    @BeforeEach
    void setUp() throws Exception {
      dataSource = new JdbcDataSource();
      dataSource.setURL(
          "jdbc:h2:mem:storage-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1" + urlOptions);
      dataSource.setUser("sa");
      dataSource.setPassword("");

      new LiquibaseSchemaManager(
              new PerTenantSchemaConfig(
                  dataSource, VendorDatabasePropertiesLoader.load("h2"), PREFIX, true, null),
              "8.11.0")
          .initialize();
    }

    @Test
    void shouldStoreBlockAsLargeAsDefaultMaxFragmentSize() throws Exception {
      // given
      final var data = new byte[4 * 1024 * 1024];
      new Random(1).nextBytes(data);

      // when
      insertBlock(1, 1, 10, data);

      // then
      try (final var connection = dataSource.getConnection();
          final var statement =
              connection.prepareStatement(
                  "SELECT HIGH_POSITION, DATA FROM STORAGE_LOG_BLOCK"
                      + " WHERE PARTITION_ID = 1 AND LOW_POSITION = 1");
          final var result = statement.executeQuery()) {
        assertThat(result.next()).isTrue();
        assertThat(result.getLong("HIGH_POSITION")).isEqualTo(10);
        assertThat(result.getBytes("DATA")).isEqualTo(data);
      }
    }

    @Test
    void shouldRejectSecondBlockAtSamePosition() throws Exception {
      // given
      insertBlock(1, 1, 10, new byte[] {1});

      // when / then
      assertThatThrownBy(() -> insertBlock(1, 1, 5, new byte[] {2}))
          .isInstanceOf(SQLException.class);
    }

    @Test
    void shouldAllowSamePositionOnDifferentPartitions() throws Exception {
      // given
      insertBlock(1, 1, 10, new byte[] {1});

      // when
      insertBlock(2, 1, 10, new byte[] {2});

      // then
      try (final var connection = dataSource.getConnection();
          final var statement =
              connection.prepareStatement("SELECT COUNT(*) FROM STORAGE_LOG_BLOCK");
          final var result = statement.executeQuery()) {
        assertThat(result.next()).isTrue();
        assertThat(result.getInt(1)).isEqualTo(2);
      }
    }

    @Test
    void shouldKeepOneTermPerPartition() throws Exception {
      // given
      insertPartitionTerm(1, 3);

      // when / then
      assertThatThrownBy(() -> insertPartitionTerm(1, 4)).isInstanceOf(SQLException.class);
      try (final var connection = dataSource.getConnection();
          final var statement =
              connection.prepareStatement(
                  "SELECT TERM FROM STORAGE_LOG_PARTITION WHERE PARTITION_ID = 1");
          final var result = statement.executeQuery()) {
        assertThat(result.next()).isTrue();
        assertThat(result.getLong("TERM")).isEqualTo(3);
        assertThat(result.next()).isFalse();
      }
    }

    private void insertPartitionTerm(final int partitionId, final long term) throws SQLException {
      try (final var connection = dataSource.getConnection();
          final var statement =
              connection.prepareStatement(
                  "INSERT INTO STORAGE_LOG_PARTITION (PARTITION_ID, TERM) VALUES (?, ?)")) {
        statement.setInt(1, partitionId);
        statement.setLong(2, term);
        statement.executeUpdate();
      }
    }

    private void insertBlock(
        final int partitionId, final long lowPosition, final long highPosition, final byte[] data)
        throws SQLException {
      try (final var connection = dataSource.getConnection();
          final var statement = connection.prepareStatement(INSERT_BLOCK)) {
        statement.setInt(1, partitionId);
        statement.setLong(2, lowPosition);
        statement.setLong(3, highPosition);
        statement.setLong(4, 1);
        statement.setBytes(5, data);
        statement.executeUpdate();
      }
    }
  }
}
