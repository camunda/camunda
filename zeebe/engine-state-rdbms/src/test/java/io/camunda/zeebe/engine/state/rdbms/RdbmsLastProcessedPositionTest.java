/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.rdbms;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.impl.jdbc.JdbcTransactionContext;
import io.camunda.zeebe.engine.state.rdbms.PartitionStateMapper.PartitionStateRow;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The position the database stores with the changes of each command, which tells the replay after a
 * restart which changes the database already contains. The changes are rows of the partition state
 * table: inserting one again fails, so a replay that applied them twice would fail.
 */
final class RdbmsLastProcessedPositionTest {

  private static EngineStateDatabase database;

  private int partitionId;

  @BeforeAll
  static void startDatabase() throws Exception {
    database = EngineStateDatabase.start();
  }

  @AfterAll
  static void stopDatabase() {
    database.close();
  }

  @BeforeEach
  void setUp() {
    partitionId = database.nextPartitionId();
  }

  @Test
  void shouldReadNoPositionOfANewPartitionAndCreateItsRow() {
    // when
    final long first = readLastProcessedPosition();
    final long second = readLastProcessedPosition();

    // then
    assertThat(first).isEqualTo(JdbcTransactionContext.NO_POSITION);
    assertThat(second).isEqualTo(JdbcTransactionContext.NO_POSITION);
    assertThat(positionRows("'-1'")).isOne();
  }

  @Test
  void shouldStoreThePositionWithTheChanges() throws Exception {
    // given
    final var context = new JdbcTransactionContext(readLastProcessedPosition());
    final var session = database.entitySession(context, partitionId);

    // when
    applyCommand(context, session, "a", 5);

    // then
    assertThat(readLastProcessedPosition()).isEqualTo(5);
    assertThat(changeRows()).isOne();
  }

  @Test
  void shouldNotStoreThePositionOfACommandWithoutChanges() throws Exception {
    // given
    final var context = new JdbcTransactionContext(readLastProcessedPosition());
    final var session = database.entitySession(context, partitionId);
    applyCommand(context, session, "a", 5);

    // when - a command that only reads
    final var transaction = context.getCurrentTransaction();
    session.get(RdbmsProcessStore.PARTITION_STATE, "change-a");
    context.markAsProcessed(6);
    transaction.commit();

    // then - the position stays, until a command with changes writes its own
    assertThat(readLastProcessedPosition()).isEqualTo(5);
    applyCommand(context, session, "b", 7);
    assertThat(readLastProcessedPosition()).isEqualTo(7);
  }

  @Test
  void shouldConvergeWhenTheDatabaseIsACommandAheadAfterACrash() throws Exception {
    // given - the database committed position 5, but RocksDB didn't before the crash
    final var beforeCrash = new JdbcTransactionContext(readLastProcessedPosition());
    applyCommand(beforeCrash, database.entitySession(beforeCrash, partitionId), "a", 5);

    // when - after the restart, the replay applies position 5 again, then processing continues
    final var restarted = new JdbcTransactionContext(readLastProcessedPosition());
    final var session = database.entitySession(restarted, partitionId);
    applyCommand(restarted, session, "a", 5);
    applyCommand(restarted, session, "b", 6);

    // then
    assertThat(changeRows()).isEqualTo(2);
    assertThat(readLastProcessedPosition()).isEqualTo(6);
  }

  private static void applyCommand(
      final JdbcTransactionContext context,
      final EntitySession session,
      final String change,
      final long position)
      throws Exception {
    final var transaction = context.getCurrentTransaction();
    session.insert(
        RdbmsProcessStore.PARTITION_STATE, new PartitionStateRow("change-" + change, ""));
    context.markAsProcessed(position);
    transaction.commit();
  }

  private long readLastProcessedPosition() {
    return EntitySession.readLastProcessedPosition(database.sessionFactory(), partitionId);
  }

  private long positionRows(final String value) {
    return database.countRows(
        "CAM_RU_PARTITION_STATE",
        partitionId,
        "state_key = 'last-processed-position' AND value = " + value);
  }

  private long changeRows() {
    return database.countRows("CAM_RU_PARTITION_STATE", partitionId, "state_key LIKE 'change-%'");
  }
}
