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

import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.impl.DbLong;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

final class CachedColumnFamilyScanConcurrencyTest {

  private static final int KEYS = 50;
  private static final int ENTRY_BYTES = 2 * Long.BYTES;
  private static final ConsistencyChecksSettings CHECKS_ON =
      new ConsistencyChecksSettings(true, true);

  private final InMemoryBackend backend = new InMemoryBackend();
  private final List<WriteBehindStore<TestColumnFamily>> stores = new ArrayList<>();

  @AfterEach
  void closeStores() {
    backend.resumeScans();
    backend.resumeTransactions();
    stores.forEach(WriteBehindStore::close);
  }

  @Test
  void shouldSeeOneTransactionPerScanWhileTheWriterFlushes() {
    // given - groups of several whole transactions, coalesced, and a budget of a few transactions
    final long maxPendingBytes = 4L * KEYS * ENTRY_BYTES;
    final var store =
        store(
            new TestConfiguration()
                .maxWriteDelay(Duration.ofMillis(1))
                .batchSize(100)
                .maxPendingBytes(maxPendingBytes));
    final var committer = client(store);
    final var scanner = client(store);
    final long transactions = 500;

    // when - each transaction writes the same value to every key
    final var committing =
        CompletableFuture.runAsync(
            () -> {
              for (long n = 1; n <= transactions; n++) {
                final long value = n;
                committer
                    .context()
                    .runInTransaction(
                        () -> {
                          for (long key = 0; key < KEYS; key++) {
                            committer.columnFamily().upsert(dbLong(key), dbLong(value));
                          }
                        });
              }
            });
    final List<String> inconsistentScans = new ArrayList<>();
    long maxSeenPendingBytes = 0;
    while (!committing.isDone()) {
      final var values = new TreeSet<Long>();
      final var visited = new ArrayList<Long>();
      scanner
          .columnFamily()
          .forEach(
              (key, value) -> {
                visited.add(key.getValue());
                values.add(value.getValue());
                if (!scanner.columnFamily().exists(key)) {
                  inconsistentScans.add("key " + key.getValue() + " vanished during the scan");
                }
              });
      if (values.size() > 1 || (!visited.isEmpty() && visited.size() != KEYS)) {
        inconsistentScans.add(visited.size() + " keys with values " + values);
      }
      maxSeenPendingBytes = Math.max(maxSeenPendingBytes, store.pendingBytes());
    }

    // then
    assertThat(committing).succeedsWithin(Duration.ofSeconds(60));
    assertThat(inconsistentScans).isEmpty();
    assertThat(maxSeenPendingBytes).isLessThanOrEqualTo(maxPendingBytes + KEYS * ENTRY_BYTES);
    await().untilAsserted(() -> assertThat(store.pendingBytes()).isZero());
    final var values = new TreeSet<Long>();
    scanner.columnFamily().forEach((key, value) -> values.add(value.getValue()));
    assertThat(values).containsExactly(transactions);
    assertThat(backend.get(FIRST, serialize(dbLong(0)))).isEqualTo(serialize(dbLong(transactions)));
  }

  @Test
  void shouldHoldPublicationsBackUntilTheScansFirstEntry() throws Exception {
    // given
    for (long key = 0; key < 5; key++) {
      backend.put(FIRST, serialize(dbLong(key)), serialize(dbLong(0)));
    }
    final var store = store(new TestConfiguration().maxWriteDelay(Duration.ofHours(1)));
    final var committer = client(store);
    final var scanner = client(store);
    backend.pauseScans();
    final var scanned = new ArrayList<Long>();
    final var scanning =
        inBackgroundThread(
            () -> scanner.columnFamily().forEach((key, value) -> scanned.add(value.getValue())));
    backend.awaitPausedScan();

    // when - another context commits while the backend has fixed its view, before its first entry
    final var committing =
        inBackgroundThread(
            () ->
                committer
                    .context()
                    .runInTransaction(
                        () -> {
                          for (long key = 0; key < 5; key++) {
                            committer.columnFamily().upsert(dbLong(key), dbLong(1));
                          }
                        }));
    await().until(() -> committing.thread().getState() == Thread.State.WAITING);
    assertThat(committing.done()).isNotDone();
    backend.resumeScans();

    // then - the scan sees none of the transaction, which publishes once the scan got going
    assertThat(scanning.done()).succeedsWithin(Duration.ofSeconds(5));
    assertThat(committing.done()).succeedsWithin(Duration.ofSeconds(5));
    assertThat(scanned).containsExactly(0L, 0L, 0L, 0L, 0L);
    final var values = new ArrayList<Long>();
    scanner.columnFamily().forEach((key, value) -> values.add(value.getValue()));
    assertThat(values).containsExactly(1L, 1L, 1L, 1L, 1L);
  }

  private WriteBehindStore<TestColumnFamily> store(final TestConfiguration configuration) {
    final var store = new WriteBehindStore<>(backend.db(), configuration.build());
    stores.add(store);
    return store;
  }

  private Client client(final WriteBehindStore<TestColumnFamily> store) {
    final var context = new CachedTransactionContext<>(store, backend.db());
    return new Client(
        context,
        new CachedColumnFamily<>(store, context, FIRST, new DbLong(), new DbLong(), CHECKS_ON));
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

  private static DbLong dbLong(final long value) {
    final var dbLong = new DbLong();
    dbLong.wrapLong(value);
    return dbLong;
  }

  private static byte[] serialize(final DbLong value) {
    final var buffer = new UnsafeBuffer(new byte[value.getLength()]);
    value.write(buffer, 0);
    return buffer.byteArray();
  }

  private record Client(
      CachedTransactionContext<TestColumnFamily> context,
      CachedColumnFamily<TestColumnFamily, DbLong, DbLong> columnFamily) {}

  private record Background(Thread thread, CompletableFuture<Void> done) {}
}
