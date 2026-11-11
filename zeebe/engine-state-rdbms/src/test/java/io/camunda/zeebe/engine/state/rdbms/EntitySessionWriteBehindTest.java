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
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Runs the session with a write-behind store on the partition state table of PostgreSQL. The store
 * flushes after an hour, so a test sees the database before the flush, and flushes by itself when
 * it needs to.
 */
final class EntitySessionWriteBehindTest {

  private static final EntityType<String, PartitionStateRow> ROW =
      new EntityType<>(
          PartitionStateMapper.class, "PartitionState", "test row", PartitionStateRow::key);

  /** Rows whose group is the first character of the key. */
  private static final EntityType<String, PartitionStateRow> GROUPED =
      new EntityType<>(
          PartitionStateMapper.class,
          "PartitionState",
          "grouped row",
          PartitionStateRow::key,
          key -> key.substring(0, 1));

  private static final Comparator<PartitionStateRow> BY_KEY =
      Comparator.comparing(PartitionStateRow::key);

  private static EngineStateDatabase database;

  private int partitionId;
  private JdbcTransactionContext context;
  private EntitySession session;
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
    partitionId = database.nextPartitionId();
    store =
        new WriteBehindStore<>(
            new JdbcBackend(partitionId, database.sessionFactory()),
            WriteBehindConfiguration.defaults()
                .withKeyIndexColumnFamilies(Set.of())
                .withFlushDelay(Duration.ofHours(1)));
    context = new JdbcTransactionContext();
    session =
        new EntitySession(
            context, partitionId, database.sessionFactory(), store, new GroupCache(100));
    context.register(session);
  }

  @AfterEach
  void tearDown() throws Exception {
    context.getCurrentTransaction().rollback();
    store.close();
  }

  @Test
  void shouldNotWriteToTheDatabaseOnCommit() throws Exception {
    // when
    commit(row("1", "a"));

    // then
    assertThat(committedRows()).isEmpty();
  }

  @Test
  void shouldReadRowThatIsNotFlushedYet() throws Exception {
    // given
    commit(row("1", "a"));

    // when - then
    assertThat(session.get(ROW, "1")).isEqualTo(row("1", "a"));
    assertThat(session.get(ROW, "2")).isNull();
  }

  @Test
  void shouldWriteCommittedRowsWhenFlushed() throws Exception {
    // given
    commit(row("1", "a"), row("2", "b"));

    // when
    store.awaitFlushed();

    // then
    assertThat(committedRows()).containsExactly(row("1", "a"), row("2", "b"));
  }

  @Test
  void shouldWriteTheLatestRowOfSeveralCommits() throws Exception {
    // given
    commit(row("1", "a"));
    update(row("1", "b"));
    update(row("1", "c"));

    // when
    store.awaitFlushed();

    // then
    assertThat(committedRows()).containsExactly(row("1", "c"));
  }

  @Test
  void shouldDeleteFlushedRow() throws Exception {
    // given
    commit(row("1", "a"), row("2", "b"));
    store.awaitFlushed();

    // when
    final var transaction = context.getCurrentTransaction();
    session.delete(ROW, "1");
    transaction.commit();

    // then
    assertThat(session.get(ROW, "1")).isNull();
    store.awaitFlushed();
    assertThat(committedRows()).containsExactly(row("2", "b"));
  }

  @Test
  void shouldReturnUnflushedRowsFromQueriesWithoutFlushing() throws Exception {
    // given
    commit(row("1", "a"), row("2", "b"));

    // when
    final var result = session.query(ROW, List.of(), row -> true, BY_KEY);

    // then
    assertThat(result).containsExactly(row("1", "a"), row("2", "b"));
    assertThat(committedRows()).isEmpty();
  }

  @Test
  void shouldHideFlushedRowThatAnUnflushedDeleteRemoved() throws Exception {
    // given a flushed row, and a delete that is not flushed
    commit(row("1", "a"), row("2", "b"));
    store.awaitFlushed();
    final var transaction = context.getCurrentTransaction();
    session.delete(ROW, "1");
    transaction.commit();
    final var committed = committedRows();

    // when the query of the database still returns the deleted row
    final var result = session.query(ROW, committed, row -> true, BY_KEY);

    // then
    assertThat(result).containsExactly(row("2", "b"));
  }

  @Test
  void shouldReplaceFlushedRowByAnUnflushedUpdate() throws Exception {
    // given
    commit(row("1", "a"));
    store.awaitFlushed();
    update(row("1", "b"));

    // when
    final var result = session.query(ROW, committedRows(), row -> true, BY_KEY);

    // then
    assertThat(result).containsExactly(row("1", "b"));
  }

  @Test
  void shouldReadUnflushedRowsOfSeveralKeysWithoutFlushing() throws Exception {
    // given
    commit(row("1", "a"));

    // when
    final var rows =
        session.getAll(
            ROW, List.of("1", "2"), PartitionStateMapper.class, (mapper, keys) -> List.of());

    // then
    assertThat(rows).containsExactly(row("1", "a"), null);
    assertThat(committedRows()).isEmpty();
  }

  @Test
  void shouldFlushWhenTheStoreIsClosed() throws Exception {
    // given
    commit(row("1", "a"));

    // when
    store.close();

    // then
    assertThat(committedRows()).containsExactly(row("1", "a"));
  }

  @Test
  void shouldNotPublishRolledBackRows() throws Exception {
    // given
    final var transaction = context.getCurrentTransaction();
    session.insert(ROW, row("1", "a"));

    // when
    transaction.rollback();

    // then
    assertThat(session.get(ROW, "1")).isNull();
    store.awaitFlushed();
    assertThat(committedRows()).isEmpty();
  }

  @Test
  void shouldSelectAGroupOnceAcrossTransactions() throws Exception {
    // given a group in the database
    commitGrouped(row("a1", "x"), row("a2", "x"), row("b1", "x"));
    store.awaitFlushed();
    final var selects = new AtomicInteger();

    // when the group is read in several transactions
    final var first = readGroup("a", selects);
    context.getCurrentTransaction().rollback();
    final var second = readGroup("a", selects);

    // then the database was asked once
    assertThat(first).containsExactlyInAnyOrder(row("a1", "x"), row("a2", "x"));
    assertThat(second).containsExactlyInAnyOrder(row("a1", "x"), row("a2", "x"));
    assertThat(selects).hasValue(1);
  }

  @Test
  void shouldApplyCommitsToACachedGroup() throws Exception {
    // given a cached group
    commitGrouped(row("a1", "x"), row("a2", "x"));
    store.awaitFlushed();
    final var selects = new AtomicInteger();
    readGroup("a", selects);
    context.getCurrentTransaction().rollback();

    // when rows of the group change, without a flush
    final var transaction = context.getCurrentTransaction();
    session.insert(GROUPED, row("a3", "x"));
    session.delete(GROUPED, "a1");
    session.update(GROUPED, row("a2", "y"));
    transaction.commit();

    // then the group has the changes and the database was not asked again
    assertThat(readGroup("a", selects)).containsExactlyInAnyOrder(row("a2", "y"), row("a3", "x"));
    assertThat(selects).hasValue(1);
  }

  @Test
  void shouldNotChangeACachedGroupOnRollback() throws Exception {
    // given a cached group
    commitGrouped(row("a1", "x"));
    store.awaitFlushed();
    final var selects = new AtomicInteger();
    readGroup("a", selects);
    context.getCurrentTransaction().rollback();

    // when a transaction changes it and rolls back
    final var transaction = context.getCurrentTransaction();
    session.insert(GROUPED, row("a2", "x"));
    transaction.rollback();

    // then
    assertThat(readGroup("a", selects)).containsExactly(row("a1", "x"));
    assertThat(selects).hasValue(1);
  }

  private List<PartitionStateRow> readGroup(final String group, final AtomicInteger selects) {
    return session.queryGroups(
        GROUPED,
        List.of(group),
        PartitionStateMapper.class,
        (mapper, groups) -> {
          selects.incrementAndGet();
          try {
            return committedRows().stream()
                .filter(row -> groups.contains(row.key().substring(0, 1)))
                .toList();
          } catch (final SQLException e) {
            throw new IllegalStateException(e);
          }
        });
  }

  private void commitGrouped(final PartitionStateRow... rows) throws Exception {
    final var transaction = context.getCurrentTransaction();
    for (final var row : rows) {
      session.insert(GROUPED, row);
    }
    transaction.commit();
  }

  private void commit(final PartitionStateRow... rows) throws Exception {
    final var transaction = context.getCurrentTransaction();
    for (final var row : rows) {
      session.insert(ROW, row);
    }
    transaction.commit();
  }

  private void update(final PartitionStateRow row) throws Exception {
    final var transaction = context.getCurrentTransaction();
    session.update(ROW, row);
    transaction.commit();
  }

  private static PartitionStateRow row(final String key, final String value) {
    return new PartitionStateRow(key, value);
  }

  private List<PartitionStateRow> committedRows() throws SQLException {
    final List<PartitionStateRow> rows = new ArrayList<>();
    try (final var observer = database.dataSource().getConnection();
        final var statement =
            observer.prepareStatement(
                "SELECT state_key, value FROM CAM_RU_PARTITION_STATE"
                    + " WHERE partition_id = ? ORDER BY state_key")) {
      statement.setInt(1, partitionId);
      try (final var result = statement.executeQuery()) {
        while (result.next()) {
          rows.add(row(result.getString(1), result.getString(2)));
        }
      }
    }
    return rows;
  }
}
