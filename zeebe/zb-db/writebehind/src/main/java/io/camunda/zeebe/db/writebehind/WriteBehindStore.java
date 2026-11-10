/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.writebehind;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.camunda.zeebe.db.DbIterator;
import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.db.RawDbAccess;
import io.camunda.zeebe.db.ZeebeDbException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.StampedLock;
import org.agrona.ExpandableArrayBuffer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Committed state of one write-behind DB, shared by all its transaction contexts.
 *
 * <p>A commit publishes the transaction's versions to the dirty index and queues the transaction.
 * One writer thread takes the queued transactions in commit order and writes each group of them to
 * the storage as one atomic batch. So the storage always holds every transaction up to some
 * sequence and none after it, and a checkpoint at any moment is transaction-consistent.
 *
 * <p>Values are held as objects, never as bytes: the dirty index holds exactly the versions that
 * the storage doesn't have yet, and the read cache holds values decoded from the storage. A commit
 * invalidates the keys it writes. Only the writer thread encodes values.
 *
 * <p>{@link #publishLock} makes three things atomic: publishing a transaction, removing a flushed
 * group from the dirty index, and taking a read view (a storage iterator plus a copy of the dirty
 * index). Nothing that holds it blocks on anything else.
 */
final class WriteBehindStore implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger("io.camunda.zeebe.db.writebehind");
  private static final PendingTransaction STOP =
      new PendingTransaction(Long.MAX_VALUE, new RawKey[0], new Version[0], 0);
  private static final long INITIAL_RETRY_DELAY_NANOS = TimeUnit.MILLISECONDS.toNanos(10);
  private static final long MAX_RETRY_DELAY_NANOS = TimeUnit.SECONDS.toNanos(1);

  private final RawDbAccess storage;
  private final WriteBehindConfiguration config;
  private final ConcurrentSkipListMap<RawKey, Version> dirty = new ConcurrentSkipListMap<>();
  private final @Nullable Cache<RawKey, Version> cache;
  private final StampedLock publishLock = new StampedLock();
  private final LinkedBlockingQueue<PendingTransaction> queue = new LinkedBlockingQueue<>();
  private final AtomicLong pendingBytes = new AtomicLong();
  private final ReentrantLock flushLock = new ReentrantLock();
  private final Condition flushed = flushLock.newCondition();
  private final Thread writer;

  // only used by the writer thread
  private final ExpandableArrayBuffer encodeBuffer = new ExpandableArrayBuffer();

  // guarded by publishLock's write side
  private long lastSeq;

  // written under publishLock's write side, read without it to fail fast
  private volatile boolean closed;

  private volatile long committedSeq;
  private volatile long flushedSeq;
  private volatile @Nullable Throwable failure;

  WriteBehindStore(final RawDbAccess storage, final WriteBehindConfiguration config) {
    this.storage = storage;
    this.config = config;
    cache =
        config.cacheMaxBytes() > 0
            ? Caffeine.newBuilder()
                .maximumWeight(config.cacheMaxBytes())
                .<RawKey, Version>weigher((key, version) -> version.weigh(key))
                .build()
            : null;
    writer = new Thread(this::runWriter, "zb-db-write-behind");
    writer.setDaemon(true);
    writer.start();
  }

  /**
   * Publishes a transaction and queues it for the writer. Waits first while too many bytes are
   * unflushed. A transaction above the limit on its own still commits once the queue is empty.
   *
   * @param values the new value per key, or null for a delete; the store takes ownership and nobody
   *     may change them afterwards
   */
  void commit(final RawKey[] keys, final @Nullable DbValue[] values) {
    ensureUsable();
    awaitPendingBelowLimit();

    final var versions = new Version[keys.length];
    final long stamp = publishLock.writeLock();
    try {
      ensureUsable();
      final long seq = ++lastSeq;
      long bytes = 0;
      for (int i = 0; i < keys.length; i++) {
        final var version = new Version(values[i], seq);
        versions[i] = version;
        bytes += version.weigh(keys[i]);
        dirty.put(keys[i], version);
        if (cache != null) {
          cache.invalidate(keys[i]);
        }
      }
      pendingBytes.addAndGet(bytes);
      queue.add(new PendingTransaction(seq, keys, versions, bytes));
      committedSeq = seq;
    } finally {
      publishLock.unlockWrite(stamp);
    }
  }

  /**
   * Reads the latest committed value, or null if there is none. A value read from the storage is
   * decoded into a new instance of {@code prototype}'s class and cached.
   *
   * <p>The result is shared with the store: callers must copy it before handing it out.
   */
  @Nullable DbValue get(final RawKey key, final DbValue prototype) {
    ensureUsable();
    final var unflushed = dirty.get(key);
    if (unflushed != null) {
      return unflushed.value();
    }
    if (cache != null) {
      final var cached = cache.getIfPresent(key);
      if (cached != null) {
        return cached.value();
      }
    }

    // Under the read lock no commit can publish this key and no flush can remove it, so if it isn't
    // in the dirty index now, the storage has its latest value and nothing can make the cached copy
    // stale before a later commit invalidates it.
    final long stamp = publishLock.readLock();
    try {
      final var latest = dirty.get(key);
      if (latest != null) {
        return latest.value();
      }
      final var bytes = storage.get(key.bytes(), key.bytes().length);
      final var value = bytes == null ? null : Values.decode(bytes, prototype);
      if (cache != null) {
        cache.put(key, new Version(value, 0));
      }
      return value;
    } finally {
      publishLock.unlockRead(stamp);
    }
  }

  /** Whether the key has a committed value. Doesn't decode or cache what it reads. */
  boolean exists(final RawKey key) {
    ensureUsable();
    final var unflushed = dirty.get(key);
    if (unflushed != null) {
      return !unflushed.isTombstone();
    }
    if (cache != null) {
      final var cached = cache.getIfPresent(key);
      if (cached != null) {
        return !cached.isTombstone();
      }
    }
    final long stamp = publishLock.readLock();
    try {
      final var latest = dirty.get(key);
      if (latest != null) {
        return !latest.isTombstone();
      }
      return storage.get(key.bytes(), key.bytes().length) != null;
    } finally {
      publishLock.unlockRead(stamp);
    }
  }

  /**
   * Takes a point-in-time view of the committed state for keys that start with the given prefix: a
   * storage iterator and a copy of the matching dirty entries, taken together.
   */
  View openView(final byte[] prefix, final int prefixLength) {
    ensureUsable();
    final var from = RawKey.copyOf(prefix, prefixLength);
    final var to = RawKey.prefixEnd(prefix, prefixLength);
    final long stamp = publishLock.readLock();
    try {
      final var storageIterator = storage.newIterator();
      final var range = to == null ? dirty.tailMap(from) : dirty.subMap(from, to);
      return new View(storageIterator, new TreeMap<>(range));
    } finally {
      publishLock.unlockRead(stamp);
    }
  }

  /**
   * Waits until the storage holds every transaction committed before this call. Fails if the writer
   * fails or the flush takes longer than the configured timeout.
   */
  void awaitFlushed() {
    final long target = committedSeq;
    long remaining = config.flushTimeout().toNanos();
    flushLock.lock();
    try {
      while (flushedSeq < target) {
        ensureUsable();
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

  /** Throws if the writer failed or the store is closed, so that callers fail fast. */
  void ensureUsable() {
    final var cause = failure;
    if (cause != null) {
      throw new IllegalStateException(
          "Expected the write-behind writer to keep the storage up to date, but it failed", cause);
    }
    if (closed) {
      throw new IllegalStateException(
          "Expected the write-behind store to be open, but it is closed");
    }
  }

  /** Stops accepting commits, lets the writer flush what is queued, and stops it. */
  @Override
  public void close() {
    final long stamp = publishLock.writeLock();
    try {
      if (closed) {
        return;
      }
      closed = true;
      queue.add(STOP);
    } finally {
      publishLock.unlockWrite(stamp);
    }
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

  private void awaitPendingBelowLimit() {
    if (pendingBytes.get() <= config.maxPendingBytes()) {
      return;
    }
    flushLock.lock();
    try {
      while (pendingBytes.get() > config.maxPendingBytes()) {
        ensureUsable();
        flushed.await();
      }
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while waiting for the write-behind flush", e);
    } finally {
      flushLock.unlock();
    }
  }

  private void runWriter() {
    final var group = new ArrayList<PendingTransaction>();
    try {
      while (true) {
        group.add(queue.take());
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

    final long stamp = publishLock.writeLock();
    try {
      latest.forEach(dirty::remove);
    } finally {
      publishLock.unlockWrite(stamp);
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

  private void signalFlushed() {
    flushLock.lock();
    try {
      flushed.signalAll();
    } finally {
      flushLock.unlock();
    }
  }

  /** A storage iterator plus the dirty entries of the same instant, restricted to a prefix. */
  record View(DbIterator storageIterator, NavigableMap<RawKey, Version> dirty) {}

  private record PendingTransaction(long seq, RawKey[] keys, Version[] versions, long bytes) {}
}
