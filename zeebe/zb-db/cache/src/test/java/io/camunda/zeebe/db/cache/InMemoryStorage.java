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
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.Nullable;

/**
 * A storage that keeps each column family in a sorted map. It supports only what the storage column
 * families use: {@code get}, {@code upsert}, {@code deleteIfExists}, forward prefix scans and
 * reverse scans, and transactions that run their operations directly. It is safe to use from
 * several threads.
 *
 * <p>It records the writes of each transaction, in {@link #transactionLog()}. Transactions can be
 * made to fail, with {@link #failTransactions}.
 *
 * <p>Transactions can be paused, which stalls the cache's writer: it is the only caller of {@code
 * runInTransaction}, since reads go straight to the column families.
 *
 * <p>A scan fixes its view when it starts, like a RocksDB iterator, by copying its range. Scans can
 * be paused right after that, before their first callback.
 *
 * <p>Its meter registry is a {@link SimpleMeterRegistry}, in {@link #meterRegistry()}.
 */
final class InMemoryStorage {

  private final Map<TestColumnFamily, NavigableMap<byte[], byte[]>> data =
      new ConcurrentHashMap<>();
  private final AtomicInteger transactions = new AtomicInteger();
  private final List<List<String>> transactionLog = new CopyOnWriteArrayList<>();
  private final ThreadLocal<List<String>> currentTransaction = new ThreadLocal<>();
  private final AtomicInteger failingTransactions = new AtomicInteger();
  private volatile Supplier<RuntimeException> transactionFailure = IllegalStateException::new;
  private final Semaphore pausedTransactions = new Semaphore(0);
  private volatile CountDownLatch resumed = new CountDownLatch(0);
  private final Semaphore pausedScans = new Semaphore(0);
  private volatile CountDownLatch scansResumed = new CountDownLatch(0);
  private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final ZeebeDb<TestColumnFamily> db;

