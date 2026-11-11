/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.rdbms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.db.impl.jdbc.JdbcTransactionContext;
import io.camunda.zeebe.db.writebehind.WriteBehindConfiguration;
import io.camunda.zeebe.db.writebehind.WriteBehindStore;
import io.camunda.zeebe.engine.state.rdbms.PartitionStateMapper.PartitionStateRow;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Makes the flusher's writes to PostgreSQL fail, by failing the commit of the connections the
 * flusher borrows. The rows of a failed write must stay readable from the store, the flusher must
 * retry, and a flush must not report success before the database holds the rows.
 */
final class EntitySessionWriteBehindFlushFailureTest {

  private static final String FLUSHER_THREAD = "zb-db-write-behind";

  private static final EntityType<String, PartitionStateRow> ROW =
      new EntityType<>(
          PartitionStateMapper.class, "PartitionState", "test row", PartitionStateRow::key);

  private static EngineStateDatabase database;

  private final AtomicInteger commitAttempts = new AtomicInteger();
  private volatile boolean failCommits;
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
    database.observed().reset();
    database.observed().decorate(this::failingCommits);
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
    failCommits = false;
    context.getCurrentTransaction().rollback();
    try {
      store.close();
    } catch (final RuntimeException ignored) {
      // a test that exhausts the retries leaves a failed store, which can't flush any more
    }
    database.observed().reset();
  }

  @Test
  void shouldKeepAndRetryTheRowsOfAFailedFlush() throws Exception {
    // given - committed rows, and a database that rejects every write for now
    failCommits = true;
    commit(row("1", "a"), row("2", "b"));

    // when - a flush is requested
    final var flush = CompletableFuture.runAsync(store::awaitFlushed);
    Awaitility.await().until(() -> commitAttempts.get() >= 2);

    // then - the flusher retries, the flush does not report success, the database holds nothing,
    // and the rows are still readable
    assertThat(flush).isNotDone();
    assertThat(committedRows()).isEmpty();
    assertThat(session.get(ROW, "1")).isEqualTo(row("1", "a"));
    assertThat(
            session.query(
                ROW, List.of(), row -> true, Comparator.comparing(PartitionStateRow::key)))
        .containsExactly(row("1", "a"), row("2", "b"));

    // when - the database accepts the writes again
    failCommits = false;
    flush.get();

    // then
    assertThat(committedRows()).containsExactly(row("1", "a"), row("2", "b"));
    assertThat(session.get(ROW, "1")).isEqualTo(row("1", "a"));
  }

  @Test
  void shouldAcceptCommitsWhileTheFlushFails() throws Exception {
    // given - a database that rejects every write for now
    failCommits = true;
    commit(row("1", "a"));
    final var flush = CompletableFuture.runAsync(store::awaitFlushed);
    Awaitility.await().until(() -> commitAttempts.get() >= 1);

    // when - the processing goes on
    update(row("1", "b"));
    commit(row("2", "c"));

    // then - it reads its own commits
    assertThat(session.get(ROW, "1")).isEqualTo(row("1", "b"));
    assertThat(session.get(ROW, "2")).isEqualTo(row("2", "c"));

    // when
    failCommits = false;
    flush.get();
    store.awaitFlushed();

    // then - the database holds the latest rows, as one flush wrote them
    assertThat(committedRows()).containsExactly(row("1", "b"), row("2", "c"));
  }

  @Test
  void shouldFailTheFlushAndTheStoreWhenTheRetriesAreExhausted() throws Exception {
    // given - a database that rejects every write
    failCommits = true;
    commit(row("1", "a"));

    // when - then - the flush fails, instead of reporting rows that are not in the database
    assertThatThrownBy(store::awaitFlushed).isInstanceOf(RuntimeException.class);
    assertThat(committedRows()).isEmpty();

    // then - and the store refuses further work, so that the partition doesn't go on without
    // a database that follows it
    assertThatThrownBy(() -> commit(row("2", "b"))).isInstanceOf(RuntimeException.class);
  }

  private Connection failingCommits(final Connection delegate) {
    return (Connection)
        Proxy.newProxyInstance(
            Connection.class.getClassLoader(),
            new Class<?>[] {Connection.class},
            (proxy, method, args) -> {
              if (method.getName().equals("commit")
                  && Thread.currentThread().getName().equals(FLUSHER_THREAD)) {
                commitAttempts.incrementAndGet();
                if (failCommits) {
                  throw new SQLException("the database rejects the write");
                }
              }
              try {
                return method.invoke(delegate, args);
              } catch (final InvocationTargetException e) {
                throw e.getCause();
              }
            });
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
    try (final var connection = database.dataSource().getConnection();
        final var statement =
            connection.prepareStatement(
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
