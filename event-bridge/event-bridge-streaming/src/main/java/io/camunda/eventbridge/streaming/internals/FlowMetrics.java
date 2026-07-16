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
 * partition task. A task owns exactly one instance, constructed at task-open time and wired into
 * every {@code Aggregation} it drives ({@code SegmentSealingAggregation#metrics}, {@code
 * SegmentMergingAggregation#metrics}). Every meter is tagged by stage <em>and</em> partition —
 * per-partition attribution is the point: a single wedged partition must be visible under the
 * healthy partitions' aggregate, not averaged away by it.
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

  /** The shared no-op recorder {@link #deltaMergedCounter} returns when uninstrumented. */
  Runnable NOOP_DELTA_COUNTER = () -> {};

  /**
   * Flow instrumentation for {@code stage} ({@code "projection"} or {@code "aggregation"}) and
   * {@code partition} on {@code registry}, or {@link #NOOP} when {@code registry} is {@code null}.
   */
  static FlowMetrics of(final MeterRegistry registry, final String stage, final int partition) {
    return registry == null ? NOOP : new MicrometerFlowMetrics(registry, stage, partition);
  }

  /**
   * One record left the task's {@code process()} loop (folded or skipped alike). The unit is the
   * stage's source record: a Zeebe record in the projection stage, a whole shuffle envelope (a
   * batch of cell deltas) in the aggregation stage.
   */
  default void countRecordProcessed() {}

  /**
   * A producer duplicate was skipped before the fold: either the pre-fold position watermark
   * (projection) or the segment-coordinate dedup (aggregation).
   */
  default void countDedupSkipped() {}

  /** The projection side sealed a completed segment (one count per seal, not per sealed cell). */
  default void countSegmentSealed() {}

  /**
   * A pre-resolved recorder for the aggregation side's merge accounting, tagged by the tier's
   * window size: each run counts one deduped cell delta accepted into that tier's running cell. The
   * tier tag keeps the rate honest — a composite delta is rolled into <em>every</em> tier, so an
   * untiered counter would inflate by the tier count. Resolved once per merger at wiring time;
   * running the recorder allocates nothing.
   */
  default Runnable deltaMergedCounter(final long tierWindowMs) {
    return NOOP_DELTA_COUNTER;
  }

  /**
   * The Micrometer-backed implementation; meters are registered once, recording allocates nothing.
   */
  final class MicrometerFlowMetrics implements FlowMetrics {

    private final MeterRegistry registry;
    private final String stage;
    private final String partition;
    private final Counter recordsProcessed;
    private final Counter dedupSkipped;
    private final Counter segmentsSealed;

    private MicrometerFlowMetrics(
        final MeterRegistry registry, final String stage, final int partition) {
      this.registry = registry;
      this.stage = stage;
      this.partition = Integer.toString(partition);
      recordsProcessed =
          Counter.builder("eb.streaming.records.processed")
              .description(
                  "Records the task's process() loop consumed (folded or skipped); the unit is"
                      + " the stage's source record")
              .tag("stage", stage)
              .tag("partition", this.partition)
              .register(registry);
      dedupSkipped =
          Counter.builder("eb.streaming.dedup.skipped")
              .description(
                  "Producer duplicates skipped before the fold; expected right after a restart")
              .tag("stage", stage)
              .tag("partition", this.partition)
              .register(registry);
      segmentsSealed =
          Counter.builder("eb.streaming.segments.sealed")
              .description("Completed segments sealed (one per seal, not per sealed cell)")
              .tag("stage", stage)
              .tag("partition", this.partition)
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
    public Runnable deltaMergedCounter(final long tierWindowMs) {
      // Resolved once per merger at wiring time; Micrometer returns the existing counter for a
      // repeated id, so sibling cubes' same-tier mergers share one counter per (stage, partition,
      // tier).
      final Counter deltasMerged =
          Counter.builder("eb.streaming.deltas.merged")
              .description("Deduped cell deltas accepted into this tier's running cells")
              .tag("stage", stage)
              .tag("partition", partition)
              .tag("tier", Long.toString(tierWindowMs))
              .register(registry);
      return deltasMerged::increment;
    }
  }
}
