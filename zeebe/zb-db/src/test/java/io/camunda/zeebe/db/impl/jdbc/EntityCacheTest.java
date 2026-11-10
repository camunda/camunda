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

import io.camunda.zeebe.db.ZeebeDbException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLTransientException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Runs the cache against H2 through a {@link JdbcTransactionContext}, with a plain JDBC persister
 * that logs every statement it sends.
 */
final class EntityCacheTest {

  private Connection connection;
  private Connection observer;
  private JdbcTransactionContext context;
  private RowPersister persister;
  private EntityCache<Long, Row> cache;

  @BeforeEach
  void setUp() throws SQLException {
    final var url = "jdbc:h2:mem:" + UUID.randomUUID();
    connection = DriverManager.getConnection(url);
    observer = DriverManager.getConnection(url);
    try (final var statement = observer.createStatement()) {
      statement.execute(
          "CREATE TABLE test_rows (id BIGINT PRIMARY KEY, grp INT NOT NULL, val VARCHAR(255))");
    }
    context = new JdbcTransactionContext(connection);
    persister = new RowPersister();
    cache = new EntityCache<>("test row", context, persister);
    context.register(new CacheParticipant());
  }

  @AfterEach
  void tearDown() throws SQLException {
    connection.close();
    observer.close();
  }

  @Test
  void shouldWriteOnlyOnCommit() throws Exception {
    // given
    final var transaction = context.getCurrentTransaction();
    cache.insert(new Row(1, 0, "a"));

    // when - then
    assertThat(cache.get(1L)).isEqualTo(new Row(1, 0, "a"));
    assertThat(committedRows()).isEmpty();
    transaction.commit();
    assertThat(committedRows()).containsExactly(new Row(1, 0, "a"));
    assertThat(cache.stagedKeys()).isEmpty();
  }

  @Test
  void shouldCollapseInsertAndUpdateIntoInsert() throws Exception {
    // given
    final var transaction = context.getCurrentTransaction();

    // when
    cache.insert(new Row(1, 0, "a"));
    cache.update(new Row(1, 0, "b"));
    transaction.commit();

    // then
    assertThat(persister.statements).containsExactly("INSERT 1");
    assertThat(committedRows()).containsExactly(new Row(1, 0, "b"));
  }

  @Test
  void shouldNotWriteInsertedThenDeletedRow() throws Exception {
    // given
    final var transaction = context.getCurrentTransaction();

    // when
    cache.insert(new Row(1, 0, "a"));
    cache.delete(1L);
    transaction.commit();

    // then
    assertThat(persister.statements).isEmpty();
    assertThat(committedRows()).isEmpty();
  }

  @Test
  void shouldCollapseDeleteAndInsertIntoUpdate() throws Exception {
    // given
    commit(new Row(1, 0, "a"));
    final var transaction = context.getCurrentTransaction();

    // when
    cache.delete(1L);
    assertThat(cache.get(1L)).isNull();
    cache.insert(new Row(1, 0, "b"));
    transaction.commit();

    // then
    assertThat(persister.statements).containsExactly("UPDATE 1");
    assertThat(committedRows()).containsExactly(new Row(1, 0, "b"));
  }

  @Test
  void shouldCollapseUpdateAndDeleteIntoDelete() throws Exception {
    // given
    commit(new Row(1, 0, "a"));
    final var transaction = context.getCurrentTransaction();

    // when
    cache.update(new Row(1, 0, "b"));
    cache.delete(1L);
    transaction.commit();

    // then
    assertThat(persister.statements).containsExactly("DELETE 1");
    assertThat(committedRows()).isEmpty();
  }

