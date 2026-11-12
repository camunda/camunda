/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.cache;

import static io.camunda.zeebe.db.cache.TestColumnFamily.FIRST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

import io.camunda.zeebe.db.RawBatchWriter;
import io.camunda.zeebe.db.RawWrite;
import io.camunda.zeebe.db.ZeebeDb;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.StampedLock;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

final class TransactionWriterTest {

  private static final Duration STALLED = Duration.ofHours(1);

  private final InMemoryStorage storage = new InMemoryStorage();
  private final DirtyIndex dirtyIndex = new DirtyIndex();
  private final AtomicInteger flushes = new AtomicInteger();
  private final List<Throwable> failures = new CopyOnWriteArrayList<>();
  private final List<TransactionWriter<TestColumnFamily>> writers = new ArrayList<>();

  @AfterEach
  void closeWriters() {
    writers.forEach(TransactionWriter::close);
    assertThat(failures).isEmpty();
  }

  @Test
  void shouldWriteATransactionInOneStorageTransaction() {
    // given
    storage.put(FIRST, bytes(2), bytes(20));
    final var writer = writer(1_000, Duration.ofMillis(1));

    // when
    writer.enqueue(transaction(version(1, 10, 1), version(2, null, 1)));

    // then
    await().untilAsserted(() -> assertThat(storage.transactionLog()).hasSize(1));
    assertThat(storage.transactionLog().getFirst())
        .containsExactly("FIRST [1]=[10]", "FIRST [2] deleted");
    assertThat(storage.get(FIRST, bytes(2))).isNull();
  }

  @Test
  void shouldGroupWholeTransactionsAndWriteEachKeyOnceWithItsNewestVersion() {
    // given
    final var writer = writer(4, STALLED);

    // when - 4 queued keys close the first group; the 5-key transaction exceeds it and goes alone
    writer.enqueue(transaction(version(1, 11, 1), version(2, 21, 1)));
    writer.enqueue(transaction(version(2, 22, 2), version(3, 32, 2)));
    writer.enqueue(
        transaction(
            version(1, 13, 3),
            version(2, 23, 3),
            version(3, 33, 3),
            version(4, 43, 3),
            version(5, 53, 3)));

    // then
    await().untilAsserted(() -> assertThat(storage.transactionLog()).hasSize(2));
    assertThat(storage.transactionLog().get(0))
        .containsExactly("FIRST [1]=[11]", "FIRST [2]=[22]", "FIRST [3]=[32]");
    assertThat(storage.transactionLog().get(1))
        .containsExactly(
            "FIRST [1]=[13]",
            "FIRST [2]=[23]",
            "FIRST [3]=[33]",
            "FIRST [4]=[43]",
            "FIRST [5]=[53]");
    assertThat(writer.queuedBytes()).isZero();
  }

  @Test
  void shouldWriteOnceTheOldestTransactionWaitedMaxWriteDelay() {
    // given
    final var writer = writer(1_000, Duration.ofMillis(10));

    // when
    writer.enqueue(transaction(version(1, 10, 1)));

    // then
    await().untilAsserted(() -> assertThat(storage.get(FIRST, bytes(1))).containsExactly(10));
  }

  @Test
  void shouldWriteUpToARequestedSeqWithoutWaitingOutMaxWriteDelay() {
    // given
    final var writer = writer(1_000, STALLED);
    writer.enqueue(transaction(version(1, 10, 1)));
    writer.enqueue(transaction(version(2, 20, 2)));

    // when
    writer.flushUpTo(2);

    // then
    await().untilAsserted(() -> assertThat(writer.flushedSeq()).isEqualTo(2));
    assertThat(writer.lastQueuedSeq()).isEqualTo(2);
    assertThat(storage.get(FIRST, bytes(2))).containsExactly(20);
  }

  @Test
  void shouldRemoveWrittenVersionsFromTheDirtyIndexButKeepNewerOnes() {
    // given - key 1 was overwritten by a transaction that isn't queued yet
    final var writer = writer(1, Duration.ofMillis(1));
    final var first = version(1, 10, 1);
    final var second = version(2, 20, 1);
    final var newer = new CachedValue(bytes(11), 2);
    dirtyIndex.put(first.getKey(), newer);
    dirtyIndex.put(second.getKey(), second.getValue());

    // when
    writer.enqueue(transaction(first, second));

    // then
    await().untilAsserted(() -> assertThat(flushes).hasValue(1));
    assertThat(dirtyIndex.get(first.getKey())).isSameAs(newer);
    assertThat(dirtyIndex.get(second.getKey())).isNull();
  }

  @Test
  void shouldWriteOnlyTransactionsWhoseRecordsTheLogCommitted() {
    // given
    final var gate = new TestCommitGate();
    final var writer = writer(1_000, STALLED, gate);
    writer.enqueue(transactionAt(10, version(1, 11, 1)));
    writer.enqueue(transactionAt(20, version(2, 22, 2)));
    writer.flushUpTo(2);

    // when
    gate.commit(10);

    // then
    await().untilAsserted(() -> assertThat(storage.transactionLog()).hasSize(1));
    assertThat(storage.transactionLog().getFirst()).containsExactly("FIRST [1]=[11]");
    assertThat(writer.flushedSeq()).isEqualTo(1);

    // when
    gate.commit(20);

    // then
    await().untilAsserted(() -> assertThat(writer.flushedSeq()).isEqualTo(2));
    assertThat(storage.transactionLog().get(1)).containsExactly("FIRST [2]=[22]");
    assertThat(writer.queuedBytes()).isZero();
  }

