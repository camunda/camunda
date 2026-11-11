/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.cache;

import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.protocol.EnumValue;
import io.camunda.zeebe.util.exception.RecoverableException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.StampedLock;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Writes committed transactions to the backend in commit order, on a thread of its own.
 *
 * <p>It groups consecutive whole transactions into one backend transaction, writing each key once
 * with its newest version, and never splits a transaction. Each group commits atomically and ends
 * on a transaction boundary, so the backend always holds a prefix of the committed transactions.
 *
 * <p>A group closes when the queued transactions have {@code batchSize} keys, when the oldest of
 * them has waited {@code maxWriteDelay}, or when {@link #flushUpTo} asked for it. It takes whole
 * transactions from the head of the queue while it stays within {@code batchSize} distinct keys; a
 * larger transaction is written alone.
 */
final class TransactionWriter<ColumnFamilyNames extends Enum<? extends EnumValue> & EnumValue>
    implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(TransactionWriter.class);
  private static final int MAX_WRITE_ATTEMPTS = 5;
  private static final Duration INITIAL_BACKOFF = Duration.ofMillis(10);

  private final PassThroughColumnFamilies<ColumnFamilyNames> backendColumnFamilies;
  private final DirtyIndex dirtyIndex;
  private final StampedLock publishLock;
  private final int batchSize;
  private final long maxWriteDelayNanos;
  private final Runnable onFlushed;
  private final Consumer<Throwable> onFailure;
  private final ReentrantLock queueLock = new ReentrantLock();
  private final Condition queueChanged = queueLock.newCondition();
  private final ArrayDeque<CommittedTransaction> queue = new ArrayDeque<>();
  private final AtomicLong queuedBytes = new AtomicLong();
  private volatile long lastQueuedSeq;
  private volatile long flushedSeq;
  private final Thread thread;

  private long queuedKeys; // guarded by queueLock
  private long flushRequestedSeq; // guarded by queueLock
  private boolean closed; // guarded by queueLock

  /**
   * @param onFlushed called after each group is written and cleaned up
   * @param onFailure called when a group can't be written; the writer then stops
   */
  TransactionWriter(
      final ZeebeDb<ColumnFamilyNames> backend,
      final DirtyIndex dirtyIndex,
      final StampedLock publishLock,
      final WriteBehindConfiguration configuration,
      final Runnable onFlushed,
      final Consumer<Throwable> onFailure) {
    backendColumnFamilies = new PassThroughColumnFamilies<>(backend, backend.createContext());
    this.dirtyIndex = dirtyIndex;
    this.publishLock = publishLock;
    batchSize = configuration.batchSize();
    maxWriteDelayNanos = configuration.maxWriteDelay().toNanos();
    this.onFlushed = onFlushed;
    this.onFailure = onFailure;
    thread = new Thread(this::run, "zb-db-write-behind");
    thread.setDaemon(true);
    thread.start();
  }

  /** Queues a committed transaction, after the ones queued before it. */
  void enqueue(final CommittedTransaction transaction) {
    queueLock.lock();
    try {
      if (closed) {
        throw new IllegalStateException(
            "Expected to queue a transaction for the backend, but the writer is closed");
      }
      queue.add(transaction);
      queuedKeys += transaction.versions().size();
      queuedBytes.addAndGet(transaction.bytes());
      lastQueuedSeq = transaction.seq();
      queueChanged.signal();
    } finally {
      queueLock.unlock();
    }
  }

  /** Returns the seq of the last transaction queued, or 0 if there is none yet. */
  long lastQueuedSeq() {
    return lastQueuedSeq;
  }

  /** Returns the seq of the last transaction written to the backend, or 0 if there is none yet. */
  long flushedSeq() {
    return flushedSeq;
  }

  /**
   * Writes the queued transactions up to {@code seq} without waiting out {@code maxWriteDelay}. The
   * ones after it are grouped as usual.
   */
  void flushUpTo(final long seq) {
    queueLock.lock();
    try {
      flushRequestedSeq = Math.max(flushRequestedSeq, seq);
      queueChanged.signal();
    } finally {
      queueLock.unlock();
    }
  }

  /** Returns the key and value bytes of the transactions the backend doesn't have yet. */
  long queuedBytes() {
    return queuedBytes.get();
  }

  /**
   * Stops the writer once its current group, if any, is written. The transactions still queued are
   * dropped.
   */
  @Override
  public void close() {
    queueLock.lock();
    try {
      closed = true;
      queueChanged.signal();
    } finally {
      queueLock.unlock();
    }

    boolean interrupted = false;
    while (thread.isAlive()) {
      try {
        thread.join();
      } catch (final InterruptedException e) {
        interrupted = true;
      }
    }
    if (interrupted) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Writes {@code newest} as one backend transaction. A recoverable error is retried with backoff,
   * since the group rewrites exactly the same versions; anything else, or running out of attempts,
   * is thrown.
   *
   * @return {@code false} if the writer was closed while waiting to retry
   */
  private boolean writeToBackend(final TreeMap<CacheKey, CachedValue> newest)
      throws InterruptedException {
    for (int attempt = 1; ; attempt++) {
      try {
        backendColumnFamilies
            .context()
            .runInTransaction(
                () ->
                    newest.forEach(
                        (key, version) -> {
                          final var value = version.bytes();
                          if (value == null) {
                            backendColumnFamilies.deleteIfExists(key);
                          } else {
                            backendColumnFamilies.upsert(key, value);
                          }
                        }));
        return true;
      } catch (final RecoverableException e) {
        if (attempt == MAX_WRITE_ATTEMPTS) {
          throw e;
        }
        final long backoffNanos = INITIAL_BACKOFF.toNanos() << (attempt - 1);
        LOG.warn(
            "Failed to write {} keys to the backend (attempt {} of {}), retrying in {} ms",
            newest.size(),
            attempt,
            MAX_WRITE_ATTEMPTS,
            TimeUnit.NANOSECONDS.toMillis(backoffNanos),
            e);
        if (!awaitUnlessClosed(backoffNanos)) {
          return false;
        }
      }
    }
  }

  /** Waits {@code nanos}, or less if the writer is closed. Returns whether it's still open. */
  private boolean awaitUnlessClosed(final long nanos) throws InterruptedException {
    queueLock.lock();
    try {
      long remaining = nanos;
      while (!closed && remaining > 0) {
        remaining = queueChanged.awaitNanos(remaining);
      }
      return !closed;
    } finally {
      queueLock.unlock();
    }
  }

  private void run() {
    try {
      List<CommittedTransaction> group;
      while ((group = nextGroup()) != null) {
        write(group);
      }
    } catch (final Throwable e) {
      onFailure.accept(e);
    }
  }

  /** Waits until a group closes and takes it from the queue; returns {@code null} once closed. */
  private @Nullable List<CommittedTransaction> nextGroup() throws InterruptedException {
    queueLock.lock();
    try {
      while (!closed) {
        final var oldest = queue.peek();
        if (oldest == null) {
          queueChanged.await();
          continue;
        }

        final long waitedNanos = System.nanoTime() - oldest.enqueuedNanos();
        if (queuedKeys >= batchSize
            || waitedNanos >= maxWriteDelayNanos
            || oldest.seq() <= flushRequestedSeq) {
          return takeGroup();
        }
        queueChanged.awaitNanos(maxWriteDelayNanos - waitedNanos);
      }
      return null;
    } finally {
      queueLock.unlock();
    }
  }

  private List<CommittedTransaction> takeGroup() {
    final List<CommittedTransaction> group = new ArrayList<>();
    final Set<CacheKey> keys = new HashSet<>();
    CommittedTransaction next;
    while ((next = queue.peek()) != null) {
      if (!group.isEmpty() && distinctKeysWith(keys, next) > batchSize) {
        break;
      }
      queue.poll();
      queuedKeys -= next.versions().size();
      group.add(next);
      next.versions().forEach(version -> keys.add(version.getKey()));
    }
    return group;
  }

  private static int distinctKeysWith(
      final Set<CacheKey> keys, final CommittedTransaction transaction) {
    int distinct = keys.size();
    for (final var version : transaction.versions()) {
      if (!keys.contains(version.getKey())) {
        distinct++;
      }
    }
    return distinct;
  }

  private void write(final List<CommittedTransaction> group) throws InterruptedException {
    final var newest = new TreeMap<CacheKey, CachedValue>();
    long bytes = 0;
    for (final var transaction : group) {
      transaction.versions().forEach(version -> newest.put(version.getKey(), version.getValue()));
      bytes += transaction.bytes();
    }

    if (!writeToBackend(newest)) {
      return;
    }

    final long stamp = publishLock.writeLock();
    try {
      newest.forEach((key, version) -> dirtyIndex.removeFlushed(key, version.seq()));
    } finally {
      publishLock.unlockWrite(stamp);
    }
    queuedBytes.addAndGet(-bytes);
    flushedSeq = group.getLast().seq();
    onFlushed.run();
  }
}
