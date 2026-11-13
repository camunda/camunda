/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.rdbms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Checks what the tables of the state guarantee, whatever writes to them. */
class StateSchemaTest {

  private static final String UNIQUE_VIOLATION = "23505";

  private final TestDatabase database = createDatabase();

  @AfterEach
  void tearDown() throws SQLException {
    database.close();
  }

  @Test
  void shouldRejectSecondStateForSamePartition() throws SQLException {
    // given
    TestStorage.createTables(database.dataSource(), "A_");
    insertPartition("A_", 1, "state-1");

    // when - then
    assertThatThrownBy(() -> insertPartition("A_", 1, "state-2"))
        .isInstanceOfSatisfying(
            SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo(UNIQUE_VIOLATION));
  }

  @Test
  void shouldSortKeysAsUnsignedBytesLikeRocksDb() throws SQLException {
    // given
    TestStorage.createTables(database.dataSource(), "A_");
    insertPartition("A_", 1, "state-1");
    final var keys =
        List.of(
            new byte[] {(byte) 0xFF},
            new byte[] {0x01},
            new byte[] {(byte) 0x80},
            new byte[] {0x00, 0x01},
            new byte[] {0x7F},
            new byte[] {0x00});
    for (final var key : keys) {
      insertState("A_", 1, key);
    }

    // when
    final var sortedKeys = stateKeys("A_", 1);

    // then
    assertThat(sortedKeys)
        .containsExactly(
            new byte[] {0x00},
            new byte[] {0x00, 0x01},
            new byte[] {0x01},
            new byte[] {0x7F},
            new byte[] {(byte) 0x80},
            new byte[] {(byte) 0xFF});
  }

  private void insertPartition(final String prefix, final int partitionId, final String stateId)
      throws SQLException {
    try (final var connection = database.dataSource().getConnection();
        final var statement =
            connection.prepareStatement(
                "INSERT INTO %sZS_PARTITION VALUES (?, ?, 0, -1)".formatted(prefix))) {
      statement.setInt(1, partitionId);
      statement.setString(2, stateId);
      statement.executeUpdate();
    }
  }

  private void insertState(final String prefix, final int partitionId, final byte[] key)
      throws SQLException {
    try (final var connection = database.dataSource().getConnection();
        final var statement =
            connection.prepareStatement(
                "INSERT INTO %sZS_KEY VALUES (?, ?, ?, -1)".formatted(prefix))) {
      statement.setInt(1, partitionId);
      statement.setBytes(2, key);
      statement.setBytes(3, new byte[0]);
      statement.executeUpdate();
    }
  }

  private List<byte[]> stateKeys(final String prefix, final int partitionId) throws SQLException {
    final var keys = new ArrayList<byte[]>();
    try (final var connection = database.dataSource().getConnection();
        final var statement =
            connection.prepareStatement(
                "SELECT STATE_KEY FROM %sZS_KEY WHERE PARTITION_ID = ? ORDER BY STATE_KEY"
                    .formatted(prefix))) {
      statement.setInt(1, partitionId);
      try (final var rows = statement.executeQuery()) {
        while (rows.next()) {
          keys.add(rows.getBytes(1));
        }
      }
    }
    return keys;
  }

  /** Overridden to run the same tests on another database. */
  TestDatabase createDatabase() {
    return new TestH2Database();
  }
}
