/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.internals;

import java.util.ArrayList;
import java.util.List;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.OneToOneConcurrentArrayQueue;

/**
 * A bounded hand-off buffer for one partition: the source stage decodes ahead and enqueues here
 * while the partition's leaseholder drains and processes, so decode overlaps process. Exactly one
 * producer (the source thread) and, by the lease invariant, at most one consumer (the current
 * leaseholder) touch it, so it is backed by Agrona's lock-free single-producer/single-consumer
 * {@link OneToOneConcurrentArrayQueue} — {@code offer}/{@code drainTo} carry no lock and allocate
 * nothing on the hot path, unlike a {@code java.util.concurrent} blocking queue.
 *
 * <p>The bound is the back-pressure: when it fills, the source blocks in {@link #put}, spinning
 * then parking on a backoff idle strategy until space frees, which stops it draining the client
 * buffer, which in turn stops the client's own prefetch — a bounded path from the broker to the
 * processor with no unbounded heap. The current {@link #size} doubles as this partition's
 * processing lag.
 *
 * @param <R> the decoded record type
 */
public final class PartitionQueue<R> {

  private final OneToOneConcurrentArrayQueue<SourceEntry<R>> entries;
  // Lock-free back-pressure: spin briefly, then yield, then park up to 1ms while the queue is full.
  private final IdleStrategy backpressureIdle = new BackoffIdleStrategy(64, 8, 1_000L, 1_000_000L);

  public PartitionQueue(final int capacity) {
    if (capacity < 1) {
      throw new IllegalArgumentException("capacity must be >= 1, was " + capacity);
    }
    entries = new OneToOneConcurrentArrayQueue<>(capacity);
  }

  /** The queue's fixed capacity in entries (rounded up to a power of two by the backing queue). */
  public int capacity() {
    return entries.capacity();
  }

  /** Free capacity right now — how many more entries the source may enqueue without blocking. */
  public int remainingCapacity() {
    return entries.capacity() - entries.size();
  }

  /** Entries buffered and not yet drained — this partition's processing lag. */
  public int size() {
    return entries.size();
  }

  public boolean isEmpty() {
    return entries.isEmpty();
  }

  /**
   * Enqueues one entry without blocking, returning {@code false} when the queue is full. The source
   * uses the refusal as its signal to pause the partition rather than block on it.
   */
  public boolean offer(final SourceEntry<R> entry) {
    return entries.offer(entry);
  }

  /**
   * Enqueues one entry, blocking the source until space frees (back-pressure) via a lock-free
   * spin/park backoff. Propagates an interrupt as {@link InterruptedException} so the source can
   * wind down on shutdown.
   */
  public void put(final SourceEntry<R> entry) throws InterruptedException {
    if (entries.offer(entry)) {
      return;
    }
    backpressureIdle.reset();
    while (!entries.offer(entry)) {
      if (Thread.interrupted()) {
        throw new InterruptedException();
      }
      backpressureIdle.idle();
    }
  }

  /**
   * Moves up to {@code max} buffered entries into {@code out} in offset order, returning the number
   * moved. Called only by the current leaseholder (the single consumer).
   */
  public int drainTo(final List<SourceEntry<R>> out, final int max) {
    return entries.drainTo(out, max);
  }

  /**
   * Discards all buffered entries. Called by the source thread when a partition is repositioned
   * (rebuild) so no pre-seek record is processed; safe because the source is the sole toucher and,
   * during a reposition, no lease is held (no concurrent consumer).
   */
  public void clear() {
    while (entries.poll() != null) {
      // drain to empty
    }
  }

  /** Drains and returns all buffered entries — used to reclaim a revoked partition's buffer. */
  public List<SourceEntry<R>> drainAll() {
    final List<SourceEntry<R>> out = new ArrayList<>(size());
    entries.drainTo(out, entries.capacity());
    return out;
  }
}
