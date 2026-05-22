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
import static io.camunda.exporter.analytics.AnalyticsAttributes.PARTITION_ID;
import static io.camunda.exporter.analytics.AnalyticsAttributes.SEQUENCE_NUMBER;
import static io.camunda.exporter.analytics.AnalyticsAttributes.SERVICE_NAME;

import io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.resources.Resource;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Manages the OTel SDK lifecycle for one partition. */
class OtelSdkManager {

  private static final String INSTRUMENTATION_SCOPE = "io.camunda.analytics";
  private static final String SCHEMA_URL = "https://camunda.io/schemas/analytics/v1";
  private static final String OTLP_LOGS_PATH = "/v1/logs";

  private OpenTelemetrySdk sdk;
  private Logger otelLogger;
  private AnalyticsExporterMetadata metadata;

  OtelSdkManager initialize(
      final AnalyticsExporterConfig config,
      final String clusterId,
      final int partitionId,
      final AnalyticsExporterMetadata metadata) {
    this.metadata = metadata;
    final var resource = buildResource(clusterId, partitionId);
    final var loggerProvider = createLoggerProvider(config, resource);

    sdk = OpenTelemetrySdk.builder().setLoggerProvider(loggerProvider).build();
    otelLogger =
        sdk.getLogsBridge().loggerBuilder(INSTRUMENTATION_SCOPE).setSchemaUrl(SCHEMA_URL).build();
    return this;
  }

  void logEvent(
      final String eventName, final long logPosition, final Consumer<LogRecordBuilder> builder) {
    final var record =
        otelLogger
            .logRecordBuilder()
            .setSeverity(Severity.INFO)
            .setSeverityText("INFO")
            .setAttribute(EVENT_NAME, eventName)
            .setAttribute(LOG_POSITION, logPosition)
            .setAttribute(SEQUENCE_NUMBER, metadata.incrementAndGetRawEventSequenceNumber());
    builder.accept(record);
    record.emit();
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
      final AnalyticsExporterConfig config, final Resource resource) {
    return SdkLoggerProvider.builder()
        .setResource(resource)
        .addLogRecordProcessor(
            BatchLogRecordProcessor.builder(createLogExporter(config))
                .setMaxQueueSize(config.getMaxQueueSize())
                .setMaxExportBatchSize(config.getMaxBatchSize())
                .setScheduleDelay(config.getPushInterval())
                .build())
        .build();
  }

  /** Override in tests to swap the OTLP transport for an in-memory exporter. */
  protected LogRecordExporter createLogExporter(final AnalyticsExporterConfig config) {
    return OtlpHttpLogRecordExporter.builder()
        .setEndpoint(config.getEndpoint() + OTLP_LOGS_PATH)
        .build();
  }

  private static Resource buildResource(final String clusterId, final int partitionId) {
    return Resource.getDefault()
        .merge(
            Resource.builder()
                .put(SERVICE_NAME, "camunda-zeebe")
                .put(CLUSTER_ID, clusterId)
                .put(PARTITION_ID, partitionId)
                .build());
  }
}
