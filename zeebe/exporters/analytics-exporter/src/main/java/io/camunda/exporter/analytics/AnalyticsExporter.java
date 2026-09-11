/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import io.camunda.exporter.analytics.sampling.HashSampler;
import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.exporter.api.context.Configuration;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.exporter.api.context.ScheduledTask;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.util.logging.ThrottledLogger;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Exporter that ships Camunda process analytics to the Camunda Analytics backend. */
public class AnalyticsExporter implements Exporter {

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

  public AnalyticsExporter() {
    this(new OtelSdkManager());
  }

  AnalyticsExporter(final OtelSdkManager otelSdkManager) {
    this.otelSdkManager = otelSdkManager;
  }

  @Override
  public void configure(final Context context) {
    config = instantiateConfig(context.getConfiguration()).validate();

    handlers =
        AnalyticsHandlerCatalog.build(otelSdkManager, config.getActiveCategories()).apply(context);
    meterRegistry = context.getMeterRegistry();

    analyticsContext =
        AnalyticsExporterContext.create(
            resolveLicenseKey(context),
            resolveClusterId(context),
            context.getPartitionId(),
            context.getPhysicalTenantId(),
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
   * Instantiates {@link AnalyticsExporterConfig} from the raw exporter arguments, re-mapping a
   * non-numeric {@code samplingRate} to the same range-naming message {@link
   * AnalyticsExporterConfig#validate()} gives for numeric out-of-range values. Jackson rejects a
   * non-numeric value while converting the raw args map, before {@code validate()} ever runs, so
   * without this it would otherwise surface as a raw {@link InvalidFormatException} instead (see
   * https://github.com/camunda/camunda/issues/62752).
   */
  private static AnalyticsExporterConfig instantiateConfig(final Configuration configuration) {
    try {
      return configuration.instantiate(AnalyticsExporterConfig.class);
    } catch (final IllegalArgumentException e) {
      if (e.getCause() instanceof final InvalidFormatException ife
          && ife.getPath().stream()
              .anyMatch(ref -> "samplingRate".equalsIgnoreCase(ref.getFieldName()))) {
        throw new IllegalArgumentException(
            "samplingRate must be between "
                + HashSampler.MIN_SAMPLE_RATE
                + " and "
                + HashSampler.MAX_SAMPLE_RATE
                + ", got: '"
                + ife.getValue()
                + "'",
            e);
      }
      throw e;
    }
  }

  private static String resolveLicenseKey(final Context context) {
    final String licenseKey = context.getLicenseKey();
    if (licenseKey == null || licenseKey.isBlank()) {
      throw new IllegalStateException(
          "Analytics exporter requires a license key. Set camunda.license.key in configuration.");
    }
    return licenseKey;
  }

  private static String resolveClusterId(final Context context) {
    return context.getClusterId();
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
