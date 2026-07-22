/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.sink.pipeline;

import io.camunda.analytics.lake.sink.ColumnarSegmentRing;
import io.camunda.analytics.lake.sink.SealReason;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Micrometer instrumentation for one {@link SinkPipeline}. Purely observational: nothing here feeds
 * back into a trigger decision — the package-info's "flush timing is a performance knob, never
 * correctness" cuts both ways, so metrics must never become a hidden input either.
 */
final class SinkMetrics {

  private static final String PREFIX = "analytics.lake.sink.";

  private final MeterRegistry registry;
  private final Map<SealReason, Counter> sealsByReason = new EnumMap<>(SealReason.class);
  private final Timer flushDuration;
  private final Counter descriptors;
  private final Counter backpressurePauses;
  private final Counter backpressureResumes;
  private final AtomicBoolean failed = new AtomicBoolean();

  SinkMetrics(
      final MeterRegistry registry,
      final String table,
      final int sourcePartition,
      final ColumnarSegmentRing ring) {
    this.registry = registry;
    final String partition = String.valueOf(sourcePartition);

    Gauge.builder(PREFIX + "ring.sealed", ring, ColumnarSegmentRing::sealedCount)
        .description("Sealed segments currently awaiting the flush thread")
        .tag("table", table)
        .tag("partition", partition)
        .register(registry);
    Gauge.builder(PREFIX + "failed", failed, f -> f.get() ? 1 : 0)
        .description("1 once the pipeline has hit a terminal failure, 0 otherwise")
        .tag("table", table)
        .tag("partition", partition)
        .register(registry);
    for (final SealReason reason : SealReason.values()) {
      sealsByReason.put(
          reason,
          Counter.builder(PREFIX + "seals")
              .description("Segments sealed, by reason")
              .tag("table", table)
              .tag("partition", partition)
              .tag("reason", reason.name())
              .register(registry));
    }
    flushDuration =
        Timer.builder(PREFIX + "flush.duration")
            .description("Time spent closing one file-boundary window")
            .tag("table", table)
            .tag("partition", partition)
            .register(registry);
    descriptors =
        Counter.builder(PREFIX + "descriptors")
            .description("Descriptors accepted by the descriptor sink")
            .tag("table", table)
            .tag("partition", partition)
            .register(registry);
    backpressurePauses =
        Counter.builder(PREFIX + "backpressure.pause")
            .description("Times the backpressure gate was paused")
            .tag("table", table)
            .tag("partition", partition)
            .register(registry);
    backpressureResumes =
        Counter.builder(PREFIX + "backpressure.resume")
            .description("Times the backpressure gate was resumed")
            .tag("table", table)
            .tag("partition", partition)
            .register(registry);
  }

  void seal(final SealReason reason) {
    sealsByReason.get(reason).increment();
  }

  Timer.Sample startFlush() {
    return Timer.start(registry);
  }

  void stopFlush(final Timer.Sample sample) {
    sample.stop(flushDuration);
  }

  void descriptorAccepted() {
    descriptors.increment();
  }

  void backpressurePaused() {
    backpressurePauses.increment();
  }

  void backpressureResumed() {
    backpressureResumes.increment();
  }

  void markFailed() {
    failed.set(true);
  }
}
