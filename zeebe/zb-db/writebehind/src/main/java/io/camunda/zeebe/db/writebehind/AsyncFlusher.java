/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.writebehind;

import io.camunda.zeebe.db.ZeebeDbException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The asynchronous half of the write-behind store: it owns the queue of committed transactions, the
 * writer thread, and every piece of state that the writer shares with the committing thread.
 *
 * <p>The committing thread (the stream processor) calls {@link #enqueue} under the store's publish
 * lock, and may call {@link #awaitCapacity}. It never waits for the writer in any other way. The
 * writer thread waits until the oldest queued transaction is due, takes everything that is queued,
 * writes it to the storage as one atomic batch, and then reports the flushed versions to the store
 * in small chunks, so that the store's publish lock is never held for long.
 *
 * <h2>Concurrency</h2>
 *
 * <ul>
 *   <li>{@link #queue} and {@link #pendingBytes} are the hand-over from the committing thread to
 *       the writer. Both are safe for concurrent use. The committing thread adds to the queue under
 *       the publish lock, so the queue order is the commit order.
 *   <li>{@link #flushLock} guards {@link #flushRequestedSeq} and is held for the conditions {@link
 *       #flushed} (the writer finished a batch) and {@link #flushWanted} (the writer must not wait
 *       any longer). Nobody does I/O or takes another lock while holding it.
 *   <li>{@link #flushedSeq}, {@link #failure} and {@link #stopping} are volatile: one writer, any
 *       number of readers.
 * </ul>
 */
final class AsyncFlusher<K extends Comparable<K>, V> {

  private static final Logger LOG = LoggerFactory.getLogger("io.camunda.zeebe.db.writebehind");
  private static final long INITIAL_RETRY_DELAY_NANOS = TimeUnit.MILLISECONDS.toNanos(10);
  private static final long MAX_RETRY_DELAY_NANOS = TimeUnit.SECONDS.toNanos(1);

  private final Backend<K, V, ?> backend;
  private final WriteBehindConfiguration config;
  private final WriteBehindMetrics metrics;
  private final Runnable ensureUsable;
  private final Function<Runnable, NavigableMap<K, Version<V>>> sealBatch;
  private final Consumer<NavigableMap<K, Version<V>>> releaseBatch;
  private final long flushThresholdBytes;
  private final Thread writer;

  @SuppressWarnings({"unchecked", "rawtypes"})
  private final PendingTransaction<K, V> stop =
      new PendingTransaction<>(Long.MAX_VALUE, (K[]) new Comparable[0], new Version[0], 0, 0);

  private final LinkedBlockingQueue<PendingTransaction<K, V>> queue = new LinkedBlockingQueue<>();
  private final AtomicLong pendingBytes = new AtomicLong();

  private final ReentrantLock flushLock = new ReentrantLock();
  private final Condition flushed = flushLock.newCondition();
  private final Condition flushWanted = flushLock.newCondition();

  // guarded by flushLock: the commits up to this sequence must not wait for the flush delay
  private long flushRequestedSeq;

  private volatile long flushedSeq;
  private volatile @Nullable Throwable failure;
  private volatile boolean stopping;

  // the oldest transaction that the writer took off the queue and has not flushed yet
  private volatile @Nullable PendingTransaction<K, V> oldestTaken;

  /**
   * @param ensureUsable throws if the store can't be used any more; called while waiting, so that
   *     waiters fail fast
   * @param sealBatch called on the writer thread to take the batch of the queued commits away from
   *     the store; it runs the given task, which takes them off the queue, while no commit can
   *     publish
   * @param releaseBatch called on the writer thread when the storage holds the batch; the store
   *     moves it to its cache and drops it
   */
  AsyncFlusher(
      final Backend<K, V, ?> backend,
      final WriteBehindConfiguration config,
      final WriteBehindMetrics metrics,
      final Runnable ensureUsable,
      final Function<Runnable, NavigableMap<K, Version<V>>> sealBatch,
      final Consumer<NavigableMap<K, Version<V>>> releaseBatch) {
    this.backend = backend;
    this.config = config;
    this.metrics = metrics;
    this.ensureUsable = ensureUsable;
    this.sealBatch = sealBatch;
    this.releaseBatch = releaseBatch;
    flushThresholdBytes = Math.min(config.flushThresholdBytes(), config.maxPendingBytes());
    writer = new Thread(this::runWriter, "zb-db-write-behind");
    writer.setDaemon(true);
  }

  /** Starts the writer; separate from the constructor so that the callbacks see a built store. */
  void start() {
    writer.start();
  }

  @Nullable Throwable failure() {
    return failure;
  }

  long flushedSeq() {
    return flushedSeq;
  }

  long pendingBytes() {
    return pendingBytes.get();
  }

  /** How long the oldest transaction that the storage doesn't hold yet has been committed. */
  long oldestUnflushedAgeNanos() {
    final var taken = oldestTaken;
    final var oldest = taken != null ? taken : queue.peek();
    if (oldest == null || oldest == stop) {
      return 0;
    }
    return Math.max(0, System.nanoTime() - oldest.committedAtNanos());
  }

  /**
   * Queues a transaction. Must be called under the store's publish lock, so that the queue order is
   * the commit order. Never blocks: the only thing it can do beyond a queue insert is to wake the
   * writer once, when the unflushed bytes cross the early-flush threshold.
   */
  void enqueue(final long seq, final K[] keys, final Version<V>[] versions, final long bytes) {
    queue.add(new PendingTransaction<>(seq, keys, versions, bytes, System.nanoTime()));
    final long after = pendingBytes.addAndGet(bytes);
    if (after >= flushThresholdBytes && after - bytes < flushThresholdBytes) {
      wakeWriter();
    }
  }

  /**
   * The one place where the committing thread waits for the writer: while the unflushed bytes are
   * above the limit. The writer starts an early flush at the flush threshold, so this only blocks
   * when the storage is slower than the commits for a long time.
   */
  void awaitCapacity() {
    if (pendingBytes.get() <= config.maxPendingBytes()) {
      return;
    }
    flushLock.lock();
    try {
      while (pendingBytes.get() > config.maxPendingBytes()) {
        ensureUsable.run();
        flushed.await();
      }
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while waiting for the write-behind flush", e);
    } finally {
      flushLock.unlock();
    }
  }

  /**
   * Makes the writer flush now, and waits until the storage holds every transaction up to {@code
   * target}. Fails if the writer fails or the flush takes longer than the configured timeout.
   */
  void awaitFlushed(final long target) {
    long remaining = config.flushTimeout().toNanos();
    flushLock.lock();
    try {
      if (flushRequestedSeq < target) {
        flushRequestedSeq = target;
        flushWanted.signalAll();
      }
      while (flushedSeq < target) {
        ensureUsable.run();
        if (remaining <= 0) {
          throw new IllegalStateException(
              "Expected to flush up to commit %d within %s, but only flushed up to %d"
                  .formatted(target, config.flushTimeout(), flushedSeq));
        }
        remaining = flushed.awaitNanos(remaining);
      }
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while waiting for the write-behind flush", e);
    } finally {
      flushLock.unlock();
    }
  }

  /**
   * Queues the stop marker. Must be called under the store's publish lock, after the last commit,
   * so that the writer flushes everything that was committed and then stops.
   */
  void requestStop() {
    stopping = true;
    queue.add(stop);
  }

  /** Wakes the writer and waits for it to finish; interrupts it after the flush timeout. */
  void awaitStopped() {
    wakeWriter();
    signalFlushed();
    try {
      writer.join(config.flushTimeout().toMillis());
      if (writer.isAlive()) {
        LOG.warn(
            "Write-behind writer didn't stop within {}; interrupting it", config.flushTimeout());
        writer.interrupt();
        writer.join();
      }
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private void runWriter() {
    final var group = new ArrayList<PendingTransaction<K, V>>();
    try {
      while (true) {
        final var oldest = queue.take();
        group.add(oldest);
        if (oldest != stop) {
          oldestTaken = oldest;
          awaitFlushDue(oldest);
        }
        final var sealed = sealBatch.apply(() -> queue.drainTo(group));
        final boolean stopAfter = group.getLast() == stop;
        if (stopAfter) {
          group.removeLast();
        }
        if (!flush(group, sealed)) {
          return;
        }
        group.clear();
        oldestTaken = null;
        if (stopAfter) {
          return;
        }
      }
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Waits until the oldest queued transaction is due. This is the one place that decides when to
   * flush, so a new policy changes the condition of the loop and nothing else. A transaction is due
   * when it has waited for the flush delay, or earlier when a snapshot waits for it, when the
   * unflushed bytes reach the flush threshold, or when the store stops or fails.
   *
   * <p>The writer then takes everything that is queued, so a key that changed several times in the
   * delay is written once, and one that a commit deleted is not written at all.
   */
  private void awaitFlushDue(final PendingTransaction<K, V> oldest) throws InterruptedException {
    final long delay = config.flushDelay().toNanos();
    if (delay <= 0) {
      return;
    }
    final long deadline = oldest.committedAtNanos() + delay;
    flushLock.lock();
    try {
      while (!stopping
          && failure == null
          && flushRequestedSeq < oldest.seq()
          && pendingBytes.get() < flushThresholdBytes) {
        final long remaining = deadline - System.nanoTime();
        if (remaining <= 0) {
          return;
        }
        flushWanted.awaitNanos(remaining);
      }
    } finally {
      flushLock.unlock();
    }
  }

  /**
   * Writes a sealed batch, the latest version per key of a group of consecutive transactions,
   * atomically; returns false if the store failed.
   */
  private boolean flush(
      final List<PendingTransaction<K, V>> group, final NavigableMap<K, Version<V>> sealed) {
    if (group.isEmpty()) {
      releaseBatch.accept(sealed);
      return true;
    }
    long bytes = 0;
    for (final var transaction : group) {
      bytes += transaction.bytes();
    }

    final long start = System.nanoTime();
    if (!writeWithRetries(sealed)) {
      return false;
    }
    metrics.flushNanos.add(System.nanoTime() - start);
    metrics.flushes.increment();

    releaseBatch.accept(sealed);
    pendingBytes.addAndGet(-bytes);
    flushedSeq = group.getLast().seq();
    signalFlushed();
    return true;
  }

  private boolean writeWithRetries(final Map<K, Version<V>> latest) {
    long delay = INITIAL_RETRY_DELAY_NANOS;
    for (int attempt = 0; ; attempt++) {
      try {
        write(latest);
        return true;
      } catch (final ZeebeDbException e) {
        if (attempt >= config.maxWriteRetries()) {
          fail(e);
          return false;
        }
        metrics.flushRetries.increment();
        LOG.warn("Failed to write a batch to the storage, will retry (attempt {})", attempt + 1, e);
        LockSupport.parkNanos(delay);
        delay = Math.min(delay * 2, MAX_RETRY_DELAY_NANOS);
      } catch (final Throwable e) {
        fail(e);
        return false;
      }
    }
  }

  private void write(final Map<K, Version<V>> latest) {
    try (final var batch = backend.newBatch()) {
      for (final var entry : latest.entrySet()) {
        final var key = entry.getKey();
        final var value = entry.getValue().value();
        final var counters = metrics.of(backend.groupOf(key));
        if (value != null) {
          batch.put(key, value);
          counters.flushedPuts.increment();
        } else if (backend.mayExist(key)) {
          // a key that the storage never had needs no delete; this keeps short-lived state out of
          // it
          batch.delete(key);
          counters.flushedDeletes.increment();
        } else {
          counters.skippedDeletes.increment();
        }
      }
      batch.write();
    }
  }

  private void fail(final Throwable cause) {
    LOG.error(
        "Failed to write committed state to the storage; the write-behind store stops accepting work",
        cause);
    failure = cause;
    metrics.flushFailures.increment();
    signalFlushed();
  }

  private void wakeWriter() {
    flushLock.lock();
    try {
      flushWanted.signalAll();
    } finally {
      flushLock.unlock();
    }
  }

  private void signalFlushed() {
    flushLock.lock();
    try {
      flushed.signalAll();
    } finally {
      flushLock.unlock();
    }
  }

  private record PendingTransaction<K, V>(
      long seq, K[] keys, Version<V>[] versions, long bytes, long committedAtNanos) {}
}
