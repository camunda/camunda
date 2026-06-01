/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics;

import static io.camunda.exporter.analytics.AnalyticsAttributes.CLUSTER_ID;
import static io.camunda.exporter.analytics.AnalyticsAttributes.EVENT_NAME;
import static io.camunda.exporter.analytics.AnalyticsAttributes.EVENT_SEQUENCE_NUMBER;
import static io.camunda.exporter.analytics.AnalyticsAttributes.LOG_POSITION;
import static io.camunda.exporter.analytics.AnalyticsAttributes.METRIC_EXPORT_WINDOW;
import static io.camunda.exporter.analytics.AnalyticsAttributes.PARTITION_ID;
import static io.camunda.exporter.analytics.AnalyticsAttributes.SERVICE_NAME;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.AggregationTemporalitySelector;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.resources.Resource;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Manages the OTel SDK lifecycle for one partition. Provides log events via {@link #logEvent} and
 * pre-aggregated metric counters via {@link #incrementMetric}. Metric collection is triggered
 * manually via {@link #flushMetrics} from the partition thread — no background reader thread.
 */
public class OtelSdkManager {

  private static final String INSTRUMENTATION_SCOPE = "io.camunda.analytics";
  private static final String SCHEMA_URL = "https://camunda.io/schemas/analytics/v1";
  private static final String OTLP_LOGS_PATH = "/v1/logs";
  private static final String OTLP_METRICS_PATH = "/v1/metrics";
  private static final String SERVICE_NAME_VALUE = "camunda-zeebe";

  private OpenTelemetrySdk sdk;
  private Logger otelLogger;
  private Meter otelMeter;
  private ManualMetricReader metricReader;
  private AnalyticsExporterMetadata metadata;
  private final Map<String, LongCounter> counters = new HashMap<>();
  private final MetricWindow metricWindow = new MetricWindow();

  OtelSdkManager initialize(
      final AnalyticsExporterConfig config,
      final AnalyticsExporterContext context,
      final AnalyticsExporterMetadata metadata) {
    this.metadata = metadata;
    counters.clear();
    metricWindow.reset();
    final var loggerProvider = createLoggerProvider(config, context);
    metricReader = createMetricReader(config, context);
    final var meterProvider = createMeterProvider(context, metricReader);

    sdk =
        OpenTelemetrySdk.builder()
            .setLoggerProvider(loggerProvider)
            .setMeterProvider(meterProvider)
            .build();
    otelLogger =
        sdk.getLogsBridge().loggerBuilder(INSTRUMENTATION_SCOPE).setSchemaUrl(SCHEMA_URL).build();
    otelMeter =
        sdk.getMeterProvider().meterBuilder(INSTRUMENTATION_SCOPE).setSchemaUrl(SCHEMA_URL).build();
    registerExportWindowGauge();
    return this;
  }

  public void logEvent(
      final String eventName, final long logPosition, final Consumer<LogRecordBuilder> builder) {
    final var record =
        otelLogger
            .logRecordBuilder()
            .setSeverity(Severity.INFO)
            .setSeverityText("INFO")
            .setAttribute(EVENT_NAME, eventName)
            .setAttribute(LOG_POSITION, logPosition)
            .setAttribute(EVENT_SEQUENCE_NUMBER, metadata.incrementAndGetEventSequenceNumber());
    builder.accept(record);
    record.emit();
  }

  /** Increments the named counter by 1 and updates the export window tracking fields. */
  public void incrementMetric(
      final String metricName,
      final long position,
      final long eventTimeMs,
      final Attributes dimensions) {
    counters
        .computeIfAbsent(metricName, name -> otelMeter.counterBuilder(name).build())
        .add(1, dimensions);
    metricWindow.record(position, eventTimeMs);
  }

  // Performance: collectAndExport() runs on the partition thread. Benchmarked at ~0.2ms for 50
  // metrics × 1999 dimensions, ~5ms for 1000 × 1999 (worst case). If collection time becomes
  // a concern, offload to a background thread: create a fresh MeterProvider per flush ("seal &
  // create"), swap atomically, and collect the old provider on a background ExecutorService.
  void flushMetrics() {
    if (metricReader != null) {
      metricReader.collectAndExport();
    }
  }

  void shutdown() {
    flushMetrics();
    if (sdk != null) {
      sdk.shutdown().join(10, TimeUnit.SECONDS);
    }
  }

  private void registerExportWindowGauge() {
    otelMeter
        .gaugeBuilder(METRIC_EXPORT_WINDOW)
        .ofLongs()
        .buildWithCallback(
            measurement -> {
              // Always report — acts as heartbeat even when no events occurred.
              // Note: metricSequenceNumber is persisted piggyback on the next export(Record)
              // call, not on flush itself (the broker only stores metadata when position
              // strictly increases). On restart after a flush-without-export, the sequence
              // may regress by one — the backend handles this via position-based dedup.
              final long seq = metadata.incrementAndGetMetricSequenceNumber();
              measurement.record(metricWindow.eventCount(), metricWindow.toGaugeAttributes(seq));
              metricWindow.reset();
            });
  }

  /**
   * Override in tests that need full pipeline control (e.g. sync processor, custom queue sizes).
   */
  protected SdkLoggerProvider createLoggerProvider(
      final AnalyticsExporterConfig config, final AnalyticsExporterContext context) {
    return SdkLoggerProvider.builder()
        .setResource(buildResource(context))
        .addLogRecordProcessor(
            BatchLogRecordProcessor.builder(createLogExporter(config, context))
                .setMaxQueueSize(config.getMaxQueueSize())
                .setMaxExportBatchSize(config.getMaxBatchSize())
                .setScheduleDelay(config.getPushInterval())
                .build())
        .build();
  }

  /** Override in tests to swap the OTLP transport for an in-memory exporter. */
  protected LogRecordExporter createLogExporter(
      final AnalyticsExporterConfig config, final AnalyticsExporterContext context) {
    final var builder =
        OtlpHttpLogRecordExporter.builder()
            .setEndpoint(config.getEndpoint() + OTLP_LOGS_PATH)
            .addHeader(AnalyticsExporterContext.HEADER_FINGERPRINT, context.fingerprint())
            .addHeader(AnalyticsExporterContext.HEADER_CLUSTER_ID, context.clusterId());

    if (config.isSigning()) {
      builder.setHeaders(context::computeSignatureHeaders);
    }

    return builder.build();
  }

  /** Override in tests to use an in-memory metric reader instead of OTLP. */
  protected ManualMetricReader createMetricReader(
      final AnalyticsExporterConfig config, final AnalyticsExporterContext context) {
    return new ManualMetricReader(createMetricExporter(config, context));
  }

  /** Override in tests to swap the OTLP metric transport for an in-memory exporter. */
  protected MetricExporter createMetricExporter(
      final AnalyticsExporterConfig config, final AnalyticsExporterContext context) {
    final var builder =
        OtlpHttpMetricExporter.builder()
            .setEndpoint(config.getEndpoint() + OTLP_METRICS_PATH)
            .setAggregationTemporalitySelector(AggregationTemporalitySelector.deltaPreferred())
            .addHeader(AnalyticsExporterContext.HEADER_FINGERPRINT, context.fingerprint())
            .addHeader(AnalyticsExporterContext.HEADER_CLUSTER_ID, context.clusterId());

    if (config.isSigning()) {
      builder.setHeaders(context::computeSignatureHeaders);
    }

    return builder.build();
  }

  /** Override in tests to use an in-memory metric reader instead of ManualMetricReader. */
  protected SdkMeterProvider createMeterProvider(
      final AnalyticsExporterContext context, final ManualMetricReader reader) {
    return SdkMeterProvider.builder()
        .setResource(buildResource(context))
        .registerMetricReader(reader)
        .build();
  }

  static Resource buildResource(final AnalyticsExporterContext context) {
    return Resource.getDefault()
        .merge(
            Resource.builder()
                .put(SERVICE_NAME, SERVICE_NAME_VALUE)
                .put(CLUSTER_ID, context.clusterId())
                .put(PARTITION_ID, context.partitionId())
                .build());
  }
}
