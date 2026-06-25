/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;

/** Test factory for {@link OtelSdkManager} with in-memory OTel transport. */
public final class TestOtelSdkManager {

  private TestOtelSdkManager() {}

  /** Creates an initialized OtelSdkManager that captures events in the given exporter. */
  public static OtelSdkManager inMemory(final InMemoryLogRecordExporter memoryExporter) {
    return inMemory(memoryExporter, new AnalyticsExporterConfig());
  }

  public static OtelSdkManager inMemory(
      final InMemoryLogRecordExporter memoryExporter, final AnalyticsExporterConfig config) {
    return inMemoryWithMetrics(memoryExporter, InMemoryMetricReader.create(), config);
  }

  public static OtelSdkManager inMemoryWithMetrics(
      final InMemoryLogRecordExporter logExporter, final InMemoryMetricReader metricReader) {
    return inMemoryWithMetrics(logExporter, metricReader, new AnalyticsExporterConfig());
  }

  public static OtelSdkManager inMemoryWithMetrics(
      final InMemoryLogRecordExporter logExporter,
      final InMemoryMetricReader metricReader,
      final AnalyticsExporterConfig config) {
    return inMemoryWithMetrics(logExporter, metricReader, config, new SimpleMeterRegistry());
  }

  public static OtelSdkManager inMemoryWithMetrics(
      final InMemoryLogRecordExporter logExporter,
      final InMemoryMetricReader metricReader,
      final AnalyticsExporterConfig config,
      final MeterRegistry meterRegistry) {
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
              final AnalyticsExporterContext context, final ManualMetricReader reader) {
            // Bypass ManualMetricReader — use InMemoryMetricReader for synchronous test collection
            return SdkMeterProvider.builder()
                .setResource(OtelSdkManager.buildResource(context))
                .registerMetricReader(metricReader)
                .build();
          }
        };
    manager.initialize(
        config,
        AnalyticsExporterContext.create("test-license", "test-cluster", 1),
        new AnalyticsExporterMetadata(),
        meterRegistry);
    return manager;
  }

  /**
   * Creates an initialized manager wired to both an in-memory OTel exporter and a Micrometer
   * registry. Uses the production {@link OtelSdkManager#createLoggerProvider} so that {@code
   * selfMetricsSdkMeterProvider} is wired in (needed to test OTel SDK self-metrics). Only {@link
   * OtelSdkManager#createLogExporter} is overridden to avoid real HTTP connections.
   */
  public static OtelSdkManager inMemoryWithRegistry(
      final InMemoryLogRecordExporter logExporter, final MeterRegistry meterRegistry) {
    final var manager =
        new OtelSdkManager() {
          @Override
          protected LogRecordExporter createLogExporter(
              final AnalyticsExporterConfig cfg, final AnalyticsExporterContext context) {
            return logExporter;
          }
        };
    manager.initialize(
        new AnalyticsExporterConfig(),
        AnalyticsExporterContext.create("test-license", "test-cluster", 1),
        new AnalyticsExporterMetadata(),
        meterRegistry);
    return manager;
  }
}