  @SuppressWarnings("unchecked")
  InMemoryStorage() {
    db = mock(ZeebeDb.class);
    when(db.getMeterRegistry()).thenReturn(meterRegistry);
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

  MeterRegistry meterRegistry() {
    return meterRegistry;
  }

  int transactions() {
    return transactions.get();
  }

  /**
   * Returns the writes of each finished transaction, in order: {@code "FIRST [1]=[10]"} for an
   * upsert, {@code "FIRST [1] deleted"} for a delete.
   */
  List<List<String>> transactionLog() {
    return transactionLog;
  }

  /**
   * Makes the next {@code count} transactions throw instead of writing anything, including one that
   * is paused right now.
   */
  void failTransactions(final int count, final Supplier<RuntimeException> failure) {
    transactionFailure = failure;
    failingTransactions.set(count);
  }

  /** Makes every transaction that starts from now on wait until {@link #resumeTransactions()}. */
  void pauseTransactions() {
    resumed = new CountDownLatch(1);
  }

  void resumeTransactions() {
    resumed.countDown();
  }

  /** Makes every scan that starts from now on wait before its first callback. */
  void pauseScans() {
    scansResumed = new CountDownLatch(1);
  }

  void resumeScans() {
    scansResumed.countDown();
  }

  /** Waits until a scan is waiting because of {@link #pauseScans()}. */
  void awaitPausedScan() throws InterruptedException {
    if (!pausedScans.tryAcquire(10, TimeUnit.SECONDS)) {
      throw new IllegalStateException("Expected a scan to pause, but none did");
    }
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
              if (failingTransactions.getAndUpdate(left -> Math.max(left - 1, 0)) > 0) {
                throw transactionFailure.get();
              }
              transactions.incrementAndGet();
              final List<String> writes = new ArrayList<>();
              currentTransaction.set(writes);
              try {
                invocation.<TransactionOperation>getArgument(0).run();
              } finally {
                currentTransaction.remove();
              }
              transactionLog.add(writes);
              return null;
            })
        .when(context)
        .runInTransaction(any());
    return context;
  }

  @SuppressWarnings("unchecked")
  private ColumnFamily<RawKey, RawValue> createColumnFamily(
      final TestColumnFamily columnFamily, final RawKey keyInstance, final RawValue valueInstance) {
    final ColumnFamily<RawKey, RawValue> storageColumnFamily = mock(ColumnFamily.class);
    when(storageColumnFamily.get(any()))
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
              final var key = invocation.<RawKey>getArgument(0).bytes();
              final var value = invocation.<RawValue>getArgument(1).bytes();
              record(columnFamily + " " + Arrays.toString(key) + "=" + Arrays.toString(value));
              put(columnFamily, key, value);
              return null;
            })
        .when(storageColumnFamily)
        .upsert(any(), any());
    doAnswer(
            invocation -> {
              final var key = invocation.<RawKey>getArgument(0).bytes();
              record(columnFamily + " " + Arrays.toString(key) + " deleted");
              rows(columnFamily).remove(key);
              return null;
            })
        .when(storageColumnFamily)
        .deleteIfExists(any());
    doAnswer(
            invocation -> {
              final KeyValuePairVisitor<RawKey, RawValue> visitor = invocation.getArgument(2);
              final DbKey prefix = invocation.getArgument(0);
              scan(
                  rows(columnFamily).tailMap(invocation.<RawKey>getArgument(1).bytes(), true),
                  ((RawKey) prefix).bytes(),
                  keyInstance,
                  valueInstance,
                  visitor);
              return null;
            })
        .when(storageColumnFamily)
        .whileEqualPrefix(any(DbKey.class), any(RawKey.class), any(KeyValuePairVisitor.class));
    doAnswer(
            invocation -> {
              final KeyVisitor<RawKey> visitor = invocation.getArgument(2);
              final DbKey prefix = invocation.getArgument(0);
              scan(
                  rows(columnFamily).tailMap(invocation.<RawKey>getArgument(1).bytes(), true),
                  ((RawKey) prefix).bytes(),
                  keyInstance,
                  valueInstance,
                  (key, value) -> visitor.visit(key));
              return null;
            })
        .when(storageColumnFamily)
        .whileEqualPrefix(any(DbKey.class), any(RawKey.class), any(KeyVisitor.class));
    doAnswer(
            invocation -> {
              final KeyValuePairVisitor<RawKey, RawValue> visitor = invocation.getArgument(1);
              scan(
                  rows(columnFamily)
                      .headMap(invocation.<RawKey>getArgument(0).bytes(), true)
                      .descendingMap(),
                  new byte[0],
                  keyInstance,
                  valueInstance,
                  visitor);
              return null;
            })
        .when(storageColumnFamily)
        .whileTrueReverse(any(RawKey.class), any(KeyValuePairVisitor.class));
    doAnswer(
            invocation -> {
              final KeyVisitor<RawKey> visitor = invocation.getArgument(1);
              scan(
                  rows(columnFamily)
                      .headMap(invocation.<RawKey>getArgument(0).bytes(), true)
                      .descendingMap(),
                  new byte[0],
                  keyInstance,
                  valueInstance,
                  (key, value) -> visitor.visit(key));
              return null;
            })
        .when(storageColumnFamily)
        .whileTrueReverse(any(RawKey.class), any(KeyVisitor.class));
    return storageColumnFamily;
  }

  private void record(final String write) {
    final var writes = currentTransaction.get();
    if (writes != null) {
      writes.add(write);
    }
  }

  private void scan(
      final NavigableMap<byte[], byte[]> range,
      final byte[] prefix,
      final RawKey keyInstance,
      final RawValue valueInstance,
      final KeyValuePairVisitor<RawKey, RawValue> visitor)
      throws InterruptedException {
    final var view = new ArrayList<>(range.entrySet());
    final var scansResumed = this.scansResumed;
    if (scansResumed.getCount() > 0) {
      pausedScans.release();
      scansResumed.await();
    }
    for (final var entry : view) {
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
