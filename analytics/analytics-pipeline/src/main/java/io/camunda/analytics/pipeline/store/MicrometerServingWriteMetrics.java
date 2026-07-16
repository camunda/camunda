/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.pipeline.store;

import io.camunda.analytics.serving.spi.ServingWriteMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * {@link ServingWriteMetrics} as Micrometer meters, tagged by the backend's identity ({@code
 * rdbms|elasticsearch|opensearch}): {@code analytics.serving.rows.written} (per dataset), {@code
 * analytics.serving.fenced.rejected}, {@code analytics.serving.write.duration} (one sample per
 * flush/bulk call) and {@code analytics.serving.batch.size} (one sample per bulk request, document
 * backends only). One instance is shared by every partition task's writer — the fixed meters are
 * pre-resolved at construction and the per-dataset counter cache is concurrent, since the writers
 * record from the runtime's sink IO threads.
 */
public final class MicrometerServingWriteMetrics implements ServingWriteMetrics {

  private final MeterRegistry registry;
  private final String backend;
  private final Counter fencedRejected;
  private final Timer writeDuration;
  private final DistributionSummary batchSize;
  private final Map<String, Counter> rowsWritten = new ConcurrentHashMap<>();

  public MicrometerServingWriteMetrics(final MeterRegistry registry, final String backend) {
    this.registry = registry;
    this.backend = backend;
    fencedRejected =
        Counter.builder("analytics.serving.fenced.rejected")
            .description(
                "Serving writes rejected by the version fence — nonzero outside a"
                    + " failover/replay window means a zombie writer")
            .tag("backend", backend)
            .register(registry);
    writeDuration =
        Timer.builder("analytics.serving.write.duration")
            .description("Wall time of one serving flush/bulk call")
            .tag("backend", backend)
            .register(registry);
    batchSize =
        DistributionSummary.builder("analytics.serving.batch.size")
            .description("Documents per bulk request (document backends only)")
            .tag("backend", backend)
            .register(registry);
  }

  @Override
  public void rowWritten(final String datasetName) {
    rowsWritten
        .computeIfAbsent(
            datasetName == null ? "unknown" : datasetName,
            name ->
                Counter.builder("analytics.serving.rows.written")
                    .description("Rows (cells/snapshot rows/table rows) upserted successfully")
                    .tag("backend", backend)
                    .tag("dataset", name)
                    .register(registry))
        .increment();
  }

  @Override
  public void fencedRejected() {
    fencedRejected.increment();
  }

  @Override
  public void writeDuration(final long durationNanos) {
    writeDuration.record(durationNanos, TimeUnit.NANOSECONDS);
  }

  @Override
  public void batchSize(final int size) {
    batchSize.record(size);
  }
}
