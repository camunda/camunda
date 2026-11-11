/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.writebehind;

import com.google.errorprone.annotations.concurrent.GuardedBy;
import io.camunda.zeebe.db.RawDbAccess;
import io.camunda.zeebe.db.ZeebeDbException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import org.agrona.ExpandableArrayBuffer;
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
 *   <li>{@link #encodeBuffer} belongs to the writer thread alone.
 * </ul>
 */
final class AsyncFlusher {

  private static final Logger LOG = LoggerFactory.getLogger("io.camunda.zeebe.db.writebehind");
  private static final PendingTransaction STOP =
      new PendingTransaction(Long.MAX_VALUE, new RawKey[0], new Version[0], 0, 0);
  private static final long INITIAL_RETRY_DELAY_NANOS = TimeUnit.MILLISECONDS.toNanos(10);
  private static final long MAX_RETRY_DELAY_NANOS = TimeUnit.SECONDS.toNanos(1);

  /**
   * How many flushed versions are removed from the dirty index while the publish lock is held. A
   * flush of a few seconds of commits holds many more keys than this, and the committing thread
   * waits for every hold.
   */
  private static final int REMOVAL_CHUNK_SIZE = 512;

  private final RawDbAccess storage;
  private final WriteBehindConfiguration config;
  private final Runnable ensureUsable;
  private final Consumer<List<Map.Entry<RawKey, Version>>> onFlushed;
  private final long flushThresholdBytes;
  private final Thread writer;

  private final LinkedBlockingQueue<PendingTransaction> queue = new LinkedBlockingQueue<>();
  private final AtomicLong pendingBytes = new AtomicLong();

  private final ReentrantLock flushLock = new ReentrantLock();
  private final Condition flushed = flushLock.newCondition();
  private final Condition flushWanted = flushLock.newCondition();

  // guarded by flushLock: the commits up to this sequence must not wait for the flush delay
  @GuardedBy("flushLock")
  private long flushRequestedSeq;

  private volatile long flushedSeq;
  private volatile @Nullable Throwable failure;
  private volatile boolean stopping;

  // only used by the writer thread
  private final ExpandableArrayBuffer encodeBuffer = new ExpandableArrayBuffer();

  /**
   * @param ensureUsable throws if the store can't be used any more; called while waiting, so that
   *     waiters fail fast
   * @param onFlushed called on the writer thread with versions that the storage now holds; the
   *     store removes them from its dirty index if they are still the latest
   */
  AsyncFlusher(
      final RawDbAccess storage,
      final WriteBehindConfiguration config,
      final Runnable ensureUsable,
      final Consumer<List<Map.Entry<RawKey, Version>>> onFlushed) {
    this.storage = storage;
    this.config = config;
    this.ensureUsable = ensureUsable;
    this.onFlushed = onFlushed;
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

  /**
   * Queues a transaction. Must be called under the store's publish lock, so that the queue order is
   * the commit order. Never blocks: the only thing it can do beyond a queue insert is to wake the
   * writer once, when the unflushed bytes cross the early-flush threshold.
   */
  void enqueue(final long seq, final RawKey[] keys, final Version[] versions, final long bytes) {
    queue.add(new PendingTransaction(seq, keys, versions, bytes, System.nanoTime()));
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
    queue.add(STOP);
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
    final var group = new ArrayList<PendingTransaction>();
    try {
      while (true) {
        final var oldest = queue.take();
        group.add(oldest);
        if (oldest != STOP) {
          awaitFlushDue(oldest);
        }
        queue.drainTo(group);
        final boolean stop = group.getLast() == STOP;
        if (stop) {
          group.removeLast();
        }
        if (!group.isEmpty() && !flush(group)) {
          return;
        }
        group.clear();
        if (stop) {
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
  private void awaitFlushDue(final PendingTransaction oldest) throws InterruptedException {
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

  /** Writes a group of consecutive transactions atomically; returns false if the store failed. */
  private boolean flush(final List<PendingTransaction> group) {
    final var latest = new HashMap<RawKey, Version>();
    long bytes = 0;
    for (final var transaction : group) {
      for (int i = 0; i < transaction.keys().length; i++) {
        latest.put(transaction.keys()[i], transaction.versions()[i]);
      }
      bytes += transaction.bytes();
    }

    if (!writeWithRetries(latest)) {
      return false;
    }

    final var chunk = new ArrayList<Map.Entry<RawKey, Version>>(REMOVAL_CHUNK_SIZE);
    for (final var entry : latest.entrySet()) {
      chunk.add(entry);
      if (chunk.size() == REMOVAL_CHUNK_SIZE) {
        onFlushed.accept(chunk);
        chunk.clear();
      }
    }
    if (!chunk.isEmpty()) {
      onFlushed.accept(chunk);
    }
    pendingBytes.addAndGet(-bytes);
    flushedSeq = group.getLast().seq();
    signalFlushed();
    return true;
  }

  private boolean writeWithRetries(final Map<RawKey, Version> latest) {
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
        LOG.warn("Failed to write a batch to the storage, will retry (attempt {})", attempt + 1, e);
        LockSupport.parkNanos(delay);
        delay = Math.min(delay * 2, MAX_RETRY_DELAY_NANOS);
      } catch (final Throwable e) {
        fail(e);
        return false;
      }
    }
  }

  private void write(final Map<RawKey, Version> latest) {
    try (final var batch = storage.newBatch()) {
      for (final var entry : latest.entrySet()) {
        final var key = entry.getKey().bytes();
        final var value = entry.getValue().value();
        if (value != null) {
          batch.put(key, Values.encode(value, encodeBuffer));
        } else if (storage.keyMayExist(key, key.length)) {
          // a key that the storage never had needs no delete; this keeps short-lived state out of
          // it
          batch.delete(key);
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

  private record PendingTransaction(
      long seq, RawKey[] keys, Version[] versions, long bytes, long committedAtNanos) {}
}
