/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics;

import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.exporter.api.context.ScheduledTask;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.util.logging.ThrottledLogger;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Exporter that ships Camunda process analytics to the Camunda Analytics backend. */
public class AnalyticsExporter implements Exporter {

  private static final String LICENSE_KEY_ENV_VAR = "CAMUNDA_LICENSE_KEY";
  private static final String CLUSTER_ID_ENV_VAR = "ZEEBE_BROKER_CLUSTER_CLUSTERID";
  private static final Logger LOG =
      LoggerFactory.getLogger(AnalyticsExporter.class.getPackageName());
  private static final ThrottledLogger SAMPLED_WARN_LOG =
      new ThrottledLogger(LOG, Duration.ofMinutes(1));
  private final OtelSdkManager otelSdkManager;

  private AnalyticsExporterConfig config;
  private Controller controller;
  private HandlerRegistry handlers;
  private AnalyticsExporterContext analyticsContext;
  private AnalyticsExporterMetadata metadata;
  private ScheduledTask metricFlushTask;
  private ScheduledTask heartbeatTask;

  public AnalyticsExporter() {
    this(new OtelSdkManager());
  }

  AnalyticsExporter(final OtelSdkManager otelSdkManager) {
    this.otelSdkManager = otelSdkManager;
  }

  @Override
  public void configure(final Context context) {
    config = context.getConfiguration().instantiate(AnalyticsExporterConfig.class).validate();

    analyticsContext =
        AnalyticsExporterContext.create(
            resolveLicenseKey(context), resolveClusterId(context), context.getPartitionId());

    handlers = AnalyticsHandlerCatalog.build(otelSdkManager).apply(context);

    LOG.info(
        "Analytics exporter configured: endpoint={}, clusterId={}, partitionId={}",
        config.getEndpoint(),
        analyticsContext.clusterId(),
        analyticsContext.partitionId());
  }

  @Override
  public void open(final Controller controller) {
    this.controller = controller;
    metadata =
        controller
            .readMetadata()
            .map(AnalyticsExporterMetadata::deserialize)
            .orElse(new AnalyticsExporterMetadata());
    otelSdkManager.initialize(config, analyticsContext, metadata);
    scheduleMetricFlush();
    scheduleHeartbeat();
    LOG.info("Analytics exporter opened");
  }

  @Override
  public void close() {
    if (metricFlushTask != null) {
      metricFlushTask.cancel();
      metricFlushTask = null;
    }
    if (heartbeatTask != null) {
      heartbeatTask.cancel();
      heartbeatTask = null;
    }
    otelSdkManager.close();
    controller.updateLastExportedRecordPosition(
        controller.getLastExportedRecordPosition(), metadata.serialize());
    LOG.info("Analytics exporter closed");
  }

  @Override
  public void export(final Record<?> record) {
    try {
      handlers.handle(record);
    } catch (final Exception e) {
      SAMPLED_WARN_LOG.warn("Failed to handle record at position {}", record.getPosition(), e);
    }
    if (metadata.isDirty()) {
      controller.updateLastExportedRecordPosition(record.getPosition(), metadata.serialize());
    } else {
      // No need to serialize the metadata if it didn't change.
      controller.updateLastExportedRecordPosition(record.getPosition());
    }
  }

  private void scheduleMetricFlush() {
    metricFlushTask =
        controller.scheduleCancellableTask(
            config.getPushInterval(), this::flushMetricsAndReschedule);
  }

  private void flushMetricsAndReschedule() {
    try {
      otelSdkManager.flushMetrics();
    } catch (final Exception e) {
      SAMPLED_WARN_LOG.warn("Failed to flush metrics", e);
    } finally {
      scheduleMetricFlush();
    }
  }

  private void scheduleHeartbeat() {
    heartbeatTask =
        controller.scheduleCancellableTask(
            config.getHeartbeatInterval(), this::emitHeartbeatAndReschedule);
  }

  private void emitHeartbeatAndReschedule() {
    try {
      otelSdkManager.emitHeartbeat();
    } catch (final Exception e) {
      SAMPLED_WARN_LOG.warn("Failed to emit heartbeat", e);
    } finally {
      scheduleHeartbeat();
    }
  }

  /**
   * Resolves and validates the license key from the broker context, falling back to the {@code
   * CAMUNDA_LICENSE_KEY} environment variable for brokers that predate the {@code getLicenseKey()}
   * API (8.8 and earlier).
   */
  private static String resolveLicenseKey(final Context context) {
    String licenseKey;
    try {
      licenseKey = context.getLicenseKey();
    } catch (final NoSuchMethodError e) {
      LOG.warn("Context.getLicenseKey() not available (pre-8.10 broker); falling back to env var");
      licenseKey = System.getenv(LICENSE_KEY_ENV_VAR);
    }
    if (licenseKey == null || licenseKey.isBlank()) {
      throw new IllegalStateException(
          "Analytics exporter requires a license key. Set camunda.license.key in configuration or the "
              + LICENSE_KEY_ENV_VAR
              + " environment variable.");
    }
    return licenseKey;
  }

  /**
   * Resolves the cluster ID from the broker context, falling back to the {@code
   * ZEEBE_BROKER_CLUSTER_CLUSTERID} environment variable for brokers that predate the {@code
   * getClusterId()} API (8.8 and earlier).
   */
  private static String resolveClusterId(final Context context) {
    try {
      return context.getClusterId();
    } catch (final NoSuchMethodError e) {
      final var fromEnv = System.getenv(CLUSTER_ID_ENV_VAR);
      return fromEnv != null ? fromEnv : "";
    }
  }
}
