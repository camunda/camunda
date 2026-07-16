/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.internals;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;

/**
 * Instrumentation of one partition's commit-cut lifecycle: how long the fold pauses to freeze a
 * cut, how long the persist takes, how often a failed persist merges back for retry, and how often
 * folding enters a write stall. Every cut records the same timers — in-flight cuts persisting on
 * the IO pool and the final inline stop cut alike — so a partition's cuts form one consistent
 * stream. Every meter is tagged by partition id only, keeping cardinality low.
 *
 * <p>Optional by design: without a meter registry, {@link #NOOP} makes every recording a
 * zero-allocation no-op, so the hot path pays nothing when instrumentation is off.
 */
public interface CutMetrics {

  /** The zero-allocation no-op used when no meter registry is configured. */
  CutMetrics NOOP = new CutMetrics() {};

  /**
   * Cut instrumentation for {@code partitionId} on {@code registry}, or {@link #NOOP} when {@code
   * registry} is {@code null}. {@code stageLabel} (nullable) distinguishes runtimes that share one
   * registry with overlapping partition-id spaces; it tags only the new {@code
   * eb.streaming.cut.early} counter — the pre-existing cut timers/counters keep their
   * partition-only tags.
   */
  static CutMetrics of(
      final MeterRegistry registry, final int partitionId, final String stageLabel) {
    return registry == null ? NOOP : new MicrometerCutMetrics(registry, partitionId, stageLabel);
  }

  /**
   * Wall time of the freeze on the actor thread — the residual per-commit processing pause, which
   * should read in microseconds.
   */
  default void observeFreeze(final long durationNanos) {}

  /**
   * Wall time from persist pickup to full cut completion (transaction plus source-offset ack). This
   * is the pause a synchronous barrier would impose on the fold — the async design's measured win
   * is the gap between this timer and the freeze timer. Recorded only for successful persists; a
   * failure counts a {@link #countRetry() retry} instead.
   */
  default void observePersist(final long durationNanos) {}

  /** A persist failed and its cut merged back for retry as part of the next, larger cut. */
  default void countRetry() {}

  /**
   * Folding entered a write stall: the budget was exhausted (active + frozen entries pinned) while
   * a cut was still in flight. Counts stall entries, not per-record re-checks.
   */
  default void countWriteStall() {}

  /**
   * A cut was triggered by {@link io.camunda.eventbridge.streaming.Task#needsCheckpoint()} (the
   * task's bounded overlay is full) rather than the commit-interval cadence. A sustained nonzero
   * rate means the overlay is undersized for the load — this partition is cutting far more often
   * than its configured interval.
   */
  default void countEarlyCut() {}

  /**
   * The Micrometer-backed implementation; meters are registered once, recording allocates nothing.
   */
  final class MicrometerCutMetrics implements CutMetrics {

    private final Timer freezeDuration;
    private final Timer persistDuration;
    private final Counter retries;
    private final Counter writeStalls;
    private final Counter earlyCuts;

    private MicrometerCutMetrics(
        final MeterRegistry registry, final int partitionId, final String stageLabel) {
      final String partition = Integer.toString(partitionId);
      freezeDuration =
          Timer.builder("eb.streaming.cut.freeze.duration")
              .description("Wall time of freezing a commit cut on the processing thread")
              .tag("partition", partition)
              .register(registry);
      persistDuration =
          Timer.builder("eb.streaming.cut.persist.duration")
              .description("Wall time of persisting a frozen commit cut")
              .tag("partition", partition)
              .register(registry);
      retries =
          Counter.builder("eb.streaming.cut.retries")
              .description("Failed cut persists that merged back for retry")
              .tag("partition", partition)
              .register(registry);
      writeStalls =
          Counter.builder("eb.streaming.write.stalls")
              .description(
                  "Entries into the budget-exhausted write stall while a cut was in flight")
              .tag("partition", partition)
              .register(registry);
      // The stage tag on the early-cut counter only: runtimes sharing one registry have
      // overlapping partition-id spaces, and an early-cut storm must be attributable to a stage.
      final Counter.Builder earlyCutsBuilder =
          Counter.builder("eb.streaming.cut.early")
              .description(
                  "Cuts triggered by needsCheckpoint() (overlay full) rather than the commit"
                      + " cadence")
              .tag("partition", partition);
      if (stageLabel != null) {
        earlyCutsBuilder.tag("stage", stageLabel);
      }
      earlyCuts = earlyCutsBuilder.register(registry);
    }

    @Override
    public void observeFreeze(final long durationNanos) {
      freezeDuration.record(durationNanos, TimeUnit.NANOSECONDS);
    }

    @Override
    public void observePersist(final long durationNanos) {
      persistDuration.record(durationNanos, TimeUnit.NANOSECONDS);
    }

    @Override
    public void countRetry() {
      retries.increment();
    }

    @Override
    public void countWriteStall() {
      writeStalls.increment();
    }

    @Override
    public void countEarlyCut() {
      earlyCuts.increment();
    }
  }
}
