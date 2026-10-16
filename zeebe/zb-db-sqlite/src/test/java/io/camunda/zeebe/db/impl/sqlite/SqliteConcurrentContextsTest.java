/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.sqlite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.db.conformance.ConformanceDbTestBase;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.protocol.EnumValue;
import io.camunda.zeebe.protocol.ScopedColumnFamily;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The engine uses several contexts of one database at once: the stream processor writes with its
 * own, while scheduled tasks and queries read with theirs, on other actors. RocksDB tolerates this
 * because each context has its own write batch, so the SQLite backend has to as well.
 *
 * <p>The preconditions are on, to show that the guard against deleting ahead of a running iteration
 * only concerns the context that iterates.
 */
final class SqliteConcurrentContextsTest extends ConformanceDbTestBase {

  @TempDir Path scratch;

  private ExecutorService otherThread;
  private TransactionContext otherContext;

  @Override
  protected <T extends Enum<? extends EnumValue> & EnumValue & ScopedColumnFamily>
      ZeebeDbFactory<T> factory() {
    return new SqliteDbFactory<>(new ConsistencyChecksSettings(true, false));
  }

  @BeforeEach
  void setUp() {
    otherThread = Executors.newSingleThreadExecutor();
    otherContext = db.createContext();
  }

  @AfterEach
  void tearDown() {
    otherThread.shutdownNow();
  }

  private ColumnFamily<DbLong, DbLong> otherColumnFamily(final Cf cf) {
    return db.createColumnFamily(cf, otherContext, new DbLong(), new DbLong());
  }

  @Test
  void shouldSeeOwnUncommittedWritesWhileOthersDoNot() {
    // given
    final var writer = longColumnFamily(Cf.ONE);
    final var reader = otherColumnFamily(Cf.ONE);

    // when
    final var seenByWriter = new boolean[1];
    final var seenByReader = new boolean[1];
    context.runInTransaction(
        () -> {
          writer.insert(dbLong(1), dbLong(10));
          seenByWriter[0] = writer.exists(dbLong(1));
          seenByReader[0] = reader.exists(dbLong(1));
        });

    // then
    assertThat(seenByWriter[0]).isTrue();
    assertThat(seenByReader[0]).isFalse();
    assertThat(reader.get(dbLong(1)).getValue()).isEqualTo(10);
  }

  @Test
  void shouldShowWritesToReaderOnlyAfterExplicitCommit() throws Exception {
    // given
    final var writer = longColumnFamily(Cf.ONE);
    final var reader = otherColumnFamily(Cf.ONE);

    // when
    final var transaction = context.getCurrentTransaction();
    writer.insert(dbLong(1), dbLong(10));

    // then
    assertThat(writer.exists(dbLong(1))).isTrue();
    assertThat(reader.exists(dbLong(1))).isFalse();

    transaction.commit();
    assertThat(reader.exists(dbLong(1))).isTrue();
  }

  @Test
  void shouldHideRolledBackWritesFromReaderAndReleaseWriter() throws Exception {
    // given
    final var writer = longColumnFamily(Cf.ONE);
    final var reader = otherColumnFamily(Cf.ONE);

    // when
    final var transaction = context.getCurrentTransaction();
    writer.insert(dbLong(1), dbLong(10));
    transaction.rollback();

    // then
    assertThat(reader.exists(dbLong(1))).isFalse();
    assertThat(writer.exists(dbLong(1))).isFalse();
    reader.insert(dbLong(2), dbLong(20));
    assertThat(writer.exists(dbLong(2))).isTrue();
  }

  @Test
  void shouldIterateCommittedSnapshotWhileWriterMutates() throws Exception {
    // given
    final var writer = longColumnFamily(Cf.ONE);
    final var reader = otherColumnFamily(Cf.ONE);
    for (long key = 1; key <= 5; key++) {
      writer.insert(dbLong(key), dbLong(key * 10));
    }
    final var readerAtThirdRow = new AtomicBoolean();
    final var writerCommitted = new AtomicBoolean();
    final var visited = new ArrayList<Long>();

    // when the reader is in the middle of its iteration, the writer replaces all rows
    final var iteration =
        otherThread.submit(
            () ->
                reader.forEach(
                    (key, value) -> {
                      visited.add(key.getValue());
                      if (key.getValue() == 3) {
                        readerAtThirdRow.set(true);
                        await().untilTrue(writerCommitted);
                      }
                    }));
    await().untilTrue(readerAtThirdRow);
    context.runInTransaction(
        () -> {
          for (long key = 1; key <= 5; key++) {
            writer.deleteExisting(dbLong(key));
          }
          for (long key = 6; key <= 10; key++) {
            writer.insert(dbLong(key), dbLong(key * 10));
          }
        });
    writerCommitted.set(true);
    await().until(iteration::isDone);
    iteration.get();

    // then the running iteration saw the state before the commit, and the next one sees the commit
    assertThat(visited).containsExactly(1L, 2L, 3L, 4L, 5L);
    final var next = new ArrayList<Long>();
    reader.forEach((key, value) -> next.add(key.getValue()));
    assertThat(next).containsExactly(6L, 7L, 8L, 9L, 10L);
  }

