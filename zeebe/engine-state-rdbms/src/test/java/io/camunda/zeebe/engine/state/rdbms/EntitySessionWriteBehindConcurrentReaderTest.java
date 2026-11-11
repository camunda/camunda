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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * A second session of the same store reads while the flusher writes and a commit publishes. Every
 * commit rewrites all rows, or deletes all of them, so the rows of one read must all come from the
 * same commit. A read must not show the rows of a rolled back transaction, a commit that did not
 * start yet, or a state older than an earlier read of the same reader.
 */
final class EntitySessionWriteBehindConcurrentReaderTest {

  private static final EntityType<String, PartitionStateRow> ROW =
      new EntityType<>(
          PartitionStateMapper.class, "PartitionState", "reader row", PartitionStateRow::key);

  private static final Comparator<PartitionStateRow> BY_KEY =
      Comparator.comparing(PartitionStateRow::key);
  private static final int KEYS = 20;
  private static final int COMMITS = 1_500;

  private static EngineStateDatabase database;

  @BeforeAll
  static void startDatabase() throws Exception {
    database = EngineStateDatabase.start();
  }

  @AfterAll
  static void stopDatabase() {
    database.close();
  }

  @ParameterizedTest(name = "flush delay {0} ms")
  @ValueSource(ints = {0, 3})
  void shouldNeverReadAStateThatWasNotCommitted(final int flushDelayMillis) throws Exception {
    // given
    final int partitionId = database.nextPartitionId();
    final var store =
        new WriteBehindStore<>(
            new JdbcBackend(partitionId, database.sessionFactory()),
            WriteBehindConfiguration.defaults()
                .withKeyIndexColumnFamilies(Set.of())
                .withFlushDelay(Duration.ofMillis(flushDelayMillis)));
    final var writerContext = new JdbcTransactionContext();
    final var writer =
        new EntitySession(writerContext, partitionId, database.sessionFactory(), store, null);
    writerContext.register(writer);
    final var readerContext = new JdbcTransactionContext();
    final var reader =
        new EntitySession(readerContext, partitionId, database.sessionFactory(), store, null);
    readerContext.register(reader);

    final var started = new AtomicInteger();
    final var reads = new AtomicInteger();
    final var stopped = new AtomicBoolean();
    final var failure = new AtomicReference<Throwable>();
    final var readerThread =
        new Thread(
            () ->
                readUntilStopped(
                    reader, readerContext, partitionId, started, stopped, reads, failure));
    readerThread.start();

    // when - the commits go on while the reader reads
    try {
      writeCommits(writerContext, writer, started);
    } finally {
      stopped.set(true);
      readerThread.join();
      writerContext.getCurrentTransaction().rollback();
      store.close();
    }

    // then
    assertThat(failure.get()).isNull();
    assertThat(reads.get()).describedAs("reads of a committed state").isPositive();
  }

  @Test
  void shouldReadTheLastCommitAfterTheFlush() throws Exception {
    // given
    final int partitionId = database.nextPartitionId();
    final var store =
        new WriteBehindStore<>(
            new JdbcBackend(partitionId, database.sessionFactory()),
            WriteBehindConfiguration.defaults().withKeyIndexColumnFamilies(Set.of()));
    final var writerContext = new JdbcTransactionContext();
    final var writer =
        new EntitySession(writerContext, partitionId, database.sessionFactory(), store, null);
    writerContext.register(writer);
    writeCommits(writerContext, writer, new AtomicInteger());

    // when
    store.close();

    // then - the commit that deletes all rows is not the last, so the last one's rows are there
    assertThat(committedInDatabase(partitionId))
        .hasSize(KEYS)
        .allSatisfy(row -> assertThat(row.value()).isEqualTo("v" + COMMITS));
  }

  /**
   * Commit {@code n} sets every row to {@code vn}, or every seventh deletes them all. Every
   * seventeenth transaction writes rows and rolls back.
   */
  private static void writeCommits(
      final JdbcTransactionContext context,
      final EntitySession session,
      final AtomicInteger started)
      throws Exception {
    boolean exists = false;
    for (int commit = 1; commit <= COMMITS; commit++) {
      if (commit % 17 == 0) {
        final var doomed = context.getCurrentTransaction();
        for (int key = 0; key < KEYS; key++) {
          final var row = new PartitionStateRow(key(key), "rolled-back");
          if (exists) {
            session.update(ROW, row);
          } else {
            session.insert(ROW, row);
          }
        }
        doomed.rollback();
      }
      started.set(commit);
      final var transaction = context.getCurrentTransaction();
      final boolean deleteAll = commit % 7 == 0 && exists && commit != COMMITS;
      for (int key = 0; key < KEYS; key++) {
        if (deleteAll) {
          session.delete(ROW, key(key));
        } else if (exists) {
          session.update(ROW, new PartitionStateRow(key(key), "v" + commit));
        } else {
          session.insert(ROW, new PartitionStateRow(key(key), "v" + commit));
        }
      }
      transaction.commit();
      exists = !deleteAll;
    }
  }

  private void readUntilStopped(
      final EntitySession session,
      final JdbcTransactionContext context,
      final int partitionId,
      final AtomicInteger started,
      final AtomicBoolean stopped,
      final AtomicInteger reads,
      final AtomicReference<Throwable> failure) {
    int lastSeen = 0;
    try {
      while (!stopped.get()) {
        final var transaction = context.getCurrentTransaction();
        // the first read of a transaction takes the snapshot of the unflushed rows, before the SQL
        session.read(PartitionStateMapper.class, mapper -> 0);
        final var rows = session.query(ROW, committedInDatabase(partitionId), r -> true, BY_KEY);
        final int startedAfter = started.get();
        transaction.rollback();

        if (rows.isEmpty()) {
          continue;
        }
        assertThat(rows).describedAs("rows of one commit").hasSize(KEYS);
        final var value = rows.getFirst().value();
        assertThat(rows).extracting(PartitionStateRow::value).containsOnly(value);
        assertThat(value).startsWith("v");
        final int commit = Integer.parseInt(value.substring(1));
        assertThat(commit)
            .describedAs("a commit that was not started when the read finished")
            .isLessThanOrEqualTo(startedAfter);
        assertThat(commit)
            .describedAs("a state older than an earlier read")
            .isGreaterThanOrEqualTo(lastSeen);
        lastSeen = commit;
        reads.incrementAndGet();
      }
    } catch (final Throwable t) {
      failure.set(t);
    }
  }

  private static String key(final int key) {
    return "k%02d".formatted(key);
  }

  private List<PartitionStateRow> committedInDatabase(final int partitionId) throws SQLException {
    final List<PartitionStateRow> rows = new ArrayList<>();
    try (final var connection = database.dataSource().getConnection();
        final var statement =
            connection.prepareStatement(
                "SELECT state_key, value FROM CAM_RU_PARTITION_STATE"
                    + " WHERE partition_id = ? ORDER BY state_key")) {
      statement.setInt(1, partitionId);
      try (final var result = statement.executeQuery()) {
        while (result.next()) {
          rows.add(new PartitionStateRow(result.getString(1), result.getString(2)));
        }
      }
    }
    return rows;
  }
}
