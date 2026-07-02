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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Bounded per-partition buffer holding prefetched {@link Event}s, together with the concurrency
 * primitives that coordinate the background fetcher and a blocked {@code poll}.
 *
 * <p>All mutable state — the per-partition deques, the in-flight fetch counts, and the fetch
 * generation counter — is guarded by a single {@link ReentrantLock}; the {@link Condition} on that
 * lock lets a poll park until data is available (or the buffer is signalled on close). The
 * generation counter is bumped on any seek or reassignment so that fetches issued from a stale
 * cursor can be discarded when they complete.
 *
 * <p>Prefetch depth bounds how many fetches per partition may be outstanding at once: a partition
 * is eligible for another fetch while its in-flight count plus a slot for any buffered batch is
 * below the configured depth. Depth {@code 1} therefore re-fetches only once the buffer drains (the
 * historical behavior); a higher depth lets that many fetches pipeline.
 *
 * <p>Callers that need to combine a position update with a buffer mutation atomically run both
 * under {@link #runLocked(Runnable)} so the whole critical section observes a single lock hold.
 */
public final class PrefetchBuffer {

  private final ReentrantLock lock = new ReentrantLock();
  private final Condition dataAvailable = lock.newCondition();
  private final Map<TopicPartition, ArrayDeque<Event>> buffers = new LinkedHashMap<>();
  private final Map<TopicPartition, Integer> inFlight = new HashMap<>();
  private final int prefetchDepth;
  private int fetchGeneration;

  /** Creates a buffer with the historical depth-1 behavior. */
  public PrefetchBuffer() {
    this(1);
  }

  /**
   * Creates a buffer that permits up to {@code prefetchDepth} outstanding fetches (buffered batch
   * plus in-flight) per partition.
   */
  public PrefetchBuffer(final int prefetchDepth) {
    this.prefetchDepth = Math.max(1, prefetchDepth);
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
    final ArrayDeque<Event> buf = buffers.get(tp);
    if (buf != null) {
      buf.clear();
    }
  }

  /**
   * Drops buffers and in-flight markers for partitions not in {@code retained}. Caller holds lock.
   */
  public void retain(final Collection<TopicPartition> retained) {
    buffers.keySet().retainAll(retained);
    inFlight.keySet().retainAll(retained);
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
      for (final TopicPartition tp : owned) {
        final ArrayDeque<Event> buf = buffers.get(tp);
        final int bufferedSlot = (buf != null && !buf.isEmpty()) ? 1 : 0;
        int occupied = inFlight.getOrDefault(tp, 0) + bufferedSlot;
        while (occupied < prefetchDepth) {
          inFlight.merge(tp, 1, Integer::sum);
          toFetch.add(tp);
          occupied++;
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
    inFlight.computeIfPresent(tp, (ignored, count) -> count <= 1 ? null : count - 1);
  }

  /** Appends {@code event} to {@code tp}'s buffer, creating it if absent. Caller holds lock. */
  public void add(final TopicPartition tp, final Event event) {
    buffers.computeIfAbsent(tp, ignored -> new ArrayDeque<>()).add(event);
  }

  /** True if {@code tp} currently has buffered events. Caller must hold the lock. */
  public boolean hasBuffered(final TopicPartition tp) {
    final ArrayDeque<Event> buf = buffers.get(tp);
    return buf != null && !buf.isEmpty();
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
    final var partitions = new ArrayList<>(buffers.keySet());
    Collections.sort(partitions);
    for (final TopicPartition tp : partitions) {
      final ArrayDeque<Event> buf = buffers.get(tp);
      while (buf != null && !buf.isEmpty() && out.size() < maxRecords) {
        out.add(buf.poll());
      }
      if (out.size() >= maxRecords) {
        break;
      }
    }
  }

  /** True if no partition has buffered events. Caller must hold the lock. */
  public boolean isEmpty() {
    for (final ArrayDeque<Event> buf : buffers.values()) {
      if (!buf.isEmpty()) {
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

  /** Predicate consulted while a poll is parked, so a close wakes it. */
  public interface ClosedFlag {
    boolean isClosed();
  }
}
