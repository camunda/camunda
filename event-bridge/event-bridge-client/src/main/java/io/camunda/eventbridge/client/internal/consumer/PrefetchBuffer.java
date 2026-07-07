/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.client.internal.consumer;

import io.camunda.eventbridge.client.Event;
import io.camunda.eventbridge.client.TopicPartition;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Bounded per-partition buffer holding prefetched {@link Event}s, together with the concurrency
 * primitives that coordinate the background fetcher and a blocked {@code poll}.
 *
 * <p>All mutable state — the per-partition {@link PartitionState}s (deque, in-flight fetch count,
 * buffered bytes) and the fetch generation counter — is guarded by a single {@link ReentrantLock};
 * the {@link Condition} on that lock lets a poll park until data is available (or the buffer is
 * signalled on close). The generation counter is bumped on any seek or reassignment so that fetches
 * issued from a stale cursor can be discarded when they complete.
 *
 * <p>Prefetch depth bounds how many fetches per partition may be outstanding at once: a partition
 * is eligible for another fetch while its in-flight count plus a slot for any buffered batch is
 * below the configured depth. Depth {@code 1} therefore re-fetches only once the buffer drains (the
 * historical behavior); a higher depth lets that many fetches pipeline.
 *
 * <p>Prefetch depth is a per-partition <em>count</em> bound; on its own, with many partitions or
 * large batches, the total buffered heap could still grow unbounded. A second bound — {@code
 * maxBufferedBytes} — caps the total payload bytes buffered across all partitions: once reached,
 * {@link #claim(List)} issues no further fetches until a {@code poll} drains the buffer back under
 * the cap. This is a soft cap (fetches already in flight still land), so total memory is bounded by
 * {@code maxBufferedBytes} plus the outstanding in-flight fetches.
 *
 * <p>Callers that need to combine a position update with a buffer mutation atomically run both
 * under {@link #runLocked(Runnable)} so the whole critical section observes a single lock hold.
 */
public final class PrefetchBuffer {

  private final ReentrantLock lock = new ReentrantLock();
  private final Condition dataAvailable = lock.newCondition();

  /**
   * Per-partition buffer, in-flight count and byte tally in one object with primitive fields —
   * hot-path counter updates (per fetch, per event) stay boxing-free.
   */
  private final Map<TopicPartition, PartitionState> partitions = new HashMap<>();

  /**
   * The partition set in sorted drain order, rebuilt only when the set changes rather than sorted
   * on every {@link #drainInto} call.
   */
  private final List<TopicPartition> sortedPartitions = new ArrayList<>();

  private boolean sortedPartitionsDirty;

  private final int prefetchDepth;
  private final long maxBufferedBytes;
  private long bufferedBytes;
  private int fetchGeneration;

  /** Creates a buffer with the historical depth-1, unbounded-bytes behavior. */
  public PrefetchBuffer() {
    this(1);
  }

  /** Creates a buffer with the given depth and no total-bytes bound (used by tests). */
  public PrefetchBuffer(final int prefetchDepth) {
    this(prefetchDepth, Long.MAX_VALUE);
  }

  /**
   * Creates a buffer that permits up to {@code prefetchDepth} outstanding fetches (buffered batch
   * plus in-flight) per partition, and buffers at most {@code maxBufferedBytes} of payload across
   * all partitions before applying fetch backpressure.
   */
  public PrefetchBuffer(final int prefetchDepth, final long maxBufferedBytes) {
    this.prefetchDepth = Math.max(1, prefetchDepth);
    this.maxBufferedBytes = Math.max(1L, maxBufferedBytes);
  }

  /** Runs {@code action} while holding the buffer lock. */
  public void runLocked(final Runnable action) {
    lock.lock();
    try {
      action.run();
    } finally {
      lock.unlock();
    }
  }

  /** Computes {@code decision} while holding the buffer lock and returns its result. */
  public <T> T supplyLocked(final Supplier<T> decision) {
    lock.lock();
    try {
      return decision.get();
    } finally {
      lock.unlock();
    }
  }

  /** Returns the current fetch generation. Caller must hold the lock. */
  public int generation() {
    return fetchGeneration;
  }

  /**
   * Bumps the fetch generation so any in-flight fetch started from the old cursor is discarded when
   * it completes. Caller must hold the lock.
   */
  public void bumpGeneration() {
    fetchGeneration++;
  }

  /** Clears the buffered events for {@code tp}, if any. Caller must hold the lock. */
  public void clear(final TopicPartition tp) {
    final PartitionState state = partitions.get(tp);
    if (state != null) {
      state.buffer.clear();
      bufferedBytes -= state.bufferedBytes;
      state.bufferedBytes = 0;
    }
  }

  /**
   * Drops buffers and in-flight markers for partitions not in {@code retained}. Caller holds lock.
   */
  public void retain(final Collection<TopicPartition> retained) {
    final Iterator<Map.Entry<TopicPartition, PartitionState>> it = partitions.entrySet().iterator();
    while (it.hasNext()) {
      final Map.Entry<TopicPartition, PartitionState> entry = it.next();
      if (!retained.contains(entry.getKey())) {
        bufferedBytes -= entry.getValue().bufferedBytes;
        it.remove();
        sortedPartitionsDirty = true;
      }
    }
  }

  /**
   * Claims fetch slots for every owned partition still below its prefetch depth and returns them.
   * The returned list may contain a partition multiple times when the depth permits more than one
   * new in-flight fetch this kick; the caller issues one fetch per occurrence. Acquires the lock.
   *
   * @return the fetch generation captured under the lock, and the partitions claimed for fetching
   */
  public Claim claim(final List<TopicPartition> owned) {
    final List<TopicPartition> toFetch = new ArrayList<>();
    final int gen;
    lock.lock();
    try {
      gen = fetchGeneration;
      // Total-bytes backpressure: while the buffer is at/over the cap, issue no new fetches. The
      // next poll drains it back under the cap, after which the caller's kick claims again.
      if (bufferedBytes < maxBufferedBytes) {
        for (final TopicPartition tp : owned) {
          final PartitionState state = state(tp);
          final int bufferedSlot = state.buffer.isEmpty() ? 0 : 1;
          int occupied = state.inFlight + bufferedSlot;
          while (occupied < prefetchDepth) {
            state.inFlight++;
            toFetch.add(tp);
            occupied++;
          }
        }
      }
    } finally {
      lock.unlock();
    }
    return new Claim(gen, toFetch);
  }

  /** The generation and partitions returned by {@link #claim(List)}. */
  public record Claim(int generation, List<TopicPartition> partitions) {}

  /** Decrements the in-flight count for {@code tp}. Caller must hold the lock. */
  public void clearInFlight(final TopicPartition tp) {
    final PartitionState state = partitions.get(tp);
    if (state != null && state.inFlight > 0) {
      state.inFlight--;
    }
  }

  /** Appends {@code event} to {@code tp}'s buffer, creating it if absent. Caller holds lock. */
  public void add(final TopicPartition tp, final Event event) {
    final PartitionState state = state(tp);
    state.buffer.add(event);
    final long size = sizeOf(event);
    bufferedBytes += size;
    state.bufferedBytes += size;
  }

  /**
   * Retained heap of an event: the length of its decoded payload (the only bytes the buffer holds).
   * Note this is not the on-wire batch size from the codec / {@link
   * io.camunda.eventbridge.client.FetchResult}: that region also counts keys, per-entry framing and
   * batch headers, and is freed once decoded — so it would overcount what stays buffered.
   */
  private static long sizeOf(final Event event) {
    final byte[] payload = event.payload();
    return payload == null ? 0L : payload.length;
  }

  /** True if {@code tp} currently has buffered events. Caller must hold the lock. */
  public boolean hasBuffered(final TopicPartition tp) {
    final PartitionState state = partitions.get(tp);
    return state != null && !state.buffer.isEmpty();
  }

  /** Wakes any poll parked on the buffer. Caller must hold the lock. */
  public void signal() {
    dataAvailable.signalAll();
  }

  /** Wakes any poll parked on the buffer, acquiring the lock. Used on close. */
  public void signalLocked() {
    lock.lock();
    try {
      dataAvailable.signalAll();
    } finally {
      lock.unlock();
    }
  }

  /**
   * Drains up to {@code maxRecords} buffered events in sorted partition order into {@code out}.
   * Caller must hold the lock.
   */
  public void drainInto(final List<Event> out, final int maxRecords) {
    if (sortedPartitionsDirty) {
      sortedPartitions.clear();
      sortedPartitions.addAll(partitions.keySet());
      Collections.sort(sortedPartitions);
      sortedPartitionsDirty = false;
    }
    for (int i = 0; i < sortedPartitions.size(); i++) {
      final PartitionState state = partitions.get(sortedPartitions.get(i));
      final ArrayDeque<Event> buf = state.buffer;
      long drained = 0;
      while (!buf.isEmpty() && out.size() < maxRecords) {
        final Event event = buf.poll();
        drained += sizeOf(event);
        out.add(event);
      }
      if (drained > 0) {
        bufferedBytes -= drained;
        state.bufferedBytes -= drained;
      }
      if (out.size() >= maxRecords) {
        break;
      }
    }
  }

  /** True if no partition has buffered events. Caller must hold the lock. */
  public boolean isEmpty() {
    for (final PartitionState state : partitions.values()) {
      if (!state.buffer.isEmpty()) {
        return false;
      }
    }
    return true;
  }

  /**
   * Drains the next batch of buffered events, blocking up to {@code timeoutNanos} for the
   * background fetcher to deliver at least one record. Returns as soon as any partition has data,
   * on timeout, or when {@code closed} becomes true (via {@link #signalLocked()}).
   *
   * @param closed supplier consulted under the lock so a close signal returns the poll
   */
  public List<Event> drain(final int maxRecords, final long timeoutNanos, final ClosedFlag closed) {
    final List<Event> out = new ArrayList<>();
    final long deadlineNanos = System.nanoTime() + Math.max(0L, timeoutNanos);
    lock.lock();
    try {
      while (!closed.isClosed() && isEmpty()) {
        final long remaining = deadlineNanos - System.nanoTime();
        if (remaining <= 0) {
          return out; // nothing arrived within the timeout
        }
        try {
          dataAvailable.awaitNanos(remaining);
        } catch (final InterruptedException e) {
          Thread.currentThread().interrupt();
          return out;
        }
      }
      drainInto(out, maxRecords);
    } finally {
      lock.unlock();
    }
    return out;
  }

  /** Returns {@code tp}'s state, creating it (and dirtying the drain order) if absent. */
  private PartitionState state(final TopicPartition tp) {
    PartitionState state = partitions.get(tp);
    if (state == null) {
      state = new PartitionState();
      partitions.put(tp, state);
      sortedPartitionsDirty = true;
    }
    return state;
  }

  /** One partition's buffered events, outstanding fetch count and buffered-payload byte tally. */
  private static final class PartitionState {
    private final ArrayDeque<Event> buffer = new ArrayDeque<>();
    private int inFlight;
    private long bufferedBytes;
  }

  /** Predicate consulted while a poll is parked, so a close wakes it. */
  public interface ClosedFlag {
    boolean isClosed();
  }
}
