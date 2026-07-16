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

/**
 * The "am I keeping up" lag pack: records folded, producer duplicates skipped before the fold,
 * segments sealed (the projection side) and deltas merged (the aggregation side), one group per
 * pipeline stage. A stage owns exactly one instance, constructed once at task-open time and wired
 * into every {@code Aggregation} it drives ({@code SegmentSealingAggregation#metrics}, {@code
 * SegmentMergingAggregation#metrics}) — the same instance backs every meter this stage's records
 * move.
 *
 * <p>A replay's dedup-skip spike right after restart is expected traffic (the pre-fold watermark
 * and the segment-coordinate dedup both absorbing the resume gap); a nonzero rate <em>outside</em>
 * a restart window signals duplicate source traffic. {@code records.processed} flat while the
 * source keeps moving is the wedged signature.
 *
 * <p>Optional by design: without a meter registry, {@link #NOOP} makes every recording a
 * zero-allocation no-op, mirroring {@link CutMetrics}.
 */
public interface FlowMetrics {

  /** The zero-allocation no-op used when no meter registry is configured. */
  FlowMetrics NOOP = new FlowMetrics() {};

  /**
   * Flow instrumentation for {@code stage} ({@code "projection"} or {@code "aggregation"}) on
   * {@code registry}, or {@link #NOOP} when {@code registry} is {@code null}.
   */
  static FlowMetrics of(final MeterRegistry registry, final String stage) {
    return registry == null ? NOOP : new MicrometerFlowMetrics(registry, stage);
  }

  /** One record left the task's {@code process()} loop (folded or skipped alike). */
  default void countRecordProcessed() {}

  /**
   * A producer duplicate was skipped before the fold: either the pre-fold position watermark
   * (projection) or the segment-coordinate dedup (aggregation).
   */
  default void countDedupSkipped() {}

  /** The projection side sealed a completed segment (one count per seal, not per sealed cell). */
  default void countSegmentSealed() {}

  /** The aggregation side accepted a deduped delta into a running cell. */
  default void countDeltaMerged() {}

  /**
   * The Micrometer-backed implementation; meters are registered once, recording allocates nothing.
   */
  final class MicrometerFlowMetrics implements FlowMetrics {

    private final Counter recordsProcessed;
    private final Counter dedupSkipped;
    private final Counter segmentsSealed;
    private final Counter deltasMerged;

    private MicrometerFlowMetrics(final MeterRegistry registry, final String stage) {
      recordsProcessed =
          Counter.builder("eb.streaming.records.processed")
              .description("Records the task's process() loop consumed (folded or skipped)")
              .tag("stage", stage)
              .register(registry);
      dedupSkipped =
          Counter.builder("eb.streaming.dedup.skipped")
              .description(
                  "Producer duplicates skipped before the fold; expected right after a restart")
              .tag("stage", stage)
              .register(registry);
      segmentsSealed =
          Counter.builder("eb.streaming.segments.sealed")
              .description("Completed segments sealed (one per seal, not per sealed cell)")
              .tag("stage", stage)
              .register(registry);
      deltasMerged =
          Counter.builder("eb.streaming.deltas.merged")
              .description("Deduped segment deltas accepted into a running cell")
              .tag("stage", stage)
              .register(registry);
    }

    @Override
    public void countRecordProcessed() {
      recordsProcessed.increment();
    }

    @Override
    public void countDedupSkipped() {
      dedupSkipped.increment();
    }

    @Override
    public void countSegmentSealed() {
      segmentsSealed.increment();
    }

    @Override
    public void countDeltaMerged() {
      deltasMerged.increment();
    }
  }
}
