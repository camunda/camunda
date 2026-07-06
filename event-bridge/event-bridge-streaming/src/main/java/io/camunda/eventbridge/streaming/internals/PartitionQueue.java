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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * A bounded hand-off buffer for one partition: the source stage decodes ahead and enqueues here
 * while the partition's leaseholder drains and processes, so decode overlaps process. Exactly one
 * producer (the source thread) and, by the lease invariant, at most one consumer (the current
 * leaseholder) touch it at a time, so it never needs more than the queue's own synchronization.
 *
 * <p>The bound is the back-pressure: when it fills, the source blocks in {@link #put}, which stops
 * it draining the client buffer, which in turn stops the client's own prefetch — a bounded path
 * from the broker to the processor with no unbounded heap. The current {@link #size} doubles as
 * this partition's processing lag.
 *
 * @param <R> the decoded record type
 */
public final class PartitionQueue<R> {

  private final BlockingQueue<SourceEntry<R>> entries;

  public PartitionQueue(final int capacity) {
    if (capacity < 1) {
      throw new IllegalArgumentException("capacity must be >= 1, was " + capacity);
    }
    entries = new ArrayBlockingQueue<>(capacity);
  }

  /** The queue's fixed capacity in entries. */
  public int capacity() {
    return size() + entries.remainingCapacity();
  }

  /** Free capacity right now — how many more entries the source may enqueue without blocking. */
  public int remainingCapacity() {
    return entries.remainingCapacity();
  }

  /** Entries buffered and not yet drained — this partition's processing lag. */
  public int size() {
    return entries.size();
  }

  public boolean isEmpty() {
    return entries.isEmpty();
  }

  /**
   * Enqueues one entry, blocking the source until space frees (back-pressure). Propagates an
   * interrupt as {@link InterruptedException} so the source can wind down on shutdown.
   */
  public void put(final SourceEntry<R> entry) throws InterruptedException {
    entries.put(entry);
  }

  /**
   * Moves up to {@code max} buffered entries into {@code out} in offset order, returning the number
   * moved. Called only by the current leaseholder.
   */
  public int drainTo(final List<SourceEntry<R>> out, final int max) {
    return entries.drainTo(out, max);
  }

  /**
   * Discards all buffered entries. Called by the source thread when a partition is repositioned
   * (rebuild) so no pre-seek record is processed; safe because the source is the sole producer and,
   * during a reposition, no lease is held.
   */
  public void clear() {
    entries.clear();
  }

  /** Drains and returns all buffered entries — used to reclaim a revoked partition's buffer. */
  public List<SourceEntry<R>> drainAll() {
    final List<SourceEntry<R>> out = new ArrayList<>(size());
    entries.drainTo(out);
    return out;
  }
}
