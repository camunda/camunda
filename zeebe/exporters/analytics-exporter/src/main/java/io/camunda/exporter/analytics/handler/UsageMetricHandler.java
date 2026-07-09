/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics.handler;

import static io.camunda.exporter.analytics.AnalyticsAttributes.Event.USAGE_METRIC_EXPORTED;
import static io.camunda.exporter.analytics.AnalyticsAttributes.UsageMetric.COUNT;
import static io.camunda.exporter.analytics.AnalyticsAttributes.UsageMetric.EVENT_TYPE;
import static io.camunda.exporter.analytics.AnalyticsAttributes.UsageMetric.INTERVAL_END;
import static io.camunda.exporter.analytics.AnalyticsAttributes.UsageMetric.INTERVAL_START;

import io.camunda.exporter.analytics.AnalyticsHandler;
import io.camunda.exporter.analytics.OtelSdkManager;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Emits a {@code usage_metric_exported} OTel event for each usage metric export (RPI, EDI, TU).
 * Skips internal {@link UsageMetricRecordValue.EventType#NONE NONE} reset events.
 */
public final class UsageMetricHandler implements AnalyticsHandler<UsageMetricRecordValue> {

  private final OtelSdkManager otelSdkManager;

  public UsageMetricHandler(final OtelSdkManager otelSdkManager) {
    this.otelSdkManager = Objects.requireNonNull(otelSdkManager);
  }

  @Override
  public void handle(final Record<UsageMetricRecordValue> record) {
    final var value = record.getValue();

    if (value.getEventType() == UsageMetricRecordValue.EventType.NONE) {
      return;
    }

    final long count =
        switch (value.getEventType()) {
          case TU -> value.getSetValues().values().stream().mapToLong(set -> set.size()).sum();
          default -> value.getCounterValues().values().stream().mapToLong(Long::longValue).sum();
        };

    otelSdkManager.logEvent(
        USAGE_METRIC_EXPORTED,
        record.getPosition(),
        log ->
            log.setAttribute(EVENT_TYPE, value.getEventType().name())
                .setAttribute(COUNT, count)
                .setAttribute(INTERVAL_START, value.getStartTime())
                .setAttribute(INTERVAL_END, value.getEndTime())
                .setTimestamp(record.getTimestamp(), TimeUnit.MILLISECONDS));
  }
}
