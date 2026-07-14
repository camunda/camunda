/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.layered.zdb;

import io.camunda.zeebe.db.layered.LayeredKeyValueStore;
import java.time.Duration;
import java.util.Objects;

/**
 * Configuration of the layered stores created by a {@link LayeredZeebeDb}; every layered column
 * family receives the same settings. See {@link LayeredKeyValueStore} for what each knob controls.
 *
 * @param maxBytesPerStore soft byte budget per store; buffered (pinned) writes may exceed it, which
 *     {@link LayeredZeebeDb#overCapacity()} surfaces as the signal to run a persist round
 * @param maxBufferedBytes total buffered-bytes budget across a domain's stores; when positive, the
 *     runtime driving the domain reacts to the buffered (not yet persisted) bytes on a graduated
 *     pressure ladder (see the ladder fractions below) — the ladder and the pre-snapshot flush are
 *     what bound memory and the recovery replay window; there is deliberately no periodic persist
 *     cadence (intermediate persists write a runtime directory a crash discards, so recovery
 *     restores from snapshots and an age-based cadence bounds nothing an invariant needs). Zero
 *     (the default) disables the ladder: rounds then run on per-store over-capacity, before
 *     snapshots and behind the scheduled-task barrier only
 * @param ladderStartFraction the buffer-pressure ladder's start rung, as a fraction of {@code
 *     maxBufferedBytes}: at this fill level the runtime starts a paced persist round early (or
 *     expedites the one already in flight), so the buffer drains before pressure builds further
 * @param ladderFlatOutFraction the buffer-pressure ladder's flat-out rung, as a fraction of {@code
 *     maxBufferedBytes}: at this fill level the runtime starts a round immediately and drains it
 *     unpaced (a store exceeding its own {@code maxBytesPerStore} budget feeds this rung too); must
 *     be at least {@code ladderStartFraction}
 * @param absorbDeletes whether a delete of a never-persisted put annihilates the pair in memory so
 *     neither write ever reaches RocksDB. On by default: exact flushed flags plus negative caching
 *     make absorption unconditionally sound (a pair only annihilates when the durable store
 *     provably never held the key), and short-lived put/delete churn is exactly what the layered
 *     store exists to elide
 * @param pipelineSegmentLimit maximum number of non-persisting frozen segments per store before
 *     they are merged down, bounding read amplification
 * @param persistMinSliceBytes minimum consumed entry bytes per sub-batch slice of a paced persist
 *     drain (see {@code LayeredStoreCoordinator.PersistRound#persistSlice}); the runtime spreads a
 *     round's drain over slices of at least this size, each committed as its own inner transaction
 * @param persistPacingWindow the time window a paced drain spreads its slices across (Postgres
 *     checkpoint-spreading style): after each slice the drain waits while its progress fraction
 *     runs ahead of elapsed-time / window. The deadline only shapes disk amplitude — correctness
 *     never depends on it (every drained cut is equally valid whenever it lands) — so it defaults
 *     to a window in the order of the snapshot cadence; only ladder start-rung rounds pace, and the
 *     runtime drops the pacing (drains flat-out) once buffered state climbs further up the ladder
 *     mid-round. There is deliberately no freeze cadence either: freezes happen on demand only —
 *     right before an asynchronous view reader executes (which is the only freshness anyone
 *     consumes) and implicitly when a persist round prepares — because freezing early forfeits the
 *     free in-place overwrite absorption of the active overlay and turns it into pipeline-merge
 *     work
 */
public record LayeredZeebeDbConfig(
    long maxBytesPerStore,
    long maxBufferedBytes,
    double ladderStartFraction,
    double ladderFlatOutFraction,
    boolean absorbDeletes,
    int pipelineSegmentLimit,
    long persistMinSliceBytes,
    Duration persistPacingWindow) {

  private static final long DEFAULT_MAX_BYTES_PER_STORE = 16 * 1024 * 1024;
  private static final long DEFAULT_MAX_BUFFERED_BYTES = 0;
  private static final double DEFAULT_LADDER_START_FRACTION = 0.7;
  private static final double DEFAULT_LADDER_FLAT_OUT_FRACTION = 0.9;
  private static final boolean DEFAULT_ABSORB_DELETES = true;
  private static final int DEFAULT_PIPELINE_SEGMENT_LIMIT = 4;
  private static final long DEFAULT_PERSIST_MIN_SLICE_BYTES = 1024 * 1024;
  private static final Duration DEFAULT_PERSIST_PACING_WINDOW = Duration.ofSeconds(30);

  /** Convenience constructor defaulting the ladder rungs and the slice size. */
  public LayeredZeebeDbConfig(
      final long maxBytesPerStore,
      final long maxBufferedBytes,
      final boolean absorbDeletes,
      final int pipelineSegmentLimit,
      final Duration persistPacingWindow) {
    this(
        maxBytesPerStore,
        maxBufferedBytes,
        DEFAULT_LADDER_START_FRACTION,
        DEFAULT_LADDER_FLAT_OUT_FRACTION,
        absorbDeletes,
        pipelineSegmentLimit,
        DEFAULT_PERSIST_MIN_SLICE_BYTES,
        persistPacingWindow);
  }

  public LayeredZeebeDbConfig {
    if (maxBytesPerStore <= 0) {
      throw new IllegalArgumentException(
          "expected maxBytesPerStore to be positive, but was " + maxBytesPerStore);
    }
    if (maxBufferedBytes < 0) {
      throw new IllegalArgumentException(
          "expected maxBufferedBytes to be zero (disabled) or positive, but was "
              + maxBufferedBytes);
    }
    if (ladderStartFraction <= 0 || ladderStartFraction > 1) {
      throw new IllegalArgumentException(
          "expected ladderStartFraction to be in (0, 1], but was " + ladderStartFraction);
    }
    if (ladderFlatOutFraction < ladderStartFraction || ladderFlatOutFraction > 1) {
      throw new IllegalArgumentException(
          "expected ladderFlatOutFraction to be in [ladderStartFraction, 1] = ["
              + ladderStartFraction
              + ", 1], but was "
              + ladderFlatOutFraction);
    }
    if (pipelineSegmentLimit < 1) {
      throw new IllegalArgumentException(
          "expected pipelineSegmentLimit to be at least 1, but was " + pipelineSegmentLimit);
    }
    if (persistMinSliceBytes < 1) {
      throw new IllegalArgumentException(
          "expected persistMinSliceBytes to be at least 1, but was " + persistMinSliceBytes);
    }
    Objects.requireNonNull(persistPacingWindow, "persistPacingWindow");
    if (persistPacingWindow.isZero() || persistPacingWindow.isNegative()) {
      throw new IllegalArgumentException(
          "expected persistPacingWindow to be positive, but was " + persistPacingWindow);
    }
  }

  public static LayeredZeebeDbConfig defaults() {
    return new LayeredZeebeDbConfig(
        DEFAULT_MAX_BYTES_PER_STORE,
        DEFAULT_MAX_BUFFERED_BYTES,
        DEFAULT_ABSORB_DELETES,
        DEFAULT_PIPELINE_SEGMENT_LIMIT,
        DEFAULT_PERSIST_PACING_WINDOW);
  }
}
