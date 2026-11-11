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
import static org.awaitility.Awaitility.await;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

final class WriteBehindStoreConcurrencyTest {

  private static final int COMMITS = 2_000;
  private static final Duration QUICK = Duration.ofMillis(1);

  private final InMemoryStorage storage = new InMemoryStorage();
  private final List<WriteBehindStore<TestColumnFamily>> stores = new ArrayList<>();

  @AfterEach
  void closeStores() {
    storage.resumeTransactions();
    stores.forEach(WriteBehindStore::close);
  }

  @Test
  void shouldShowReadersAllOfATransactionOrNoneOfIt() throws Exception {
    // given
    final var store = store(new TestConfiguration().maxWriteDelay(QUICK).batchSize(10), 3);
    final var reader = reader();

    // when - sorted, so key 1 is published before key 2, which a torn publication would expose
    final var committer =
        inBackground(
            () -> {
              for (int n = 1; n <= COMMITS; n++) {
                store.commit(
                    new TreeMap<>(
                        Map.of(key(1), new Op(intBytes(n)), key(2), new Op(intBytes(n)))));
              }
            });
    final List<String> tornReads = new ArrayList<>();
    while (!committer.isDone()) {
      final int first = intValue(store.get(key(1), reader));
      final int second = intValue(store.get(key(2), reader));
      if (second < first) {
        tornReads.add("key 1 at " + first + ", then key 2 at " + second);
      }
    }

    // then
    assertThat(committer).succeedsWithin(Duration.ofSeconds(30));
    assertThat(tornReads).isEmpty();
  }

  @Test
  void shouldNeverReadAnOlderVersionAfterANewerOne() {
    // given - a small cache, so the key is evicted and loaded again while versions are flushed
    final var store =
        store(
            new TestConfiguration().cacheMaxBytes(2 * 1024).maxWriteDelay(QUICK).batchSize(10), 3);
    final var reader = reader();

    // when
    final var committer =
        inBackground(
            () -> {
              for (int n = 1; n <= COMMITS; n++) {
                store.commit(
                    Map.of(key(1), new Op(intBytes(n)), key(100 + n % 50), new Op(new byte[200])));
              }
            });
    final List<String> backwardReads = new ArrayList<>();
    int last = 0;
    while (!committer.isDone()) {
      final int value = intValue(store.get(key(1), reader));
      if (value < last) {
        backwardReads.add(last + " then " + value);
      }
      last = value;
    }

    // then
    assertThat(committer).succeedsWithin(Duration.ofSeconds(30));
    assertThat(backwardReads).isEmpty();
    await()
        .untilAsserted(() -> assertThat(intValue(storage.get(FIRST, bytes(1)))).isEqualTo(COMMITS));
    assertThat(intValue(store.get(key(1), reader))).isEqualTo(COMMITS);
  }

  @Test
  void shouldKeepTheNewerVersionWhenItIsCommittedWhileTheOlderOneIsWritten() throws Exception {
    // given
    final var store =
        store(new TestConfiguration().cacheMaxBytes(2 * 1024).maxWriteDelay(QUICK).batchSize(1), 3);
    final var reader = reader();
    storage.pauseTransactions();
    store.commit(Map.of(key(1), new Op(bytes(1))));
    storage.awaitPausedTransaction();

    // when - overwrite while the writer holds the older version, then evict it from the cache
    store.commit(Map.of(key(1), new Op(bytes(2))));
    for (int i = 0; i < 50; i++) {
      store.commit(Map.of(key(100 + i), new Op(new byte[200])));
    }
    storage.resumeTransactions();

    // then
    assertThat(store.get(key(1), reader)).containsExactly(2);
    await().untilAsserted(() -> assertThat(storage.get(FIRST, bytes(1))).containsExactly(2));
    assertThat(store.get(key(1), reader)).containsExactly(2);
  }