  @Test
  void shouldIterateWhileWriterHoldsOpenTransaction() throws Exception {
    // given
    final var writer = longColumnFamily(Cf.ONE);
    final var reader = otherColumnFamily(Cf.ONE);
    writer.insert(dbLong(1), dbLong(10));

    // when the writer's transaction is open and has uncommitted changes
    final var visited = new ArrayList<Long>();
    context.runInTransaction(
        () -> {
          writer.insert(dbLong(2), dbLong(20));
          writer.deleteExisting(dbLong(1));
          final Future<?> iteration =
              otherThread.submit(() -> reader.forEach((key, value) -> visited.add(key.getValue())));
          await().until(iteration::isDone);
          try {
            iteration.get();
          } catch (final Exception e) {
            throw new IllegalStateException(e);
          }
        });

    // then the reader was neither blocked nor shown the uncommitted changes
    assertThat(visited).containsExactly(1L);
  }

  @Test
  void shouldKeepIteratingWhenWriterDeletesRowsAheadOfReader() throws Exception {
    // given
    final var writer = longColumnFamily(Cf.ONE);
    final var reader = otherColumnFamily(Cf.ONE);
    for (long key = 1; key <= 5; key++) {
      writer.insert(dbLong(key), dbLong(key));
    }
    final var readerAtFirstRow = new AtomicBoolean();
    final var writerCommitted = new AtomicBoolean();
    final var visited = new ArrayList<Long>();

    // when the writer deletes rows that the reader has yet to visit
    final var iteration =
        otherThread.submit(
            () ->
                reader.forEach(
                    (key, value) -> {
                      visited.add(key.getValue());
                      if (key.getValue() == 1) {
                        readerAtFirstRow.set(true);
                        await().untilTrue(writerCommitted);
                      }
                    }));
    await().untilTrue(readerAtFirstRow);
    writer.deleteExisting(dbLong(4));
    writer.deleteExisting(dbLong(5));
    writerCommitted.set(true);
    await().until(iteration::isDone);

    // then the delete is not rejected by the guard of the reader's iteration
    iteration.get();
    assertThat(visited).containsExactly(1L, 2L, 3L, 4L, 5L);
  }

  @Test
  void shouldJoinOuterTransactionInEachContext() {
    // given
    final var writer = longColumnFamily(Cf.ONE);
    final var reader = otherColumnFamily(Cf.ONE);

    // when
    final var seenByReaderBeforeOuterCommit = new ArrayList<Boolean>();
    context.runInTransaction(
        () -> {
          writer.insert(dbLong(1), dbLong(10));
          context.runInTransaction(() -> writer.insert(dbLong(2), dbLong(20)));
          otherContext.runInTransaction(
              () ->
                  otherContext.runInTransaction(
                      () -> {
                        seenByReaderBeforeOuterCommit.add(reader.exists(dbLong(1)));
                        seenByReaderBeforeOuterCommit.add(reader.exists(dbLong(2)));
                      }));
        });

    // then only the outermost call of the writer committed
    assertThat(seenByReaderBeforeOuterCommit).containsExactly(false, false);
    assertThat(reader.exists(dbLong(1))).isTrue();
    assertThat(reader.exists(dbLong(2))).isTrue();
  }

  @Test
  void shouldRollBackNestedWritesWhenOutermostTransactionFails() {
    // given
    final var writer = longColumnFamily(Cf.ONE);
    final var reader = otherColumnFamily(Cf.ONE);

    // when
    assertThatThrownBy(
            () ->
                context.runInTransaction(
                    () -> {
                      context.runInTransaction(() -> writer.insert(dbLong(1), dbLong(10)));
                      throw new IllegalArgumentException("fail");
                    }))
        .isInstanceOf(IllegalArgumentException.class);

    // then
    assertThat(reader.exists(dbLong(1))).isFalse();
    assertThat(writer.exists(dbLong(1))).isFalse();
  }

  @Test
  void shouldLetContextsTakeTurnsWriting() {
    // given
    final var first = longColumnFamily(Cf.ONE);
    final var second = otherColumnFamily(Cf.ONE);

    // when
    first.insert(dbLong(1), dbLong(10));
    second.insert(dbLong(2), dbLong(20));
    first.insert(dbLong(3), dbLong(30));
    second.insert(dbLong(4), dbLong(40));

    // then
    assertThat(first.count()).isEqualTo(4);
    assertThat(second.count()).isEqualTo(4);
  }

