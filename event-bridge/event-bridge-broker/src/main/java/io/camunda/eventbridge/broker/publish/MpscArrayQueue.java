/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.publish;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * Bounded lock-free multi-producer single-consumer (MPSC) array queue.
 *
 * <p>Multiple producer threads can {@link #offer} elements concurrently. A single consumer thread
 * {@link #poll polls} or {@link #drain drains} elements sequentially. No locks, no allocation on
 * the hot path.
 *
 * <p>Capacity must be a power of two for efficient index computation via bitmask.
 *
 * <h3>Concurrency model</h3>
 *
 * <ul>
 *   <li><b>Producers</b> compete via CAS on a shared {@code tail} counter to claim slots. Once a
 *       slot is claimed, the producer writes the element and returns. If the queue is full, {@link
 *       #offer} returns {@code false} immediately (backpressure).
 *   <li><b>Consumer</b> reads from the {@code head} position. Since there is exactly one consumer,
 *       the head counter is a plain {@code long} — no CAS needed.
 * </ul>
 *
 * <h3>Race window</h3>
 *
 * <p>A brief window exists between a producer claiming a slot (CAS on tail) and writing the
 * element. If the consumer polls during this window, it sees {@code null} and returns — the element
 * is not lost, it will be picked up on the next poll/drain cycle.
 *
 * <h3>Memory visibility</h3>
 *
 * <p>{@link AtomicReferenceArray} provides volatile read/write semantics per slot, guaranteeing
 * that a producer's write is visible to the consumer without explicit synchronization.
 *
 * @param <E> element type
 */
public final class MpscArrayQueue<E> {

  private final AtomicReferenceArray<E> buffer;
  private final int mask;

  /** Shared producer counter. Producers CAS to claim the next available slot. */
  private final AtomicLong tail = new AtomicLong(0);

  /** Consumer-only counter. Incremented after each successful poll. */
  private long head = 0;

  /**
   * Creates a new queue with the given capacity, rounded up to the next power of two.
   *
   * @param capacity desired capacity (will be rounded up to the next power of two)
   */
  public MpscArrayQueue(final int capacity) {
    final int actualCapacity = nextPowerOfTwo(capacity);
    buffer = new AtomicReferenceArray<>(actualCapacity);
    mask = actualCapacity - 1;
  }

  /**
   * Offers an element to the queue. Called by producer threads.
   *
   * <p>Producers compete via CAS to claim a slot. If the CAS fails (another producer claimed the
   * slot first), the producer retries with the next position. If the queue is full, returns {@code
   * false} immediately — no blocking, no spinning.
   *
   * @param element the element to enqueue (must not be null)
   * @return {@code true} if the element was accepted, {@code false} if the queue is full
   */
  public boolean offer(final E element) {
    while (true) {
      final long currentTail = tail.get();

      if (currentTail - head >= buffer.length()) {
        return false;
      }

      if (tail.compareAndSet(currentTail, currentTail + 1)) {
        buffer.set((int) (currentTail & mask), element);
        return true;
      }
    }
  }

  /**
   * Polls an element from the queue. Called by the single consumer thread only.
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
    if (head >= tail.get()) {
      return null;
    }

    final int index = (int) (head & mask);
    final E element = buffer.get(index);

    if (element == null) {
      return null;
    }

    buffer.set(index, null);
    head++;
    return element;
  }

  /**
   * Drains up to {@code limit} elements into the provided list. Called by the single consumer
   * thread only.
   *
   * <p>Stops when the queue is empty, a producer is mid-write, or the limit is reached.
   *
   * @param target list to drain elements into
   * @param limit maximum number of elements to drain
   * @return the number of elements drained
   */
  public int drain(final List<E> target, final int limit) {
    int count = 0;

    while (count < limit) {
      final E element = poll();
      if (element == null) {
        break;
      }
      target.add(element);
      count++;
    }

    return count;
  }

  /**
   * Returns {@code true} if the queue appears empty. May return {@code false} briefly while a
   * producer is mid-write (slot claimed but element not yet written).
   */
  public boolean isEmpty() {
    return head >= tail.get();
  }

  /**
   * Returns the approximate number of elements in the queue. May be temporarily inaccurate during
   * concurrent offers.
   */
  int size() {
    return (int) (tail.get() - head);
  }

  private static int nextPowerOfTwo(final int value) {
    return 1 << (32 - Integer.numberOfLeadingZeros(value - 1));
  }
}
