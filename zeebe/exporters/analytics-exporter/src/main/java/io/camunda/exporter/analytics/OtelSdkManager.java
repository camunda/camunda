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

import io.opentelemetry.api.common.AttributeKey;
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
  private static final AttributeKey<String> SERVICE_NAME = AttributeKey.stringKey("service.name");

  private OpenTelemetrySdk sdk;
  private Logger logger;

  OtelSdkManager initialize(
      final AnalyticsExporterConfig config, final String clusterId, final int partitionId) {
    if (!config.isEnabled() || sdk != null) {
      return this;
    }

    final var resource = buildResource(clusterId, partitionId);
    final var loggerProvider = createLoggerProvider(config, resource);

    sdk = OpenTelemetrySdk.builder().setLoggerProvider(loggerProvider).build();
    logger =
        sdk.getLogsBridge().loggerBuilder(INSTRUMENTATION_SCOPE).setSchemaUrl(SCHEMA_URL).build();
    return this;
  }

  void logEvent(
      final String eventName, final long logPosition, final Consumer<LogRecordBuilder> builder) {
    if (logger == null) {
      return;
    }
    final var record =
        logger
            .logRecordBuilder()
            .setSeverity(Severity.INFO)
            .setSeverityText("INFO")
            .setAttribute(EVENT_NAME, eventName)
            .setAttribute(LOG_POSITION, logPosition);
    builder.accept(record);
    record.emit();
  }

  void shutdown() {
    if (sdk != null) {
      sdk.shutdown().join(10, TimeUnit.SECONDS);
    }
    logger = null;
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
        .setEndpoint(config.getEndpoint() + "/v1/logs")
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
