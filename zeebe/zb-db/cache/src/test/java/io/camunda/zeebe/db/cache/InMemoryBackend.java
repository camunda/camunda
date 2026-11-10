/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.cache;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.KeyValuePairVisitor;
import io.camunda.zeebe.db.KeyVisitor;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.TransactionOperation;
import io.camunda.zeebe.db.ZeebeDb;
import java.util.Arrays;
import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.Nullable;

/**
 * A backend that keeps each column family in a sorted map. It supports only what the pass-through
 * column families use: {@code get}, {@code upsert}, {@code deleteIfExists}, forward prefix scans
 * and reverse scans, and transactions that run their operations directly. It is safe to use from
 * several threads.
 *
 * <p>Transactions can be paused, which stalls the cache's writer: it is the only caller of {@code
 * runInTransaction}, since reads go straight to the column families.
 */
final class InMemoryBackend {

  private final Map<TestColumnFamily, NavigableMap<byte[], byte[]>> data =
      new ConcurrentHashMap<>();
  private final AtomicInteger transactions = new AtomicInteger();
  private final Semaphore pausedTransactions = new Semaphore(0);
  private volatile CountDownLatch resumed = new CountDownLatch(0);
  private final ZeebeDb<TestColumnFamily> db;

  @SuppressWarnings("unchecked")
  InMemoryBackend() {
    db = mock(ZeebeDb.class);
    when(db.createContext()).thenAnswer(invocation -> createContext());
    when(db.createColumnFamily(any(), any(), any(), any()))
        .thenAnswer(
            invocation ->
                createColumnFamily(
                    invocation.getArgument(0),
                    invocation.getArgument(2),
                    invocation.getArgument(3)));
  }

  ZeebeDb<TestColumnFamily> db() {
    return db;
  }

  int transactions() {
    return transactions.get();
  }

  /** Makes every transaction that starts from now on wait until {@link #resumeTransactions()}. */
  void pauseTransactions() {
    resumed = new CountDownLatch(1);
  }

  void resumeTransactions() {
    resumed.countDown();
  }

  /** Waits until a transaction is waiting because of {@link #pauseTransactions()}. */
  void awaitPausedTransaction() throws InterruptedException {
    if (!pausedTransactions.tryAcquire(10, TimeUnit.SECONDS)) {
      throw new IllegalStateException("Expected a transaction to pause, but none did");
    }
  }

  byte @Nullable [] get(final TestColumnFamily columnFamily, final byte[] key) {
    return rows(columnFamily).get(key);
  }

  void put(final TestColumnFamily columnFamily, final byte[] key, final byte[] value) {
    rows(columnFamily).put(key.clone(), value.clone());
  }

  private NavigableMap<byte[], byte[]> rows(final TestColumnFamily columnFamily) {
    return data.computeIfAbsent(
        columnFamily, cf -> new ConcurrentSkipListMap<>(Arrays::compareUnsigned));
  }

  private TransactionContext createContext() {
    final var context = mock(TransactionContext.class);
    doAnswer(
            invocation -> {
              final var resumed = this.resumed;
              if (resumed.getCount() > 0) {
                pausedTransactions.release();
                resumed.await();
              }
              transactions.incrementAndGet();
              invocation.<TransactionOperation>getArgument(0).run();
              return null;
            })
        .when(context)
        .runInTransaction(any());
    return context;
  }

  @SuppressWarnings("unchecked")
  private ColumnFamily<RawKey, RawValue> createColumnFamily(
      final TestColumnFamily columnFamily, final RawKey keyInstance, final RawValue valueInstance) {
    final ColumnFamily<RawKey, RawValue> passThrough = mock(ColumnFamily.class);
    when(passThrough.get(any()))
        .thenAnswer(
            invocation -> {
              final var value = get(columnFamily, invocation.<RawKey>getArgument(0).bytes());
              if (value == null) {
                return null;
              }
              valueInstance.wrap(new UnsafeBuffer(value), 0, value.length);
              return valueInstance;
            });
    doAnswer(
            invocation -> {
              put(
                  columnFamily,
                  invocation.<RawKey>getArgument(0).bytes(),
                  invocation.<RawValue>getArgument(1).bytes());
              return null;
            })
        .when(passThrough)
        .upsert(any(), any());
    doAnswer(
            invocation -> {
              rows(columnFamily).remove(invocation.<RawKey>getArgument(0).bytes());
              return null;
            })
        .when(passThrough)
        .deleteIfExists(any());
    doAnswer(
            invocation -> {
              final KeyValuePairVisitor<RawKey, RawValue> visitor = invocation.getArgument(2);
              final DbKey prefix = invocation.getArgument(0);
              scan(
                  rows(columnFamily)
                      .tailMap(invocation.<RawKey>getArgument(1).bytes(), true)
                      .entrySet(),
                  ((RawKey) prefix).bytes(),
                  keyInstance,
                  valueInstance,
                  visitor);
              return null;
            })
        .when(passThrough)
        .whileEqualPrefix(any(DbKey.class), any(RawKey.class), any(KeyValuePairVisitor.class));
    doAnswer(
            invocation -> {
              final KeyVisitor<RawKey> visitor = invocation.getArgument(2);
              final DbKey prefix = invocation.getArgument(0);
              scan(
                  rows(columnFamily)
                      .tailMap(invocation.<RawKey>getArgument(1).bytes(), true)
                      .entrySet(),
                  ((RawKey) prefix).bytes(),
                  keyInstance,
                  valueInstance,
                  (key, value) -> visitor.visit(key));
              return null;
            })
        .when(passThrough)
        .whileEqualPrefix(any(DbKey.class), any(RawKey.class), any(KeyVisitor.class));
    doAnswer(
            invocation -> {
              final KeyValuePairVisitor<RawKey, RawValue> visitor = invocation.getArgument(1);
              scan(
                  rows(columnFamily)
                      .headMap(invocation.<RawKey>getArgument(0).bytes(), true)
                      .descendingMap()
                      .entrySet(),
                  new byte[0],
                  keyInstance,
                  valueInstance,
                  visitor);
              return null;
            })
        .when(passThrough)
        .whileTrueReverse(any(RawKey.class), any(KeyValuePairVisitor.class));
    doAnswer(
            invocation -> {
              final KeyVisitor<RawKey> visitor = invocation.getArgument(1);
              scan(
                  rows(columnFamily)
                      .headMap(invocation.<RawKey>getArgument(0).bytes(), true)
                      .descendingMap()
                      .entrySet(),
                  new byte[0],
                  keyInstance,
                  valueInstance,
                  (key, value) -> visitor.visit(key));
              return null;
            })
        .when(passThrough)
        .whileTrueReverse(any(RawKey.class), any(KeyVisitor.class));
    return passThrough;
  }

  private static void scan(
      final Iterable<Map.Entry<byte[], byte[]>> entries,
      final byte[] prefix,
      final RawKey keyInstance,
      final RawValue valueInstance,
      final KeyValuePairVisitor<RawKey, RawValue> visitor) {
    for (final var entry : entries) {
      final var key = entry.getKey();
      if (!OverlayMerge.startsWith(key, prefix)) {
        return;
      }
      keyInstance.wrap(new UnsafeBuffer(key), 0, key.length);
      valueInstance.wrap(new UnsafeBuffer(entry.getValue()), 0, entry.getValue().length);
      if (!visitor.visit(keyInstance, valueInstance)) {
        return;
      }
    }
  }
}
