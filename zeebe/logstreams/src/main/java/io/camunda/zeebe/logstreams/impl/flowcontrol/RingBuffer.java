/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.logstreams.impl.flowcontrol;

import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * A fixed-capacity ring buffer for {@link InFlightEntry} instances, indexed by position. Uses
 * power-of-two capacity with {@code & mask} indexing for O(1) operations.
 *
 * <p>Each slot has volatile read/write semantics via {@link AtomicReferenceArray}, ensuring
 * visibility across threads without external synchronization.
 *
 * <h3>Wraparound safety</h3>
 *
 * <p>Because multiple positions map to the same slot (modular indexing), a slot may be overwritten
 * by a newer position after a full wraparound. To guard against reading the wrong entry, {@link
 * #put(long, InFlightEntry)} stamps the entry with the position before the volatile write, and
 * {@link #get(long)} verifies that the entry's {@link InFlightEntry#position} matches the requested
 * position. This keeps all concurrency concerns (position stamping + volatile publication) inside
 * the ring buffer.
 */
final class RingBuffer {
  private static final int DEFAULT_CAPACITY = 8 * 1024; // 8K slots

  private final AtomicReferenceArray<InFlightEntry> buffer;
  private final int mask;

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
    final var actualCapacity = nextPowerOfTwo(requested);
    mask = actualCapacity - 1;
    buffer = new AtomicReferenceArray<>(actualCapacity);
  }

  private static int nextPowerOfTwo(final int minCapacity) {
    final var capacity = minCapacity == 1 ? 1 : Integer.highestOneBit(minCapacity - 1) << 1;
    if (capacity <= 0) {
      throw new IllegalArgumentException(
          "Expected next power of two of minCapacity to not overflow, but got %d from a minCapacity=%d"
              .formatted(capacity, minCapacity));
    }
    return capacity;
  }

  /**
   * Puts an entry at the given position. Stamps {@code entry.position} before the volatile write so
   * that it is published together with the reference. If the slot was occupied, the displaced
   * entry's {@link InFlightEntry#cleanup()} is called to release resources (timers, request
   * listeners).
   */
  void put(final long position, final InFlightEntry entry) {
    // Plain write, published by the volatile getAndSet below (happens-before).
    entry.position = position;
    final var displaced = buffer.getAndSet((int) (position & mask), entry);
    if (displaced != null) {
      displaced.cleanup();
    }
  }

  /**
   * Returns the entry at the given position, or null if the slot is empty or holds an entry for a
   * different position (i.e. the slot was overwritten by a wraparound).
   */
  InFlightEntry get(final long position) {
    final var entry = buffer.get((int) (position & mask));
    return entry != null && entry.position == position ? entry : null;
  }

  /** Removes the entry at the given position by nulling the slot. */
  void remove(final long position, final InFlightEntry entry) {
    buffer.compareAndExchange((int) (position & mask), entry, null);
  }

  /** Returns the capacity of this ring buffer. */
  int capacity() {
    return buffer.length();
  }
}
