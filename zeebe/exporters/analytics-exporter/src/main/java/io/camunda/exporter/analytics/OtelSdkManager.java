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
import static io.camunda.exporter.analytics.AnalyticsAttributes.LOG_POSITION;
import static io.camunda.exporter.analytics.AnalyticsAttributes.LOG_SEQUENCE_NUMBER;
import static io.camunda.exporter.analytics.AnalyticsAttributes.PARTITION_ID;
import static io.camunda.exporter.analytics.AnalyticsAttributes.SERVICE_NAME;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.logs.Severity;
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
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Manages the OTel SDK lifecycle for one partition. */
public class OtelSdkManager {

  private static final String INSTRUMENTATION_SCOPE = "io.camunda.analytics";
  private static final String SCHEMA_URL = "https://camunda.io/schemas/analytics/v1";
  private static final String OTLP_LOGS_PATH = "/v1/logs";
  private static final String OTLP_METRICS_PATH = "/v1/metrics";
  private static final String SERVICE_NAME_VALUE = "camunda-zeebe";

  private OpenTelemetrySdk sdk;
  private Logger otelLogger;
  private Meter otelMeter;
  private AnalyticsExporterMetadata metadata;

  OtelSdkManager initialize(
      final AnalyticsExporterConfig config,
      final AnalyticsExporterContext context,
      final AnalyticsExporterMetadata metadata) {
    this.metadata = metadata;
    final var loggerProvider = createLoggerProvider(config, context);
    final var meterProvider = createMeterProvider(config, context);

    sdk =
        OpenTelemetrySdk.builder()
            .setLoggerProvider(loggerProvider)
            .setMeterProvider(meterProvider)
            .build();
    otelLogger =
        sdk.getLogsBridge().loggerBuilder(INSTRUMENTATION_SCOPE).setSchemaUrl(SCHEMA_URL).build();
    otelMeter = sdk.getMeterProvider().get(INSTRUMENTATION_SCOPE);
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
            .setAttribute(LOG_SEQUENCE_NUMBER, metadata.incrementAndGetRawEventSequenceNumber());
    builder.accept(record);
    record.emit();
  }

  /** Increments the named counter by 1 with the given dimension attributes. */
  public void incrementMetric(final String metricName, final Attributes dimensions) {
    otelMeter.counterBuilder(metricName).build().add(1, dimensions);
  }

  void shutdown() {
    if (sdk != null) {
      sdk.shutdown().join(10, TimeUnit.SECONDS);
    }
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
            // Static auth headers (constant per exporter lifetime)
            .addHeader(AnalyticsExporterContext.HEADER_FINGERPRINT, context.fingerprint())
            .addHeader(AnalyticsExporterContext.HEADER_CLUSTER_ID, context.clusterId());

    if (config.isSigning()) {
      builder.setHeaders(context::computeSignatureHeaders);
    }

    return builder.build();
  }

  /** Override in tests to swap the OTLP metric transport for an in-memory reader. */
  protected SdkMeterProvider createMeterProvider(
      final AnalyticsExporterConfig config, final AnalyticsExporterContext context) {
    return SdkMeterProvider.builder()
        .setResource(buildResource(context))
        .registerMetricReader(
            PeriodicMetricReader.builder(createMetricExporter(config, context))
                .setInterval(config.getPushInterval())
                .build())
        .build();
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
