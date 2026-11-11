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

import java.time.Duration;
import java.util.ArrayList;
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

  private final InMemoryBackend backend = new InMemoryBackend();
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
  void shouldWriteATransactionInOneBackendTransaction() {
    // given
    backend.put(FIRST, bytes(2), bytes(20));
    final var writer = writer(1_000, Duration.ofMillis(1));

    // when
    writer.enqueue(transaction(version(1, 10, 1), version(2, null, 1)));

    // then
    await().untilAsserted(() -> assertThat(backend.transactionLog()).hasSize(1));
    assertThat(backend.transactionLog().getFirst())
        .containsExactly("FIRST [1]=[10]", "FIRST [2] deleted");
    assertThat(backend.get(FIRST, bytes(2))).isNull();
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
    await().untilAsserted(() -> assertThat(backend.transactionLog()).hasSize(2));
    assertThat(backend.transactionLog().get(0))
        .containsExactly("FIRST [1]=[11]", "FIRST [2]=[22]", "FIRST [3]=[32]");
    assertThat(backend.transactionLog().get(1))
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
    await().untilAsserted(() -> assertThat(backend.get(FIRST, bytes(1))).containsExactly(10));
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
    assertThat(backend.get(FIRST, bytes(2))).containsExactly(20);
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
  void shouldDropQueuedTransactionsOnClose() {
    // given
    final var writer = writer(1_000, STALLED);
    writer.enqueue(transaction(version(1, 10, 1)));

    // when
    writer.close();

    // then
    assertThat(backend.transactionLog()).isEmpty();
    assertThatThrownBy(() -> writer.enqueue(transaction(version(2, 20, 2))))
        .isInstanceOf(IllegalStateException.class);
  }

  private TransactionWriter<TestColumnFamily> writer(
      final int batchSize, final Duration maxWriteDelay) {
    final var writer =
        new TransactionWriter<>(
            backend.db(),
            dirtyIndex,
            new StampedLock(),
            new TestConfiguration().batchSize(batchSize).maxWriteDelay(maxWriteDelay).build(),
            flushes::incrementAndGet,
            failures::add);
    writers.add(writer);
    return writer;
  }

  @SafeVarargs
  private static CommittedTransaction transaction(
      final Map.Entry<CacheKey, CachedValue>... versions) {
    return CommittedTransaction.of(versions[0].getValue().seq(), List.of(versions));
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
}
