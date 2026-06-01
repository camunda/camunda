/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics;

import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.metrics.InstrumentType;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.export.AggregationTemporalitySelector;
import io.opentelemetry.sdk.metrics.export.CollectionRegistration;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.metrics.export.MetricReader;
import java.util.Collection;

/**
 * A {@link MetricReader} that exposes manual collection and export. Unlike {@link
 * io.opentelemetry.sdk.metrics.export.PeriodicMetricReader}, collection timing is controlled by the
 * caller — enabling single-threaded collection on the partition thread with zero cross-thread
 * races.
 */
public final class ManualMetricReader implements MetricReader {

  private final MetricExporter exporter;
  private CollectionRegistration registration = CollectionRegistration.noop();

  ManualMetricReader(final MetricExporter exporter) {
    this.exporter = exporter;
  }

  @Override
  public void register(final CollectionRegistration registration) {
    this.registration = registration;
  }

  /** Collects all metrics and exports them. Returns the export result. */
  CompletableResultCode collectAndExport() {
    final Collection<MetricData> metrics = registration.collectAllMetrics();
    if (metrics.isEmpty()) {
      return CompletableResultCode.ofSuccess();
    }
    return exporter.export(metrics);
  }

  @Override
  public AggregationTemporality getAggregationTemporality(final InstrumentType instrumentType) {
    return AggregationTemporalitySelector.deltaPreferred()
        .getAggregationTemporality(instrumentType);
  }

  /** No-op — collection is driven by explicit {@link #collectAndExport()} calls only. */
  @Override
  public CompletableResultCode forceFlush() {
    return CompletableResultCode.ofSuccess();
  }

  @Override
  public CompletableResultCode shutdown() {
    return exporter.shutdown();
  }
}
