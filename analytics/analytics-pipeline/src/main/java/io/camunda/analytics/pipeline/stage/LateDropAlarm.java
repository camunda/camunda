/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.pipeline.stage;

import io.camunda.analytics.dimension.DimensionKey;
import io.camunda.eventbridge.streaming.aggregate.SegmentMergingAggregation.LateDropListener;
import io.camunda.eventbridge.streaming.window.Windowed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The must-stay-zero alarm for one cube tier's closed-window drops: a delta arrived after its
 * window was finalized and evicted, so its facts are missing from the finalized value. The one
 * expected cause is a lagging upstream source on a multiplexed facts partition — the merge clock is
 * MAX-based over all sources, so a fast source can advance stream time past a slow source's still
 * in-flight windows (the min-of-sources clock is the structural fix; this alarm makes the loss
 * visible until then, and proves it fixed after).
 *
 * <p>Counts every drop ({@code analytics.aggregation.late.dropped}, tagged by partition, dataset,
 * and tier) but logs sparsely — the first drop and every 1024th — since a lagging source drops in
 * storms.
 */
final class LateDropAlarm implements LateDropListener<DimensionKey> {

  private static final Logger LOG = LoggerFactory.getLogger(LateDropAlarm.class);
  private static final long LOG_EVERY = 1024;

  private final Counter dropped;
  private final String dataset;
  private final long windowMs;
  private final int partition;
  private long drops;

  LateDropAlarm(
      final MeterRegistry registry,
      final int partition,
      final String dataset,
      final long windowMs) {
    this.partition = partition;
    this.dataset = dataset;
    this.windowMs = windowMs;
    dropped =
        registry == null
            ? null
            : Counter.builder("analytics.aggregation.late.dropped")
                .description("Deltas dropped because their window closed before they arrived")
                .tag("partition", Integer.toString(partition))
                .tag("dataset", dataset)
                .tag("tier", Long.toString(windowMs))
                .register(registry);
  }

  @Override
  public void onLateDrop(
      final Windowed<DimensionKey> cell, final long eventTimeHint, final long maxEventTime) {
    drops++;
    if (dropped != null) {
      dropped.increment();
    }
    if (drops == 1 || drops % LOG_EVERY == 0) {
      LOG.warn(
          "Dropped late delta #{} for cube '{}' tier {}ms on facts partition {}: window start {}"
              + " closed before the delta arrived (delta event time <= {}, stream clock {})."
              + " An upstream source lags the partition's clock past the grace — its facts are"
              + " missing from the finalized cell.",
          drops,
          dataset,
          windowMs,
          partition,
          cell.windowStart(),
          eventTimeHint,
          maxEventTime);
    }
  }
}
