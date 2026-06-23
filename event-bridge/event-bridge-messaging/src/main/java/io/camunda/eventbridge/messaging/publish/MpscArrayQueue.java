/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.messaging.publish;

import java.util.Objects;

/**
 * Bounded lock-free multi-producer single-consumer (MPSC) array queue.
 *
 * <p>Multiple producer threads can {@link #offer} elements concurrently. A single consumer thread
 * {@link #poll polls} elements sequentially. No locks, no allocation on the hot path.
 *
 * <h3>Concurrency model</h3>
 *
 * <ul>
 *   <li><b>Producers</b> compete via CAS on a shared {@code tail} counter to claim slots. A cached
 *       {@code producerLimit} avoids reading the volatile {@code head} on every offer — producers
 *       only refresh when the limit is reached, amortizing the volatile read cost across {@code
 *       capacity} offers.
 *   <li><b>Consumer</b> reads from the {@code head} position. Since there is exactly one consumer,
 *       a cached tail ({@code cachedTail}) avoids a volatile read of {@code tail} on every poll.
 * </ul>
 *
 * <h3>Race window</h3>
 *
 * <p>A brief window exists between a producer claiming a slot (CAS on tail) and writing the
 * element. If the consumer polls during this window, it sees {@code null} and returns — the element
 * is not lost, it will be picked up on the next poll/drain cycle.
 *
 * <h3>Null elements</h3>
 *
 * <p>Null elements are not permitted. The queue uses {@code null} as a sentinel to detect the
 * producer mid-write race window. Offering {@code null} would permanently brick the queue.
 *
 * <h3>False sharing prevention</h3>
 *
 * <p>Producer and consumer fields are separated into different classes in the inheritance
 * hierarchy. The JVM guarantees that fields from different classes are not interleaved in memory,
 * ensuring they reside on different cache lines (64 bytes on x86).
 *
 * @param <E> element type (must not be null)
 */
public final class MpscArrayQueue<E> extends MpscArrayQueueConsumerField<E> {

  /** Maximum allowed capacity. Must be a power of two to fit in an int. */
  private static final int MAX_CAPACITY = 1 << 30;

  /**
   * Creates a new queue with the given capacity, rounded up to the next power of two.
   *
   * @param capacity desired capacity (will be rounded up to the next power of two)
   * @throws IllegalArgumentException if capacity is ≤ 0 or > 2^30
   */
  public MpscArrayQueue(final int capacity) {
    super(nextPowerOfTwo(capacity));
  }

  /**
   * Offers an element to the queue. Called by producer threads.
   *
   * <p>Uses a {@code producerLimit} to avoid reading the volatile {@code head} on every call.
   * Producers only refresh the limit when they've exhausted the cached range — amortizing the
   * volatile head read across {@code capacity} offers.
   *
   * @param element the element to enqueue (must not be null)
   * @return {@code true} if the element was accepted, {@code false} if the queue is full
   * @throws NullPointerException if element is null
   */
  public boolean offer(final E element) {
    Objects.requireNonNull(element, "Element must not be null");
    final int capacity = buffer.length;

    while (true) {
      final long currentTail = (long) TAIL_HANDLE.getVolatile(this);

      // Fast path: check against cached limit
      if (currentTail >= producerLimit) {
        final long currentHead = head;

        if (currentTail - currentHead >= capacity) {
          return false; // genuinely full
        }

        producerLimit = currentHead + capacity;
      }

      if (TAIL_HANDLE.compareAndSet(this, currentTail, currentTail + 1)) {
        final int index = (int) (currentTail & mask);

        // Write the element. Since TAIL_HANDLE CAS acts as a full memory barrier,
        // a plain write here is technically safe, but using setRelease guarantees
        // the element is visible to the consumer polling this index.
        ARRAY_HANDLE.setRelease(buffer, index, element);
        return true;
      }
    }
  }

  /**
   * Polls an element from the queue. Called by the single consumer thread only.
   *
   * <p>Uses a cached tail value to avoid a volatile read on every call. The cache is only refreshed
   * when the consumer has caught up to the cached tail (cache miss).
   *
   * <p>Returns {@code null} in two cases:
   *
   * <ul>
   *   <li>The queue is empty (head has caught up to tail)
   *   <li>A producer has claimed a slot but hasn't written the element yet (brief race window)
   * </ul>
   *
   * <p>In the second case, the element is not lost — it will be available on the next poll.
   *
   * @return the next element, or {@code null} if empty or producer mid-write
   */
  public E poll() {
    final long currentHead = head;

    // Fast path: check cached tail
    if (currentHead >= cachedTail) {
      cachedTail = (long) TAIL_HANDLE.getVolatile(this);
      if (currentHead >= cachedTail) {
        return null; // genuinely empty
      }
    }

    final int index = (int) (currentHead & mask);

    // Volatile read of the element directly from the array
    @SuppressWarnings("unchecked")
    final E element = (E) ARRAY_HANDLE.getVolatile(buffer, index);

    if (element == null) {
      // Producer claimed this slot but hasn't stored the element yet.
      return null;
    }

    // Release semantics: avoiding expensive store fences
    ARRAY_HANDLE.setRelease(buffer, index, null);

    // Advance the head (single writer, plain volatile write is sufficient)
    head = currentHead + 1;
    return element;
  }

  /** Returns {@code true} if the queue appears empty. */
  public boolean isEmpty() {
    return head >= (long) TAIL_HANDLE.getVolatile(this);
  }

  /** Returns the approximate number of elements in the queue. */
  int size() {
    return (int) ((long) TAIL_HANDLE.getVolatile(this) - head);
  }

  private static int nextPowerOfTwo(final int value) {
    if (value <= 0) {
      throw new IllegalArgumentException("Capacity must be positive, got " + value);
    }
    if (value > MAX_CAPACITY) {
      throw new IllegalArgumentException("Capacity " + value + " exceeds maximum " + MAX_CAPACITY);
    }
    if ((value & (value - 1)) == 0) {
      return value; // already a power of two
    }
    return 1 << (32 - Integer.numberOfLeadingZeros(value));
  }
}