  @Test
  void shouldMakeSecondWriterWaitForFirstToCommit() throws Exception {
    // given
    final var first = longColumnFamily(Cf.ONE);
    final var second = otherColumnFamily(Cf.ONE);

    // when the second context writes while the first one has a transaction with writes open
    final Future<?>[] secondWrite = new Future<?>[1];
    final var waitedForFirst = new boolean[1];
    context.runInTransaction(
        () -> {
          first.insert(dbLong(1), dbLong(10));
          secondWrite[0] = otherThread.submit(() -> second.insert(dbLong(2), dbLong(20)));
          waitedForFirst[0] = stillRunningFor(secondWrite[0], Duration.ofMillis(300));
        });

    // then it proceeds once the first commits
    assertThat(waitedForFirst[0]).isTrue();
    await().until(() -> secondWrite[0].isDone());
    secondWrite[0].get();
    assertThat(first.count()).isEqualTo(2);
  }

  @Test
  void shouldFailWithClearMessageWhenWriterDoesNotCommitInTime() {
    // given a database that waits briefly for the write lease
    final var shortLeaseDb =
        SqliteDb.<Cf>open(
            scratch.resolve("short-lease"),
            new SimpleMeterRegistry(),
            new ConsistencyChecksSettings(),
            Duration.ofMillis(200));
    try {
      final var first = shortLeaseDb.createContext();
      final var second = shortLeaseDb.createContext();
      final var firstCf =
          shortLeaseDb.createColumnFamily(Cf.ONE, first, new DbLong(), new DbLong());
      final var secondCf =
          shortLeaseDb.createColumnFamily(Cf.ONE, second, new DbLong(), new DbLong());

      // when
      first.runInTransaction(
          () -> {
            firstCf.insert(dbLong(1), dbLong(10));

            // then
            assertThatThrownBy(() -> secondCf.insert(dbLong(2), dbLong(20)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("write lease")
                .hasMessageNotContaining("BUSY");
          });
      assertThat(secondCf.exists(dbLong(1))).isTrue();
      assertThat(secondCf.exists(dbLong(2))).isFalse();
    } finally {
      shortLeaseDb.close();
    }
  }

  @Test
  void shouldSnapshotCommittedStateWhileWriterHasOpenTransaction() throws Exception {
    // given
    final var writer = longColumnFamily(Cf.ONE);
    final var reader = otherColumnFamily(Cf.ONE);
    writer.insert(dbLong(1), dbLong(10));
    reader.exists(dbLong(1));
    final var snapshotDir = scratch.resolve("snapshot").toFile();

    // when a snapshot is taken in the middle of a transaction with writes
    context.runInTransaction(
        () -> {
          writer.insert(dbLong(2), dbLong(20));
          db.createSnapshot(snapshotDir);
        });

    // then it holds the committed state only
    try (final var snapshot = this.<Cf>factory().createDb(snapshotDir)) {
      final var restored =
          snapshot.createColumnFamily(Cf.ONE, snapshot.createContext(), new DbLong(), new DbLong());
      assertThat(restored.exists(dbLong(1))).isTrue();
      assertThat(restored.exists(dbLong(2))).isFalse();
    }
    assertThat(writer.exists(dbLong(2))).isTrue();
  }

  @Test
  void shouldRejectContextOfAnotherDatabase() throws Exception {
    // given
    try (final var other = this.<Cf>factory().createDb(scratch.resolve("other").toFile())) {
      final var foreignContext = other.createContext();

      // then
      assertThatThrownBy(
              () -> db.createColumnFamily(Cf.ONE, foreignContext, new DbLong(), new DbLong()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("created by this SQLite database");
    }
  }

  @Test
  void shouldKeepReadingCommittedStateWhenWriterIsMidTransactionAcrossColumnFamilies() {
    // given
    final var writerOne = longColumnFamily(Cf.ONE);
    final var writerTwo = longColumnFamily(Cf.TWO);
    final var readerOne = otherColumnFamily(Cf.ONE);
    final var readerTwo = otherColumnFamily(Cf.TWO);
    writerOne.insert(dbLong(1), dbLong(1));
    final List<Long> counts = new ArrayList<>();

    // when
    context.runInTransaction(
        () -> {
          writerOne.insert(dbLong(2), dbLong(2));
          writerTwo.insert(dbLong(1), dbLong(1));
          counts.add(readerOne.count());
          counts.add(readerTwo.count());
        });
    counts.add(readerOne.count());
    counts.add(readerTwo.count());

    // then
    assertThat(counts).containsExactly(1L, 0L, 2L, 1L);
  }

  private static boolean stillRunningFor(final Future<?> future, final Duration duration) {
    try {
      await().during(duration).atMost(duration.plusSeconds(5)).until(() -> !future.isDone());
      return true;
    } catch (final org.awaitility.core.ConditionTimeoutException e) {
      return false;
    }
  }
}
