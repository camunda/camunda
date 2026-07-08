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
 * cut, how long the background persist takes, how often a failed persist merges back for retry, and
 * how often folding enters a write stall. Every meter is tagged by partition id only, keeping
 * cardinality low.
 *
 * <p>Optional by design: without a meter registry, {@link #NOOP} makes every recording a
 * zero-allocation no-op, so the hot path pays nothing when instrumentation is off.
 */
public interface CutMetrics {

  /** The zero-allocation no-op used when no meter registry is configured. */
  CutMetrics NOOP = new CutMetrics() {};

  /**
   * Cut instrumentation for {@code partitionId} on {@code registry}, or {@link #NOOP} when {@code
   * registry} is {@code null}.
   */
  static CutMetrics of(final MeterRegistry registry, final int partitionId) {
    return registry == null ? NOOP : new MicrometerCutMetrics(registry, partitionId);
  }

  /**
   * Wall time of the freeze on the actor thread — the residual per-commit processing pause, which
   * should read in microseconds.
   */
  default void observeFreeze(final long durationNanos) {}

  /**
   * Wall time from IO-thread pickup to persist completion. This is the pause the old synchronous
   * commit design would have imposed on the fold — the feature's measured win is the gap between
   * this timer and the freeze timer.
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
   * The Micrometer-backed implementation; meters are registered once, recording allocates nothing.
   */
  final class MicrometerCutMetrics implements CutMetrics {

    private final Timer freezeDuration;
    private final Timer persistDuration;
    private final Counter retries;
    private final Counter writeStalls;

    private MicrometerCutMetrics(final MeterRegistry registry, final int partitionId) {
      final String partition = Integer.toString(partitionId);
      freezeDuration =
          Timer.builder("eb.streaming.cut.freeze.duration")
              .description("Wall time of freezing a commit cut on the processing thread")
              .tag("partition", partition)
              .register(registry);
      persistDuration =
          Timer.builder("eb.streaming.cut.persist.duration")
              .description("Wall time of persisting a frozen commit cut on the IO thread")
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
  }
}
