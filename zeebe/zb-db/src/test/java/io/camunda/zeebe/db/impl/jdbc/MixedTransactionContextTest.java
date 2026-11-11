/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DefaultColumnFamily;
import io.camunda.zeebe.db.impl.DefaultZeebeDbFactory;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class MixedTransactionContextTest {

  @TempDir private File rocksDbDir;

  private ZeebeDb<DefaultColumnFamily> rocksDb;
  private Connection connection;
  private Connection observer;
  private MixedZeebeDb<DefaultColumnFamily> mixedDb;
  private MixedTransactionContext context;
  private ColumnFamily<DbLong, DbLong> rocksDbRows;
  private ColumnFamily<DbLong, DbLong> committedRocksDbRows;
  private final DbLong key = new DbLong();
  private final DbLong value = new DbLong();
  private StagingParticipant participant;

  @BeforeEach
  void setUp() throws SQLException {
    rocksDb = DefaultZeebeDbFactory.<DefaultColumnFamily>getDefaultFactory().createDb(rocksDbDir);
    final var url = "jdbc:h2:mem:" + UUID.randomUUID();
    connection = DriverManager.getConnection(url);
    connection.setAutoCommit(false);
    observer = DriverManager.getConnection(url);
    try (final var statement = observer.createStatement()) {
      statement.execute("CREATE TABLE test_rows (id BIGINT PRIMARY KEY, val BIGINT)");
    }

    participant = new StagingParticipant(connection);
    mixedDb = new MixedZeebeDb<>(rocksDb, JdbcTransactionContext.NO_POSITION);
    context = (MixedTransactionContext) mixedDb.createContext();
    context.jdbc().register(participant);
    rocksDbRows = mixedDb.createColumnFamily(DefaultColumnFamily.DEFAULT, context, key, value);
    committedRocksDbRows =
        rocksDb.createColumnFamily(
            DefaultColumnFamily.DEFAULT, rocksDb.createContext(), new DbLong(), new DbLong());
  }

  @AfterEach
  void tearDown() throws Exception {
    mixedDb.close();
    connection.close();
    observer.close();
    rocksDb.close();
  }

  @Test
  void shouldTellTheJdbcContextWhichCommandIsReplayed() throws Exception {
    // given - a database that contains the changes up to position 10
    final var restarted = (MixedTransactionContext) new MixedZeebeDb<>(rocksDb, 10).createContext();
    restarted.getCurrentTransaction();

    // when
    restarted.markAsReplaying(10);

    // then
    assertThat(restarted.jdbc().isReplayingCommittedChanges()).isTrue();
  }

  @Test
  void shouldCommitJdbcBeforeRocksDb() throws Exception {
    // given
    final List<Boolean> rocksDbCommittedDuringFlush = new ArrayList<>();
    participant.onFlush = () -> rocksDbCommittedDuringFlush.add(isCommittedInRocksDb(1));
    final var transaction = context.getCurrentTransaction();
    transaction.run(() -> write(1, 10));

    // when
    transaction.commit();

    // then
    assertThat(rocksDbCommittedDuringFlush).containsExactly(false);
    assertThat(isCommittedInRocksDb(1)).isTrue();
    assertThat(committedJdbcRows()).containsExactlyInAnyOrderEntriesOf(Map.of(1L, 10L));
  }

  @Test
  void shouldRollBackBothStores() throws Exception {
    // given
    final var transaction = context.getCurrentTransaction();
    transaction.run(() -> write(1, 10));

    // when
    transaction.rollback();

    // then
    assertThat(isCommittedInRocksDb(1)).isFalse();
    assertThat(committedJdbcRows()).isEmpty();
    assertThat(participant.staged).isEmpty();

    // and the next transaction only commits its own writes
    final var next = context.getCurrentTransaction();
    next.run(() -> write(2, 20));
    next.commit();
    assertThat(isCommittedInRocksDb(1)).isFalse();
    assertThat(isCommittedInRocksDb(2)).isTrue();
    assertThat(committedJdbcRows()).containsExactlyInAnyOrderEntriesOf(Map.of(2L, 20L));
  }

  @Test
  void shouldCommitNestedRunInTransactionOnlyWhenOuterCompletes() throws Exception {
    // given
    final List<Boolean> committedAfterInner = new ArrayList<>();

    // when
    context.runInTransaction(
        () -> {
          write(1, 10);
          context.runInTransaction(() -> write(2, 20));
          committedAfterInner.add(isCommittedInRocksDb(2));
          committedAfterInner.add(committedJdbcRows().containsKey(2L));
        });

    // then
    assertThat(committedAfterInner).containsExactly(false, false);
    assertThat(isCommittedInRocksDb(1)).isTrue();
    assertThat(isCommittedInRocksDb(2)).isTrue();
    assertThat(committedJdbcRows()).containsExactlyInAnyOrderEntriesOf(Map.of(1L, 10L, 2L, 20L));
  }

  @Test
  void shouldNotCommitRocksDbWhenJdbcCommitFails() throws Exception {
    // given
    participant.onFlush =
        () -> {
          throw new SQLException("expected");
        };

    // when
    assertThatThrownBy(() -> context.runInTransaction(() -> write(1, 10)))
        .hasRootCauseMessage("expected");

    // then
    assertThat(isCommittedInRocksDb(1)).isFalse();
    assertThat(committedJdbcRows()).isEmpty();
  }

  @Test
  void shouldConvergeAfterACrashBetweenTheCommits() throws Exception {
    // given - the JDBC part of position 5 is committed, the RocksDB part is lost in a crash
    context.getCurrentTransaction().run(() -> write(1, 10));
    context.jdbc().markAsProcessed(5);
    context.jdbc().getCurrentTransaction().commit();
    context.rocksDb().getCurrentTransaction().rollback();
    assertThat(committedJdbcRows()).containsExactlyEntriesOf(Map.of(1L, 10L));
    assertThat(isCommittedInRocksDb(1)).isFalse();

    // when - after the restart, the replay applies the events of position 5 again, then position 6
    // is processed
    final var restartedDb = new MixedZeebeDb<>(rocksDb, 5);
    final var restarted = (MixedTransactionContext) restartedDb.createContext();
    restarted.jdbc().register(participant);
    rocksDbRows =
        restartedDb.createColumnFamily(DefaultColumnFamily.DEFAULT, restarted, key, value);
    final var replay = restarted.getCurrentTransaction();
    replay.run(() -> write(1, 10));
    restarted.jdbc().markAsProcessed(5);
    replay.commit();
    final var processing = restarted.getCurrentTransaction();
    processing.run(() -> write(2, 20));
    restarted.jdbc().markAsProcessed(6);
    processing.commit();

    // then - RocksDB caught up, and the database applied position 5 once
    assertThat(isCommittedInRocksDb(1)).isTrue();
    assertThat(isCommittedInRocksDb(2)).isTrue();
    assertThat(committedJdbcRows()).containsExactlyEntriesOf(Map.of(1L, 10L, 2L, 20L));
    assertThat(participant.marked).containsExactly(5L, 5L, 6L);
  }

  private void write(final long id, final long val) {
    key.wrapLong(id);
    value.wrapLong(val);
    rocksDbRows.insert(key, value);
    participant.staged.put(id, val);
  }

  private boolean isCommittedInRocksDb(final long id) {
    final var lookup = new DbLong();
    lookup.wrapLong(id);
    return committedRocksDbRows.exists(lookup);
  }

  private Map<Long, Long> committedJdbcRows() throws SQLException {
    final Map<Long, Long> rows = new LinkedHashMap<>();
    try (final var statement = observer.createStatement();
        final var result = statement.executeQuery("SELECT id, val FROM test_rows ORDER BY id")) {
      while (result.next()) {
        rows.put(result.getLong(1), result.getLong(2));
      }
    }
    return rows;
  }

  private interface FlushHook {
    void run() throws SQLException;
  }

  private static final class StagingParticipant implements JdbcTransactionParticipant {

    private final Connection connection;
    private final Map<Long, Long> staged = new LinkedHashMap<>();
    private final List<Long> marked = new ArrayList<>();
    private FlushHook onFlush = () -> {};

    private StagingParticipant(final Connection connection) {
      this.connection = connection;
    }

    @Override
    public void commit() throws SQLException {
      onFlush.run();
      try (final var statement =
          connection.prepareStatement("INSERT INTO test_rows (id, val) VALUES (?, ?)")) {
        for (final var row : staged.entrySet()) {
          statement.setLong(1, row.getKey());
          statement.setLong(2, row.getValue());
          statement.addBatch();
        }
        statement.executeBatch();
      }
      connection.commit();
    }

    @Override
    public void rollback() {
      rollbackQuietly();
    }

    @Override
    public void commitFailed() {
      rollbackQuietly();
    }

    private void rollbackQuietly() {
      try {
        connection.rollback();
      } catch (final SQLException ignored) {
        // nothing to roll back on a broken connection
      }
    }

    @Override
    public void markAsProcessed(final long position) {
      marked.add(position);
    }

    @Override
    public void reset() {
      staged.clear();
    }
  }
}