  @Test
  void shouldCountOnlyCommittedTransactionsTowardsTheBatchSize() {
    // given - the writer saw one committed and one held-back transaction, which together have
    // enough keys to close a group
    final var gate = new TestCommitGate();
    gate.commit(10);
    final var writer = writer(2, STALLED, gate);
    writer.enqueue(transactionAt(10, version(1, 11, 1)));
    writer.enqueue(transactionAt(20, version(2, 22, 2)));
    final int reads = gate.committedPositionReads();
    await().until(() -> gate.committedPositionReads() > reads);

    // when
    gate.commit(20);

    // then
    await().untilAsserted(() -> assertThat(storage.transactionLog()).hasSize(1));
    assertThat(storage.transactionLog().getFirst())
        .containsExactly("FIRST [1]=[11]", "FIRST [2]=[22]");
  }

  @Test
  void shouldStopListeningToTheLogOnceClosed() {
    // given
    final var gate = new TestCommitGate();
    final var writer = writer(1_000, STALLED, gate);
    assertThat(gate.listeners()).hasSize(1);

    // when
    writer.close();

    // then
    assertThat(gate.listeners()).isEmpty();
  }

  @Test
  void shouldWriteAGroupAsOneRawBatchWithThePositionsOfItsTransactions() {
    // given
    final List<RawBatch> batches = new CopyOnWriteArrayList<>();
    final var writer = writer(rawBatchStorage(batches), 1_000, STALLED, NoCommitGate.INSTANCE);
    writer.enqueue(transactionAt(10, version(2, 21, 1), version(1, 11, 1)));
    writer.enqueue(transactionAt(20, version(2, 22, 2), version(3, null, 2)));

    // when
    writer.flushUpTo(2);

    // then
    await().untilAsserted(() -> assertThat(writer.flushedSeq()).isEqualTo(2));
    assertThat(batches)
        .containsExactly(
            new RawBatch(
                20,
                List.of(
                    "FIRST [1]=[11] at 10", "FIRST [2]=[22] at 20", "FIRST [3] deleted at 20")));
  }

  @Test
  void shouldDropQueuedTransactionsOnClose() {
    // given
    final var writer = writer(1_000, STALLED);
    writer.enqueue(transaction(version(1, 10, 1)));

    // when
    writer.close();

    // then
    assertThat(storage.transactionLog()).isEmpty();
    assertThatThrownBy(() -> writer.enqueue(transaction(version(2, 20, 2))))
        .isInstanceOf(IllegalStateException.class);
  }

  private TransactionWriter<TestColumnFamily> writer(
      final int batchSize, final Duration maxWriteDelay) {
    return writer(batchSize, maxWriteDelay, NoCommitGate.INSTANCE);
  }

  private TransactionWriter<TestColumnFamily> writer(
      final int batchSize, final Duration maxWriteDelay, final CommitGate commitGate) {
    return writer(storage.db(), batchSize, maxWriteDelay, commitGate);
  }

  private TransactionWriter<TestColumnFamily> writer(
      final ZeebeDb<TestColumnFamily> db,
      final int batchSize,
      final Duration maxWriteDelay,
      final CommitGate commitGate) {
    final var writer =
        new TransactionWriter<>(
            db,
            dirtyIndex,
            new StampedLock(),
            new TestConfiguration().batchSize(batchSize).maxWriteDelay(maxWriteDelay).build(),
            commitGate,
            new WriteBehindMetrics(storage.meterRegistry()),
            flushes::incrementAndGet,
            failures::add);
    writers.add(writer);
    return writer;
  }

  /** A storage that only takes raw batches, and records them. */
  @SuppressWarnings("unchecked")
  private static ZeebeDb<TestColumnFamily> rawBatchStorage(final List<RawBatch> batches) {
    final ZeebeDb<TestColumnFamily> db =
        mock(ZeebeDb.class, withSettings().extraInterfaces(RawBatchWriter.class));
    doAnswer(
            invocation -> {
              final List<String> writes = new ArrayList<>();
              for (final RawWrite write : invocation.<List<RawWrite>>getArgument(0)) {
                final var value = write.value();
                writes.add(
                    write.columnFamily()
                        + " "
                        + Arrays.toString(write.key())
                        + (value == null ? " deleted" : "=" + Arrays.toString(value))
                        + " at "
                        + write.position());
              }
              batches.add(new RawBatch(invocation.<Long>getArgument(1), writes));
              return null;
            })
        .when((RawBatchWriter) db)
        .write(any(), anyLong());
    return db;
  }

  @SafeVarargs
  private static CommittedTransaction transaction(
      final Map.Entry<CacheKey, CachedValue>... versions) {
    return transactionAt(0, versions);
  }

  @SafeVarargs
  private static CommittedTransaction transactionAt(
      final long position, final Map.Entry<CacheKey, CachedValue>... versions) {
    return CommittedTransaction.of(versions[0].getValue().seq(), position, List.of(versions));
  }

  private static Map.Entry<CacheKey, CachedValue> version(
      final int key, final @Nullable Integer value, final long seq) {
    return Map.entry(
        new CacheKey(FIRST, bytes(key)), new CachedValue(value == null ? null : bytes(value), seq));
  }

  private static byte[] bytes(final int... values) {
    final var bytes = new byte[values.length];
    for (int i = 0; i < values.length; i++) {
      bytes[i] = (byte) values[i];
    }
    return bytes;
  }

  private record RawBatch(long position, List<String> writes) {}
}
