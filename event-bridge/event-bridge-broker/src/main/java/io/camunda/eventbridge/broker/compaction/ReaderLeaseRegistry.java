/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.compaction;

import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracks how many readers currently hold each clean segment file, so the {@link TrashQueue} can
 * defer unlinking a superseded file until no reader is mid-transfer — the deferred-deletion
 * refcount the ADR's deletion safety checklist requires (decision 8), and the same lesson the
 * zero-copy fetch path learned when it unlinked files still held by in-flight responses.
 *
 * <p>Threading: fully thread-safe. Readers acquire and release from any thread; the cleaner actor
 * reads {@link #leaseCount} while draining.
 */
public final class ReaderLeaseRegistry {

  private final ConcurrentHashMap<Path, AtomicInteger> counts = new ConcurrentHashMap<>();

  /**
   * Acquires a lease on {@code file}, incrementing its reader count. The returned lease must be
   * released (or closed) exactly once when the reader is done.
   *
   * @param file the clean segment file
   * @return the lease
   */
  public ReaderLease acquire(final Path file) {
    counts.computeIfAbsent(file, f -> new AtomicInteger()).incrementAndGet();
    return new ReaderLease(this, file);
  }

  /** Returns the number of outstanding leases on {@code file}. */
  public int leaseCount(final Path file) {
    final AtomicInteger count = counts.get(file);
    return count == null ? 0 : count.get();
  }

  void release(final Path file) {
    counts.computeIfPresent(
        file,
        (f, count) -> {
          final int remaining = count.decrementAndGet();
          if (remaining < 0) {
            throw new IllegalStateException("Reader lease released more times than acquired: " + f);
          }
          return remaining == 0 ? null : count;
        });
  }
}
