/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import java.util.Set;

public class Monitoring {
  private static final String PREFIX = "camunda.cluster.monitoring";

  private static final Set<String> LEGACY_EXECUTION_METRICS_EXPORTER_PROPERTIES =
      Set.of("zeebe.broker.executionMetricsExporterEnabled");

  /** Enable execution metrics. */
  private boolean executionMetricsEnabled;

  public boolean isExecutionMetricsEnabled() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".execution-metrics-enabled",
        executionMetricsEnabled,
        Boolean.class,
        UnifiedConfigurationHelper.BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_EXECUTION_METRICS_EXPORTER_PROPERTIES);
  }

  public void setExecutionMetricsEnabled(final boolean executionMetricsEnabled) {
    this.executionMetricsEnabled = executionMetricsEnabled;
  }
}
