/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming;

import java.util.HashMap;
import java.util.Map;
import java.util.function.ToLongFunction;

/**
 * Drives the runtime's freshness tick over every materialized task: flush emitted output (bounded
 * latency), fire the wall-clock tick (so idle partitions still do time-driven work), and — when a
 * timestamp extractor is configured — advance each partition's stream time so closed windows
 * finalize even for keys with no new records. Tracks the per-partition stream time (max event
 * timestamp seen). Does not make state durable — that is the {@link CommitBarrier}. Single-writer
 * (the runtime thread).
 *
 * @param <R> the decoded record type
 */
final class Punctuator<R> {

  private final PartitionTasks<R> tasks;
  private final ToLongFunction<R> timestampExtractor;
  private final Map<Integer, Long> streamTime = new HashMap<>();

  Punctuator(final PartitionTasks<R> tasks, final ToLongFunction<R> timestampExtractor) {
    this.tasks = tasks;
    this.timestampExtractor = timestampExtractor;
  }

  /**
   * Advances {@code partition}'s stream time from a processed record (no-op without an extractor).
   */
  void observe(final int partition, final R record) {
    if (timestampExtractor != null) {
      streamTime.merge(partition, timestampExtractor.applyAsLong(record), Math::max);
    }
  }

  /** Drops a revoked partition's stream time. */
  void forget(final int partition) {
    streamTime.remove(partition);
  }

  /** The freshness tick: flush, wall-clock punctuation, and event-time punctuation per task. */
  void punctuate() {
    final long wallClockMs = System.currentTimeMillis();
    for (final Map.Entry<Integer, Task<R>> entry : tasks.entries()) {
      final Task<R> task = entry.getValue();
      task.flush();
      // Wall-clock tick: fires even for a fully idle partition (no event-time progress), so
      // time-driven work still runs. Event-time punctuation advances only as records arrive.
      task.punctuateWallClock(wallClockMs);
      if (timestampExtractor != null) {
        final Long partitionStreamTime = streamTime.get(entry.getKey());
        if (partitionStreamTime != null) {
          task.advanceStreamTime(partitionStreamTime);
        }
      }
    }
  }
}