  @Test
  void shouldNotMakeAReadOnlyCommitWaitBehindABlockedCommitter() throws Exception {
    // given
    final var store =
        store(new TestConfiguration().maxWriteDelay(QUICK).batchSize(1).maxPendingBytes(0), 3);
    final var committer = blockedCommitter(store);

    // when
    final var readOnly = CompletableFuture.runAsync(() -> store.commit(Map.of()));

    // then
    assertThat(readOnly).succeedsWithin(Duration.ofSeconds(5));
    assertThat(committer.done()).isNotDone();
    storage.resumeTransactions();
    assertThat(committer.done()).succeedsWithin(Duration.ofSeconds(10));
  }

  @Test
  void shouldReadUnderTheLockWhileACommitterIsBlocked() throws Exception {
    // given - no optimistic attempts, so every read takes the read lock and skips the cache
    storage.put(FIRST, bytes(9), bytes(90));
    final var store =
        store(new TestConfiguration().maxWriteDelay(QUICK).batchSize(1).maxPendingBytes(0), 0);
    final var reader = reader();
    final var committer = blockedCommitter(store);

    // when
    final var reads =
        CompletableFuture.supplyAsync(
            () -> List.of(store.get(key(1), reader), store.get(key(9), reader)));

    // then
    assertThat(reads)
        .succeedsWithin(Duration.ofSeconds(5))
        .satisfies(
            values -> {
              assertThat(values.get(0)).containsExactly(1);
              assertThat(values.get(1)).containsExactly(90);
            });
    storage.resumeTransactions();
    assertThat(committer.done()).succeedsWithin(Duration.ofSeconds(10));
  }

  /**
   * Starts a committer that ends up blocked on {@code maxPendingBytes}, which the store sets to 0:
   * the writer is paused writing the first commit, and the second commit waits for the writer while
   * holding the commit lock.
   */
  private Background blockedCommitter(final WriteBehindStore<TestColumnFamily> store)
      throws InterruptedException {
    storage.pauseTransactions();
    final var committer =
        inBackgroundThread(
            () -> {
              for (int i = 1; i <= 2; i++) {
                store.commit(Map.of(key(i), new Op(bytes(i))));
              }
            });
    storage.awaitPausedTransaction();
    await()
        .until(
            () ->
                !committer.done().isDone()
                    && committer.thread().getState() == Thread.State.WAITING);
    return committer;
  }

  private WriteBehindStore<TestColumnFamily> store(
      final TestConfiguration configuration, final int optimisticReadAttempts) {
    final var store =
        new WriteBehindStore<>(storage.db(), configuration.build(), optimisticReadAttempts);
    stores.add(store);
    return store;
  }

  private StorageColumnFamilies<TestColumnFamily> reader() {
    return new StorageColumnFamilies<>(storage.db(), storage.db().createContext());
  }

  private static CompletableFuture<Void> inBackground(final Runnable runnable) {
    return inBackgroundThread(runnable).done();
  }

  private static Background inBackgroundThread(final Runnable runnable) {
    final var done = new CompletableFuture<Void>();
    final var thread =
        new Thread(
            () -> {
              try {
                runnable.run();
                done.complete(null);
              } catch (final Throwable e) {
                done.completeExceptionally(e);
              }
            });
    thread.start();
    return new Background(thread, done);
  }

  private static CacheKey key(final int key) {
    return new CacheKey(FIRST, bytes(key));
  }

  private static byte[] intBytes(final int value) {
    return ByteBuffer.allocate(Integer.BYTES).putInt(value).array();
  }

  private static int intValue(final byte @Nullable [] bytes) {
    return bytes == null ? 0 : ByteBuffer.wrap(bytes).getInt();
  }

  private static byte[] bytes(final int... values) {
    final var bytes = new byte[values.length];
    for (int i = 0; i < values.length; i++) {
      bytes[i] = (byte) values[i];
    }
    return bytes;
  }

  private record Background(Thread thread, CompletableFuture<Void> done) {}
}
