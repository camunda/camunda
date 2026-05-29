/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics;

import static io.camunda.exporter.analytics.AnalyticsAttributes.LOG_EVENT_TIME_MAX;
import static io.camunda.exporter.analytics.AnalyticsAttributes.LOG_EVENT_TIME_MIN;
import static io.camunda.exporter.analytics.AnalyticsAttributes.LOG_POSITION_END;
import static io.camunda.exporter.analytics.AnalyticsAttributes.LOG_POSITION_START;
import static io.camunda.exporter.analytics.AnalyticsAttributes.METRIC_SEQUENCE_NUMBER;

import io.opentelemetry.api.common.Attributes;

/**
 * Tracks the export window state between metric flushes. Accumulates event count, position range,
 * and event time range. Reset after each gauge collection.
 *
 * <p><b>Thread safety:</b> In Phase 2 (PeriodicMetricReader), this class is accessed from both the
 * partition thread ({@link #record}) and the metric reader thread ({@link #toGaugeAttributes},
 * {@link #reset}). This is a known race with ±few-event inaccuracy at push boundaries. Phase 3
 * (ManualMetricReader + scheduleCancellableTask) eliminates this by moving all access to the
 * partition thread.
 */
final class MetricWindow {

  private long eventCount;
  private long positionStart = Long.MAX_VALUE;
  private long positionEnd = Long.MIN_VALUE;
  private long eventTimeMin = Long.MAX_VALUE;
  private long eventTimeMax = Long.MIN_VALUE;

  /** Records a metric event, updating the window bounds. */
  void record(final long position, final long eventTimeMs) {
    eventCount++;
    positionStart = Math.min(positionStart, position);
    positionEnd = Math.max(positionEnd, position);
    eventTimeMin = Math.min(eventTimeMin, eventTimeMs);
    eventTimeMax = Math.max(eventTimeMax, eventTimeMs);
  }

  /**
   * Builds OTel attributes for the companion gauge. When no events have been recorded, only the
   * sequence number is included — position and time sentinels are omitted to avoid leaking
   * Long.MAX_VALUE/MIN_VALUE to consumers.
   */
  Attributes toGaugeAttributes(final long metricSequenceNumber) {
    if (!hasEvents()) {
      return Attributes.of(METRIC_SEQUENCE_NUMBER, metricSequenceNumber);
    }
    return Attributes.of(
        METRIC_SEQUENCE_NUMBER, metricSequenceNumber,
        LOG_POSITION_START, positionStart,
        LOG_POSITION_END, positionEnd,
        LOG_EVENT_TIME_MIN, eventTimeMin,
        LOG_EVENT_TIME_MAX, eventTimeMax);
  }

  /** Resets all fields for the next window. */
  void reset() {
    eventCount = 0;
    positionStart = Long.MAX_VALUE;
    positionEnd = Long.MIN_VALUE;
    eventTimeMin = Long.MAX_VALUE;
    eventTimeMax = Long.MIN_VALUE;
  }

  boolean hasEvents() {
    return eventCount > 0;
  }

  long eventCount() {
    return eventCount;
  }
}
