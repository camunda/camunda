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
 * partition. The runtime calls {@link #init()} once after durable state is restored, {@link
 * #process(Object)} per record, {@link #flush()} then {@link #checkpoint()} at the commit barrier,
 * and {@link #close()} on shutdown.
 *
 * <p>There is exactly one Task per source partition and the runtime drives it from a single thread,
 * so a Task never needs synchronization and is guaranteed per-partition single-writer semantics.
 *
 * <p><b>Durability model.</b> By default the runtime owns durability: it persists the consumed
 * offset and calls {@link #checkpoint()} / {@link #preCommitFlush()} around a shared transaction. A
 * task that owns per-partition state (its own state backend, offset store and output sink — the
 * sharded model) instead returns {@code true} from {@link #ownsDurability()} and takes over: the
 * runtime then calls {@link #restore()} to recover its state and last offset, and {@link
 * #commit(long)} to make one atomic per-partition cut. This is what lets each partition be an
 * isolated shard.
 *
 * @param <R> the decoded record type the task consumes
 */
public interface Task<R> {

  /** Offset value meaning "nothing committed yet for this partition". */
  long NO_OFFSET = -1L;

  /** Processes one source record (in per-partition offset order). */
  void process(R record);

  /** Called once after durable state has been restored, before any {@link #process}. */
  default void init() {}

  /**
   * Whether this task owns its partition's durability (its own state backend, offset store and
   * output sink) rather than deferring to the runtime's shared offset store and transaction. When
   * {@code true} the runtime drives {@link #restore()} and {@link #commit(long)} instead of {@link
   * #checkpoint()} + its own offset store. Default {@code false}.
   */
  default boolean ownsDurability() {
    return false;
  }

  /**
   * Restores this partition's durable state and returns the last committed source offset (or {@link
   * #NO_OFFSET}), so the runtime can skip records already folded into that state. Called once when
   * the task is materialized, before any {@link #process}. Only consulted when {@link
   * #ownsDurability()} is {@code true}.
   */
  default long restore() {
    return NO_OFFSET;
  }

  /**
   * The produce-before-commit barrier for this partition: make produced output durable, then
   * persist state and {@code offset} in one atomic transaction the task owns. Only called when
   * {@link #ownsDurability()} is {@code true}; otherwise the runtime persists the offset and calls
   * {@link #checkpoint()} in its own transaction.
   */
  default void commit(final long offset) {}

  /**
   * Freezes this partition's commit cut at {@code offset} so it can be made durable in the
   * background while processing continues: capture the state delta, produced output and any
   * admission snapshots as immutable data detached from the live working state, and return the
   * {@link CommitCut} the runtime drives through persist and completion. Called on the processing
   * thread at the commit barrier, with {@code offset} the highest processed offset — the frozen
   * data must describe exactly the records up to it. Converging buffered output (typically {@link
   * #flush()}) is part of the freeze, not the caller's job.
   *
   * <p>Returning {@code null} (the default) means the task does not support frozen cuts; the
   * runtime falls back to suspending the partition for a synchronous {@link #commit(long)} / {@link
   * #checkpoint()}. The runtime freezes at most one cut at a time per partition.
   */
  default CommitCut freezeCut(final long offset) {
    return null;
  }

  /** Emit buffered/produced output so latency stays bounded (called before {@link #checkpoint}). */
  default void flush() {}

  /**
   * Make this partition's produced output durable at its destination — the produce-before-commit
   * step, run for one partition just before its offset advances. A task that owns its own output
   * sink (e.g. a per-partition publisher) flushes it here so the shard is self-contained; default
   * no-op for tasks whose output is made durable elsewhere.
   */
  default void preCommitFlush() {}

  /** Make working state durable; invoked inside the runtime's checkpoint transaction. */
  default void checkpoint() {}

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
