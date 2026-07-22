/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.sink;

/**
 * Fixed-size single-producer/single-consumer ring of pre-allocated {@link Segment}s — the sink's
 * entire memory budget and its backpressure mechanism in one structure.
 *
 * <p>Segment states are derived from two monotonic counters, never stored: the segment at {@code
 * head} is FILLING (poll thread's exclusive property), segments in {@code [tail, head)} are SEALED
 * awaiting encoding (flush thread's queue), everything else is FREE. "Full" is the geometric
 * absence of a free slot ({@code head - tail == capacity - 1} when sealing would leave no FILLING
 * slot) — backpressure is structure, not policy.
 *
 * <p>Concurrency contract: the poll thread alone calls {@link #filling()} and {@link #seal}; the
 * flush thread alone calls {@link #take()} and {@link #release}. All plain writes into a segment
 * happen-before the volatile counter store that publishes it to the other side. No locks, no CAS —
 * the single-writer-per-counter property is the whole synchronization story. A third thread
 * touching this class is a design violation.
 */
public final class ColumnarSegmentRing {

  private final Segment[] segments;
  private final int capacity;
  private final BackpressureGate gate;

  private volatile long head; // advanced only by the poll thread
  private volatile long tail; // advanced only by the flush thread

  public ColumnarSegmentRing(final Segment[] segments, final BackpressureGate gate) {
    if (segments.length < 2) {
      throw new IllegalArgumentException("ring needs at least 2 segments");
    }
    this.segments = segments;
    capacity = segments.length;
    this.gate = gate;
  }

  // ---- poll-thread side ------------------------------------------------

  /** The segment currently being filled. Poll thread only. */
  public Segment filling() {
    return segments[(int) (head % capacity)];
  }

  /**
   * Seals the filling segment and advances to the next slot. Poll thread only. Callers must not
   * seal an empty segment (time triggers on empty segments are the pipeline's job to suppress).
   *
   * @return true if sealed and a new FILLING slot is available; false = ring full — the gate has
   *     been paused, the caller must stop consuming and retry after resume
   */
  public boolean seal(final SealReason reason) {
    if (head - tail == capacity - 1) {
      gate.pause();
      return false;
    }
    filling().markSealed(reason);
    head++; // volatile store: publishes the sealed segment's contents to the flush thread
    return true;
  }

  // ---- flush-thread side -----------------------------------------------

  /** Next sealed segment in order, or null if none. Flush thread only. Does not block. */
  public Segment take() {
    return tail < head ? segments[(int) (tail % capacity)] : null;
  }

  /**
   * Recycles an encoded segment and frees its slot. Flush thread only; must be called with the
   * segment last returned by {@link #take()}.
   */
  public void release(final Segment segment) {
    if (segment != segments[(int) (tail % capacity)]) {
      throw new IllegalStateException("release out of order: not the segment at tail");
    }
    segment.reset();
    final boolean wasFull = head - tail == capacity - 1;
    tail++; // volatile store: publishes the free slot to the poll thread
    if (wasFull) {
      gate.resume();
    }
  }

  // ---- observability ----------------------------------------------------

  public int sealedCount() {
    return (int) (head - tail);
  }

  public int capacity() {
    return capacity;
  }
}
