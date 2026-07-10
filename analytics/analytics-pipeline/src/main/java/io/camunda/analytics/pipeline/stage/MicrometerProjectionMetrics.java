/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.pipeline.stage;

import io.camunda.analytics.projection.ProjectionMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * The base projection's correctness signals as Micrometer counters, one set per source partition.
 * {@code analytics.projection.duplicate.skipped} is expected traffic (exporter retries absorbed by
 * the pre-fold watermark, ADR 0007); {@code fold.row.missing} and {@code fact.dropped} must stay at
 * zero — see {@link ProjectionMetrics}.
 */
public final class MicrometerProjectionMetrics implements ProjectionMetrics {

  private final Counter duplicateSkipped;
  private final Counter foldRowMissing;
  private final Counter factDropped;

  public MicrometerProjectionMetrics(final MeterRegistry registry, final int partition) {
    final String partitionTag = String.valueOf(partition);
    duplicateSkipped =
        Counter.builder("analytics.projection.duplicate.skipped")
            .description(
                "Producer duplicates absorbed by the pre-fold watermark — expected under exporter retries")
            .tag("partition", partitionTag)
            .register(registry);
    foldRowMissing =
        Counter.builder("analytics.projection.fold.row.missing")
            .description("Must stay zero; a fold met a missing row")
            .tag("partition", partitionTag)
            .register(registry);
    factDropped =
        Counter.builder("analytics.projection.fact.dropped")
            .description("Must stay zero; a derivation lost its fact")
            .tag("partition", partitionTag)
            .register(registry);
  }

  @Override
  public void duplicateSkipped() {
    duplicateSkipped.increment();
  }

  @Override
  public void foldRowMissing() {
    foldRowMissing.increment();
  }

  @Override
  public void factDropped() {
    factDropped.increment();
  }
}
