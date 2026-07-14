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
 * window was finalized and evicted, so its facts are missing from the finalized value. The merge
 * clock is the min over the live sources' own max event times, so a fast source can no longer close
 * a slow source's still-in-flight windows — a drop now means a source lagged <em>itself</em> past
 * the grace, re-entered behind the clock after an idle timeout, or replayed a straggler. This alarm
 * keeps every such loss visible (and proves the min-of-sources clock holds).
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
      final Windowed<DimensionKey> cell, final long eventTimeHint, final long clock) {
    drops++;
    if (dropped != null) {
      dropped.increment();
    }
    if (drops == 1 || drops % LOG_EVERY == 0) {
      LOG.warn(
          "Dropped late delta #{} for cube '{}' tier {}ms on facts partition {}: window start {}"
              + " closed before the delta arrived (delta event time <= {}, stream clock {})."
              + " The source lags itself past the grace, re-entered behind the clock after an"
              + " idle timeout, or replayed a straggler — its facts are missing from the"
              + " finalized cell.",
          drops,
          dataset,
          windowMs,
          partition,
          cell.windowStart(),
          eventTimeHint,
          clock);
    }
  }
}
