/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.rdbms.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.db.rdbms.TestDatabase;
import io.camunda.zeebe.db.rdbms.TestH2Database;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class StateSchemaManagerTest {

  private static final String UNIQUE_VIOLATION = "23505";

  private final TestDatabase database = createDatabase();

  @AfterEach
  void tearDown() throws SQLException {
    database.close();
  }

  @Test
  void shouldCreateStateTables() throws SQLException {
    // given
    final var schemaManager = new StateSchemaManager(database.dataSource(), "A_");

    // when
    schemaManager.migrate();

    // then
    assertThat(database.tableNames()).contains("A_ZEEBE_STATE_PARTITION", "A_ZEEBE_STATE");
  }

  @Test
  void shouldTrackChangesInItsOwnTables() throws SQLException {
    // given
    final var schemaManager = new StateSchemaManager(database.dataSource(), "A_");

    // when
    schemaManager.migrate();

    // then the history schema's tracking tables are left alone
    assertThat(database.tableNames())
        .contains("A_ZEEBE_STATE_DATABASECHANGELOG", "A_ZEEBE_STATE_DATABASECHANGELOGLOCK")
        .doesNotContain("A_DATABASECHANGELOG", "A_DATABASECHANGELOGLOCK");
  }

  @Test
  void shouldKeepStateWhenMigratedAgain() throws SQLException {
    // given
    final var schemaManager = new StateSchemaManager(database.dataSource(), "A_");
    schemaManager.migrate();
    insertPartition("A_", 1, "state-1");
    insertState("A_", 1, new byte[] {1});

    // when
    schemaManager.migrate();

    // then
    assertThat(stateKeys("A_", 1)).containsExactly(new byte[] {1});
  }

  @Test
  void shouldMigrateEveryPrefixOfTheSameDatabase() throws SQLException {
    // given
    new StateSchemaManager(database.dataSource(), "").migrate();

    // when
    new StateSchemaManager(database.dataSource(), "B_").migrate();

    // then
    assertThat(database.tableNames())
        .contains(
            "ZEEBE_STATE_PARTITION", "ZEEBE_STATE", "B_ZEEBE_STATE_PARTITION", "B_ZEEBE_STATE");
  }

  @Test
  void shouldUpdatePrefixThatIsBehindAnotherPrefixOfTheSameDatabase() throws SQLException {
    // given - B_ misses the last change set, as if it were still on an older version
    final var upToDate = new StateSchemaManager(database.dataSource(), "A_");
    final var behind = new StateSchemaManager(database.dataSource(), "B_");
    upToDate.migrate();
    behind.migrate();
    execute("DROP TABLE B_ZEEBE_STATE");
    execute("DELETE FROM B_ZEEBE_STATE_DATABASECHANGELOG WHERE ID = 'create_zeebe_state_table'");

    // and Liquibase has found A_ up to date
    upToDate.migrate();

    // when
    behind.migrate();

    // then
    assertThat(database.tableNames()).contains("B_ZEEBE_STATE");
  }

  @Test
  void shouldRejectSecondStateForSamePartition() throws SQLException {
    // given
    new StateSchemaManager(database.dataSource(), "A_").migrate();
    insertPartition("A_", 1, "state-1");

    // when - then
    assertThatThrownBy(() -> insertPartition("A_", 1, "state-2"))
        .isInstanceOfSatisfying(
            SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo(UNIQUE_VIOLATION));
  }

  @Test
  void shouldSortKeysAsUnsignedBytesLikeRocksDb() throws SQLException {
    // given
    new StateSchemaManager(database.dataSource(), "A_").migrate();
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

  private void execute(final String sql) throws SQLException {
    try (final var connection = database.dataSource().getConnection();
        final var statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }

  private void insertPartition(final String prefix, final int partitionId, final String stateId)
      throws SQLException {
    try (final var connection = database.dataSource().getConnection();
        final var statement =
            connection.prepareStatement(
                "INSERT INTO %sZEEBE_STATE_PARTITION VALUES (?, ?, 0)".formatted(prefix))) {
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
                "INSERT INTO %sZEEBE_STATE VALUES (?, 1, ?, ?)".formatted(prefix))) {
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
                "SELECT STATE_KEY FROM %sZEEBE_STATE WHERE PARTITION_ID = ? AND COLUMN_FAMILY = 1 ORDER BY STATE_KEY"
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