  @Test
  void shouldRejectInvalidTransitions() {
    // given
    context.getCurrentTransaction();
    cache.insert(new Row(1, 0, "a"));
    cache.delete(2L);

    // when - then
    assertThatThrownBy(() -> cache.insert(new Row(1, 0, "b")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Expected to insert test row with key 1, but it already exists");
    assertThatThrownBy(() -> cache.update(new Row(2, 0, "b")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Expected to update test row with key 2, but it was removed");
  }

  @Test
  void shouldFailCommitOnUpdateOfMissingRow() {
    // given
    final var transaction = context.getCurrentTransaction();
    cache.update(new Row(1, 0, "a"));

    // when - then
    assertThatThrownBy(transaction::commit)
        .hasRootCauseMessage("Expected to update test row with key 1, but it does not exist");
  }

  @Test
  void shouldMergeQueryResultsWithStagedWrites() throws Exception {
    // given - committed 1, 2, 3 in group 0
    commit(new Row(1, 0, "a"), new Row(2, 0, "b"), new Row(3, 0, "c"));
    context.getCurrentTransaction();

    // when - 1 moves to group 1, 2 is deleted, 3 is changed, 4 is added
    cache.update(new Row(1, 1, "a"));
    cache.delete(2L);
    cache.update(new Row(3, 0, "c2"));
    cache.insert(new Row(4, 0, "d"));

    // then
    final var dbResults = persister.selectGroup(0);
    assertThat(dbResults).extracting(Row::id).containsExactly(1L, 2L, 3L);
    assertThat(cache.query(dbResults, row -> row.grp() == 0, Comparator.comparing(Row::id)))
        .containsExactly(new Row(3, 0, "c2"), new Row(4, 0, "d"));
    assertThat(
            cache.queryKeys(
                dbResults.stream().map(Row::id).toList(),
                row -> row.grp() == 0,
                Comparator.reverseOrder()))
        .containsExactly(4L, 3L);
    assertThat(cache.stagedMatching(row -> row.grp() == 1)).containsExactly(new Row(1, 1, "a"));
    assertThat(cache.stagedKeys()).containsExactlyInAnyOrder(1L, 2L, 3L, 4L);
  }

  @Test
  void shouldFlushSameWritesAgainWhenCommitIsRetried() throws Exception {
    // given - the first flush fails after the rows were written
    commit(new Row(1, 0, "a"), new Row(2, 0, "b"));
    final var transaction = context.getCurrentTransaction();
    cache.insert(new Row(3, 0, "c"));
    cache.update(new Row(1, 0, "a2"));
    cache.delete(2L);
    persister.failNextFlush = true;

    // when
    assertThatThrownBy(transaction::commit).isInstanceOf(ZeebeDbException.class);
    assertThat(committedRows()).extracting(Row::id).containsExactly(1L, 2L);
    transaction.commit();

    // then
    assertThat(committedRows()).containsExactly(new Row(1, 0, "a2"), new Row(3, 0, "c"));
  }

  @Test
  void shouldDropStagedWritesOnRollback() throws Exception {
    // given
    commit(new Row(1, 0, "a"));
    final var transaction = context.getCurrentTransaction();
    cache.delete(1L);
    cache.insert(new Row(2, 0, "b"));

    // when
    transaction.rollback();

    // then
    assertThat(cache.stagedKeys()).isEmpty();
    assertThat(cache.get(1L)).isEqualTo(new Row(1, 0, "a"));
    assertThat(cache.get(2L)).isNull();
  }

  private void commit(final Row... rows) throws Exception {
    final var transaction = context.getCurrentTransaction();
    for (final Row row : rows) {
      cache.insert(row);
    }
    transaction.commit();
    persister.statements.clear();
  }

  private List<Row> committedRows() throws SQLException {
    final List<Row> rows = new ArrayList<>();
    try (final var statement = observer.createStatement();
        final var result =
            statement.executeQuery("SELECT id, grp, val FROM test_rows ORDER BY id")) {
      while (result.next()) {
        rows.add(new Row(result.getLong(1), result.getInt(2), result.getString(3)));
      }
    }
    return rows;
  }

  private record Row(long id, int grp, String val) {}

  /** Drains the cache on commit, like the participant of a store does. */
  private final class CacheParticipant implements JdbcTransactionParticipant {

    @Override
    public void flush(final Connection connection) throws SQLException {
      final List<Row> inserts = new ArrayList<>();
      final List<Row> updates = new ArrayList<>();
      final List<Long> deletes = new ArrayList<>();
      cache.writeStaged(
          new EntityCache.StagedWriter<>() {
            @Override
            public void delete(final Long key) {
              deletes.add(key);
            }

            @Override
            public void insert(final Row entity) {
              inserts.add(entity);
            }

            @Override
            public void update(final Row entity) {
              updates.add(entity);
            }
          });
      final int[] updateCounts = persister.flush(connection, inserts, updates, deletes);
      for (int i = 0; i < updates.size(); i++) {
        cache.verifyUpdated(updates.get(i), updateCounts[i]);
      }
    }

    @Override
    public void reset() {
      cache.reset();
    }
  }

  private final class RowPersister implements EntityPersister<Long, Row> {

    private final List<String> statements = new ArrayList<>();
    private boolean failNextFlush;

    @Override
    public Long keyOf(final Row entity) {
      return entity.id();
    }

    @Override
    public @Nullable Row load(final Long key) throws SQLException {
      try (final var statement =
          connection.prepareStatement("SELECT id, grp, val FROM test_rows WHERE id = ?")) {
        statement.setLong(1, key);
        try (final var result = statement.executeQuery()) {
          return result.next()
              ? new Row(result.getLong(1), result.getInt(2), result.getString(3))
              : null;
        }
      }
    }

    private int[] flush(
        final Connection connection,
        final List<Row> inserts,
        final List<Row> updates,
        final List<Long> deletes)
        throws SQLException {
      try (final var delete = connection.prepareStatement("DELETE FROM test_rows WHERE id = ?");
          final var insert =
              connection.prepareStatement("INSERT INTO test_rows (id, grp, val) VALUES (?, ?, ?)");
          final var update =
              connection.prepareStatement("UPDATE test_rows SET grp = ?, val = ? WHERE id = ?")) {
        for (final long key : deletes) {
          delete.setLong(1, key);
          delete.executeUpdate();
          statements.add("DELETE " + key);
        }
        for (final Row row : inserts) {
          insert.setLong(1, row.id());
          insert.setInt(2, row.grp());
          insert.setString(3, row.val());
          insert.executeUpdate();
          statements.add("INSERT " + row.id());
        }
        final int[] updateCounts = new int[updates.size()];
        for (int i = 0; i < updates.size(); i++) {
          final Row row = updates.get(i);
          update.setInt(1, row.grp());
          update.setString(2, row.val());
          update.setLong(3, row.id());
          updateCounts[i] = update.executeUpdate();
          statements.add("UPDATE " + row.id());
        }
        if (failNextFlush) {
          failNextFlush = false;
          throw new SQLTransientException("injected failure after writing");
        }
        return updateCounts;
      }
    }

    private List<Row> selectGroup(final int grp) throws SQLException {
      final List<Row> rows = new ArrayList<>();
      try (final var statement =
          connection.prepareStatement(
              "SELECT id, grp, val FROM test_rows WHERE grp = ? ORDER BY id")) {
        statement.setInt(1, grp);
        try (final var result = statement.executeQuery()) {
          while (result.next()) {
            rows.add(new Row(result.getLong(1), result.getInt(2), result.getString(3)));
          }
        }
      }
      return rows;
    }
  }
}
