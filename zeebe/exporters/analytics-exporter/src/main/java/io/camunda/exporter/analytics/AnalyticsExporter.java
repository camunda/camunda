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
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.util.logging.ThrottledLogger;
import io.micrometer.core.instrument.MeterRegistry;
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
  private MeterRegistry meterRegistry;
  private ScheduledTask metricFlushTask;
  private ScheduledTask heartbeatTask;
  private int partitionId;

  public AnalyticsExporter() {
    this(new OtelSdkManager());
  }

  AnalyticsExporter(final OtelSdkManager otelSdkManager) {
    this.otelSdkManager = otelSdkManager;
  }

  @Override
  public void configure(final Context context) {
    config = context.getConfiguration().instantiate(AnalyticsExporterConfig.class).validate();

    handlers = AnalyticsHandlerCatalog.build(otelSdkManager).apply(context);
    meterRegistry = context.getMeterRegistry();
    partitionId = context.getPartitionId();

    analyticsContext =
        AnalyticsExporterContext.create(
            resolveLicenseKey(),
            resolveClusterId(),
            context.getPartitionId(),
            resolveDigest(handlers, config));

    LOG.info(
        "Analytics exporter configured: endpoint={}, clusterId={}, partitionId={}, exporterDigest={}",
        config.getEndpoint(),
        analyticsContext.clusterId(),
        analyticsContext.partitionId(),
        analyticsContext.exporterDigest());
  }

  @Override
  public void open(final Controller controller) {
    this.controller = controller;
    metadata =
        controller
            .readMetadata()
            .map(AnalyticsExporterMetadata::deserialize)
            .orElse(new AnalyticsExporterMetadata());
    otelSdkManager.initialize(config, analyticsContext, metadata, meterRegistry);
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
    if (controller != null && metadata != null) {
      controller.updateLastExportedRecordPosition(
          controller.getLastExportedRecordPosition(), metadata.serialize());
    }
    LOG.info("Analytics exporter closed");
  }

  @Override
  public void export(final Record<?> record) {
    try {
      if (Protocol.decodePartitionId(record.getKey()) == partitionId) {
        handlers.handle(record);
      }
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

  private static String resolveLicenseKey() {
    final String licenseKey = System.getenv(LICENSE_KEY_ENV_VAR);
    if (licenseKey == null || licenseKey.isBlank()) {
      throw new IllegalStateException(
          "Analytics exporter requires a license key. Set the "
              + LICENSE_KEY_ENV_VAR
              + " environment variable.");
    }
    return licenseKey;
  }

  private static String resolveClusterId() {
    final var clusterId = System.getenv(CLUSTER_ID_ENV_VAR);
    return clusterId != null ? clusterId : "";
  }

  private String resolveDigest(
      final HandlerRegistry handlers, final AnalyticsExporterConfig config) {
    try {
      return AnalyticsExporterDigest.compute(handlers, config);
    } catch (final Exception e) {
      LOG.warn("Failed to compute exporter digest; resource attribute will be empty", e);
      return "";
    }
  }
}
