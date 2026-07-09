/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming;

/**
 * A single-threaded unit of stream processing the {@link StreamRuntime} drives for one source
 * partition. The runtime calls {@link #init()} then {@link #restore()} once when the partition is
 * materialized, {@link #process(Object)} per record, {@link #flush()} then {@link #commit(long)} or
 * {@link #freezeCut(long)} at the commit barrier, and {@link #close()} on shutdown.
 *
 * <p>There is exactly one Task per source partition and the runtime drives it from a single thread,
 * so a Task never needs synchronization and is guaranteed per-partition single-writer semantics.
 *
 * <p><b>Durability model.</b> Every task is a self-contained shard: it owns its partition's state
 * backend, stored offset and output sink. The runtime never persists anything on a task's behalf —
 * it calls {@link #restore()} to recover the shard's state and last offset, and drives one atomic
 * per-partition cut through {@link #commit(long)} (synchronous) or {@link #freezeCut(long)}
 * (asynchronous). Shards share nothing, so partitions commit fully in parallel, and a shard with no
 * local state is rebuilt from the source start — which is what lets partitions move freely between
 * members.
 *
 * @param <R> the decoded record type the task consumes
 */
public interface Task<R> {

  /** Offset value meaning "nothing committed yet for this partition". */
  long NO_OFFSET = -1L;

  /** Processes one source record (in per-partition offset order). */
  void process(R record);

  /** Called once when the partition is materialized, before {@link #restore()}. */
  default void init() {}

  /**
   * Restores this partition's durable state and returns the last committed source offset (or {@link
   * #NO_OFFSET}), so the runtime can skip records already folded into that state. Called once when
   * the task is materialized, before any {@link #process}. Returning {@link #NO_OFFSET} means the
   * shard has no local state and the runtime rebuilds it by replaying from the source start —
   * including a task with nothing durable at all (the default), which replays on every start.
   */
  default long restore() {
    return NO_OFFSET;
  }

  /**
   * The synchronous commit cut: make produced output durable, then persist state and {@code offset}
   * in one atomic transaction the task owns. This is the produce-before-commit barrier the stop
   * path always ends on, and the whole commit for a task without {@link #freezeCut(long) frozen-cut
   * support} — the partition is suspended for its duration. Default no-op for a task with nothing
   * durable.
   */
  default void commit(final long offset) {}

  /**
   * The asynchronous commit cut: freeze this partition's cut at {@code offset} so it can be made
   * durable in the background while processing continues — capture the state delta, produced output
   * and any admission snapshots as immutable data detached from the live working state, and return
   * the {@link CommitCut} the runtime drives through persist and completion. Called on the
   * processing thread at the commit barrier, with {@code offset} the highest processed offset — the
   * frozen data must describe exactly the records up to it. Converging buffered output (typically
   * {@link #flush()}) is part of the freeze, not the caller's job.
   *
   * <p>Returning {@code null} (the default) means the task does not support frozen cuts; the
   * runtime falls back to suspending the partition for a synchronous {@link #commit(long)}. The
   * runtime freezes at most one cut at a time per partition.
   */
  default CommitCut freezeCut(final long offset) {
    return null;
  }

  /** Emit buffered/produced output so latency stays bounded (called before every commit cut). */
  default void flush() {}

  /**
   * Event-time punctuation: the runtime calls this with the partition's stream time (the max event
   * timestamp seen so far, supplied by the runtime's timestamp extractor) on the punctuation tick,
   * so time-driven work — closing/finalizing windows, pruning state — advances even for keys that
   * received no new records. No-op when the runtime has no timestamp extractor configured.
   */
  default void advanceStreamTime(final long streamTimeMs) {}

  /**
   * Wall-clock punctuation: the runtime calls this on the punctuation tick with the current
   * wall-clock time, so time-driven work runs even for a fully idle partition that produces no
   * event-time progress — finalizing a window whose grace has elapsed in real time, or timing out
   * stale state. Complements {@link #advanceStreamTime}, which advances only as records arrive.
   */
  default void punctuateWallClock(final long wallClockMs) {}

  /**
   * Whether the task should be checkpointed before the regular commit interval — e.g. a bounded
   * cache has filled with buffered writes that only the commit barrier can flush durably (an atomic
   * cut with the offset), after which the memory is reclaimed. Checked by the runtime per record;
   * default {@code false}.
   */
  default boolean needsCheckpoint() {
    return false;
  }

  /** Called once on shutdown — release resources. */
  default void close() {}
}
