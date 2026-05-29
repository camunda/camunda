/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics;

import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;

/** Test factory for {@link OtelSdkManager} with in-memory OTel transport. */
public final class TestOtelSdkManager {

  private TestOtelSdkManager() {}

  /** Creates an initialized OtelSdkManager that captures events in the given exporter. */
  public static OtelSdkManager inMemory(final InMemoryLogRecordExporter memoryExporter) {
    return inMemoryWithMetrics(memoryExporter, InMemoryMetricReader.create());
  }

  /** Creates an initialized OtelSdkManager with in-memory log and metric capture. */
  public static OtelSdkManager inMemoryWithMetrics(
      final InMemoryLogRecordExporter logExporter, final InMemoryMetricReader metricReader) {
    final var manager =
        new OtelSdkManager() {
          @Override
          protected SdkLoggerProvider createLoggerProvider(
              final AnalyticsExporterConfig cfg, final AnalyticsExporterContext context) {
            return SdkLoggerProvider.builder()
                .setResource(OtelSdkManager.buildResource(context))
                .addLogRecordProcessor(SimpleLogRecordProcessor.create(logExporter))
                .build();
          }

          @Override
          protected SdkMeterProvider createMeterProvider(
              final AnalyticsExporterConfig cfg, final AnalyticsExporterContext context) {
            return SdkMeterProvider.builder()
                .setResource(OtelSdkManager.buildResource(context))
                .registerMetricReader(metricReader)
                .build();
          }
        };
    manager.initialize(
        new AnalyticsExporterConfig(),
        AnalyticsExporterContext.create("test-license", "test-cluster", 1),
        new AnalyticsExporterMetadata());
    return manager;
  }
}
