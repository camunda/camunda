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
import io.camunda.zeebe.db.writebehind.WriteBehindConfiguration;
import io.camunda.zeebe.db.writebehind.WriteBehindStore;
import io.camunda.zeebe.engine.state.rdbms.PartitionStateMapper.PartitionStateRow;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Drops a write-behind store with commits that the flusher did not write, like a crash, and opens a
 * new store on the same partition. The Zeebe log is the source of truth: the rows that were not
 * flushed are gone, and the replay of the commands after the stored position must give the state of
 * the synchronous mode. What the database holds before the replay must be the state after some
 * commit, never a part of one.
 */
final class EntitySessionWriteBehindCrashTest {

  private static final String FLUSHER_THREAD = "zb-db-write-behind";
  private static final int COMMANDS = 6;

  private static final EntityType<String, PartitionStateRow> ROW =
      new EntityType<>(
          PartitionStateMapper.class, "PartitionState", "test row", PartitionStateRow::key);

  private static EngineStateDatabase database;

  private volatile boolean failCommits;
  private int partitionId;
  private WriteBehindStore<EntityKey, Object> store;

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
    database.observed().reset();
    database.observed().decorate(this::failingCommits);
    partitionId = database.nextPartitionId();
  }

  @AfterEach
  void tearDown() {
    failCommits = false;
    database.observed().reset();
  }

  @ParameterizedTest(name = "{0} of 6 commands flushed before the crash")
  @ValueSource(ints = {0, 1, 3, 5, 6})
  void shouldLeaveTheStateAfterACommitAndConvergeByReplay(final int flushedCommands)
      throws Exception {
    // given - commands whose commits the flusher wrote up to a point, and a crash with the rest
    final var context = new JdbcTransactionContext(storedPosition());
    final var session = openSession(context);
    for (int command = 1; command <= COMMANDS; command++) {
      apply(context, session, command);
      if (command == flushedCommands) {
        store.awaitFlushed();
      }
    }
    crash();

    // then - the database holds the state after the last flushed command, and no part of a later
    // one, though the position and the rows of a command are separate rows
    assertThat(storedPosition())
        .isEqualTo(flushedCommands == 0 ? JdbcTransactionContext.NO_POSITION : flushedCommands);
    assertThat(storedRows()).isEqualTo(stateAfter(flushedCommands));

    // when - the restarted partition replays the commands after the stored position
    final var restarted = new JdbcTransactionContext(storedPosition());
    final var restartedSession = openSession(restarted);
    for (int command = flushedCommands + 1; command <= COMMANDS; command++) {
      apply(restarted, restartedSession, command);
    }
    store.close();

    // then - the state is the one of the synchronous mode, which wrote every commit at once
    assertThat(storedRows()).isEqualTo(stateAfter(COMMANDS)).isEqualTo(synchronousState());
    assertThat(storedPosition()).isEqualTo(COMMANDS);
  }

  /** What the commands leave in the table after {@code commands} of them committed. */
  private static Map<String, String> stateAfter(final int commands) {
    final Map<String, String> rows = new TreeMap<>();
    for (int command = 1; command <= commands; command++) {
      rows.put("cmd-" + command + "-a", "v" + command);
      rows.put("cmd-" + command + "-b", "v" + command);
      rows.put("cmd-shared", "v" + command);
      rows.remove("cmd-" + (command - 1) + "-b");
    }
    return rows;
  }

  /** Runs all commands against the database of the synchronous mode, on another partition. */
  private Map<String, String> synchronousState() throws Exception {
    final int synchronousPartition = database.nextPartitionId();
    final var context =
        new JdbcTransactionContext(
            EntitySession.readLastProcessedPosition(
                database.sessionFactory(), synchronousPartition));
    final var session =
        database.entitySession(StateMode.SYNCHRONOUS, context, synchronousPartition);
    for (int command = 1; command <= COMMANDS; command++) {
      apply(context, session, command);
    }
    return storedRows(synchronousPartition);
  }

  /**
   * One command: two new rows, a row that every command updates, and the delete of a row of the
   * command before, so that a part of a commit would show in the state.
   */
  private static void apply(
      final JdbcTransactionContext context, final EntitySession session, final int command)
      throws Exception {
    final var transaction = context.getCurrentTransaction();
    final var value = "v" + command;
    session.insert(ROW, new PartitionStateRow("cmd-" + command + "-a", value));
    session.insert(ROW, new PartitionStateRow("cmd-" + command + "-b", value));
    if (command == 1) {
      session.insert(ROW, new PartitionStateRow("cmd-shared", value));
    } else {
      session.update(ROW, new PartitionStateRow("cmd-shared", value));
      session.delete(ROW, "cmd-" + (command - 1) + "-b");
    }
    context.markAsProcessed(command);
    transaction.commit();
  }

  private EntitySession openSession(final JdbcTransactionContext context) {
    store =
        new WriteBehindStore<>(
            new JdbcBackend(partitionId, database.sessionFactory()),
            WriteBehindConfiguration.defaults()
                .withKeyIndexColumnFamilies(Set.of())
                .withFlushDelay(Duration.ofHours(1)));
    final var session =
        new EntitySession(
            context, partitionId, database.sessionFactory(), store, new GroupCache(100));
    context.register(session);
    return session;
  }

  /** The database rejects what the flusher still holds, and the process is gone. */
  private void crash() {
    failCommits = true;
    try {
      store.close();
    } catch (final RuntimeException expected) {
      // the flush of the last commits fails, like it would not have finished in a crash
    }
    failCommits = false;
  }

  private long storedPosition() {
    return EntitySession.readLastProcessedPosition(database.sessionFactory(), partitionId);
  }

  private Map<String, String> storedRows() throws SQLException {
    return storedRows(partitionId);
  }

  private static Map<String, String> storedRows(final int partition) throws SQLException {
    final Map<String, String> rows = new TreeMap<>();
    try (final var connection = database.dataSource().getConnection();
        final var statement =
            connection.prepareStatement(
                "SELECT state_key, value FROM CAM_RU_PARTITION_STATE"
                    + " WHERE partition_id = ? AND state_key LIKE 'cmd-%'")) {
      statement.setInt(1, partition);
      try (final var result = statement.executeQuery()) {
        while (result.next()) {
          rows.put(result.getString(1), result.getString(2));
        }
      }
    }
    return rows;
  }

  private Connection failingCommits(final Connection delegate) {
    return (Connection)
        Proxy.newProxyInstance(
            Connection.class.getClassLoader(),
            new Class<?>[] {Connection.class},
            (proxy, method, args) -> {
              if (failCommits
                  && method.getName().equals("commit")
                  && Thread.currentThread().getName().equals(FLUSHER_THREAD)) {
                throw new SQLException("the process is gone");
              }
              try {
                return method.invoke(delegate, args);
              } catch (final InvocationTargetException e) {
                throw e.getCause();
              }
            });
  }
}
