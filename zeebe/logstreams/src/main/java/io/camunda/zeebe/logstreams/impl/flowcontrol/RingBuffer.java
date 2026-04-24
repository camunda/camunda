/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.logstreams.impl.flowcontrol;

import io.camunda.zeebe.util.VisibleForTesting;
import java.util.concurrent.atomic.AtomicReferenceArray;
import org.agrona.BitUtil;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A fixed-capacity ring buffer for {@link InFlightEntry} instances, specialized for the {@link
 * FlowControl}/{@link io.camunda.zeebe.logstreams.impl.log.Sequencer} threading model. It is
 * <em>not</em> a general-purpose concurrent data structure — its correctness depends on the
 * specific access patterns of those callers:
 *
 * <ul>
 *   <li>{@link #put} is called under the sequencer lock (single writer).
 *   <li>{@link #get} is only used in tests.
 *   <li>{@link #findAndRemove} is called from the stream processor thread (single caller, processes
 *       entries in order).
 * </ul>
 *
 * <p>The buffer is lossy by design: when a slot is reused after a full wraparound, the displaced
 * entry is cleaned up rather than blocking the writer. Similarly, {@link #findAndRemove} cleans up
 * all entries before the matched one, since they were skipped by the stream processor. This is
 * acceptable because displaced or skipped entries represent in-flight appends whose metrics are no
 * longer meaningful.
 *
 * <p>Each slot has volatile read/write semantics via {@link AtomicReferenceArray}, ensuring
 * visibility across threads without external synchronization.
 *
 * <h3>Sequential indexing</h3>
 *
 * <p>The buffer uses a monotonically increasing counter ({@link #nextIndex}) incremented under the
 * sequencer lock. Every slot is used before wrapping, giving effective capacity equal to the full
 * buffer size.
 *
 * <h3>Wraparound safety</h3>
 *
 * <p>Because multiple indices map to the same slot (modular indexing), a slot may be overwritten by
 * a newer index after a full wraparound. To guard against reading the wrong entry, {@link
 * #put(InFlightEntry)} stamps the entry with the sequential index before the volatile write, and
 * {@link #get(long, long)} verifies both the sequential index and the log position match.
 */
final class RingBuffer {
  private static final int DEFAULT_CAPACITY = 8 * 1024; // 8K slots
  private static final Logger LOGGER = LoggerFactory.getLogger(RingBuffer.class);

  private final AtomicReferenceArray<@Nullable InFlightEntry> buffer;
  private final int mask;

  /**
   * Monotonically increasing counter for slot assignment.
   *
   * <p>Written by the sequencer thread (under lock) in {@link #put}. Read by the stream processor
   * thread in {@link #findAndRemove} to bound the scan range.
   */
  private volatile long nextIndex = 0;

  /**
   * Tracks the sequential index of the last entry removed by {@link #findAndRemove(long)}.
   * Subsequent scans start from {@code lastProcessedIndex + 1}, avoiding re-scanning already
   * processed slots.
   *
   * <p>Only accessed by the stream processor thread in {@link #findAndRemove}. Not volatile because
   * no other thread reads or writes this field.
   */
  private long lastProcessedIndex = -1;

  /**
   * Creates a new ring buffer with at least the given capacity. The actual capacity is rounded up
   * to the next power of two. If {@code minCapacity} is 0, a default of {@value #DEFAULT_CAPACITY}
   * is used.
   *
   * @param minCapacity must be non-negative
   * @throws IllegalArgumentException if minCapacity is negative
   */
  RingBuffer(final int minCapacity) {
    if (minCapacity < 0) {
      throw new IllegalArgumentException("capacity must be non-negative, got: " + minCapacity);
    }
    final int requested = minCapacity == 0 ? DEFAULT_CAPACITY : minCapacity;
    final var actualCapacity = BitUtil.findNextPositivePowerOfTwo(requested);
    mask = actualCapacity - 1;
    buffer = new AtomicReferenceArray<>(actualCapacity);
  }

  /**
   * Puts an entry into the next sequential slot. Stamps {@code entry.position} with the assigned
   * index before the volatile write so that it is published together with the reference. If the
   * slot was occupied, the displaced entry's {@link InFlightEntry#cleanup()} is called to release
   * resources (timers, request listeners).
   *
   * <p>Must be called under the sequencer lock.
   *
   * @return the assigned sequential index
   */
  long put(final InFlightEntry entry) {
    final long index = nextIndex++;
    // Plain write, published by the volatile getAndSet below (happens-before).
    entry.position = index;
    final var displaced = buffer.getAndSet((int) (index & mask), entry);
    if (displaced != null) {
      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace(
            "Found non-null entry at index {} (highestPosition {}) while doing a put at index {}"
                + " (highestPosition {}): cleaning it up",
            displaced.position,
            displaced.highestPosition,
            index,
            entry.highestPosition);
      }
      displaced.cleanup();
    }
    return index;
  }

  /**
   * Returns the entry at the given sequential index, but only if both the index and the
   * highestPosition match. Returns null if the slot is empty, holds an entry for a different index
   * (wraparound), or holds an entry with a different highestPosition (defense-in-depth).
   */
  @VisibleForTesting
  @Nullable InFlightEntry get(final long index, final long highestPosition) {
    final var entry = buffer.get((int) (index & mask));
    return entry != null && entry.position == index && entry.highestPosition == highestPosition
        ? entry
        : null;
  }

  /**
   * Scans forward from {@link #lastProcessedIndex} to find and remove an entry by its log position.
   * Used by {@code onProcessed} from the stream processor thread, which processes entries in order.
   *
   * <p>The scan starts from the last processed entry's sequential index and iterates forward in
   * sequential index order. Each entry's {@link InFlightEntry#position} is verified against the
   * expected index to skip displaced entries (where the slot was overwritten by a newer {@link
   * #put}).
   *
   * <p>The scan range is {@code [max(lastProcessedIndex+1, nextIndex-capacity), nextIndex)}, which
   * is at most {@code capacity} iterations and in practice much less when the stream processor
   * keeps up with the writer.
   *
   * @return the entry if found, or null if the entry was displaced or not present
   */
  @Nullable InFlightEntry findAndRemove(final long highestPosition) {
    final long scanEnd = nextIndex; // volatile read — snapshot of writer progress
    final long scanStart = Math.max(lastProcessedIndex + 1, scanEnd - buffer.length());

    for (long idx = scanStart; idx < scanEnd; idx++) {
      final int slot = (int) (idx & mask);
      final var entry = buffer.get(slot);
      if (entry == null || entry.position != idx) {
        continue; // slot empty or entry displaced by a newer put
      }
      if (entry.highestPosition == highestPosition) {
        buffer.compareAndExchange(slot, entry, null);
        lastProcessedIndex = entry.position;
        return entry;
      }
      if (entry.highestPosition < highestPosition) {
        // Entry was skipped by the stream processor — clean it up
        buffer.compareAndExchange(slot, entry, null);
        entry.cleanup();
        lastProcessedIndex = entry.position;
      }
    }
    return null;
  }

  /** Returns the capacity of this ring buffer. */
  int capacity() {
    return buffer.length();
  }
}
