/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static io.camunda.zeebe.engine.EngineConfiguration.DEFAULT_USAGE_METRICS_EXPORT_INTERVAL;

import io.camunda.configuration.UnifiedConfigurationHelper.BackwardsCompatibilityMode;
import java.time.Duration;
import java.util.Set;

public class UsageMetricsConfig {

  private static final String PREFIX = "camunda.monitoring.metrics.usage-metrics";

  private static final Set<String> LEGACY_EXPORT_INTERVAL_PROPERTIES =
      Set.of("zeebe.broker.experimental.engine.usageMetrics.exportInterval");

  /**
   * The interval at which usage metrics are exported.
   *
   * <p>Defaults to {@link
   * io.camunda.zeebe.engine.EngineConfiguration#DEFAULT_USAGE_METRICS_EXPORT_INTERVAL}.
   */
  private Duration exportInterval = DEFAULT_USAGE_METRICS_EXPORT_INTERVAL;

  public Duration getExportInterval() {
    return UnifiedConfigurationHelper.validateLegacyConfigurationUnsafe(
        PREFIX + ".export-interval",
        exportInterval,
        Duration.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_EXPORT_INTERVAL_PROPERTIES);
  }

  public void setExportInterval(final Duration exportInterval) {
    this.exportInterval = exportInterval;
  }
}
