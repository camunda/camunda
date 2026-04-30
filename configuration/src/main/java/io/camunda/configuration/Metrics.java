/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import java.util.Set;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

public class Metrics {

  private static final String PREFIX = "camunda.monitoring.metrics";

  private static final Set<String> LEGACY_ENABLE_ACTOR_METRICS_PROPERTIES =
      Set.of("zeebe.broker.experimental.features.enableActorMetrics");

  private static final Set<String> LEGACY_ENABLE_EXPORTER_EXECUTION_METRICS_PPROPERTIES =
      Set.of("zeebe.broker.executionMetricsExporterEnabled");

  /** Controls whether to collect metrics about actor usage such as actor job execution latencies */
  private boolean actor = false;

  /** Enable exporter execution metrics */
  private boolean enableExporterExecutionMetrics;

  /** Configure job metrics export settings */
  @NestedConfigurationProperty private JobMetricsConfig jobMetrics = new JobMetricsConfig();

  public boolean isActor() {
    return UnifiedConfigurationHelper.validateLegacyConfigurationUnsafe(
        PREFIX + ".actor",
        actor,
        Boolean.class,
        UnifiedConfigurationHelper.BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_ENABLE_ACTOR_METRICS_PROPERTIES);
  }

  public void setActor(final boolean actor) {
    this.actor = actor;
  }

  public boolean isEnableExporterExecutionMetrics() {
    return UnifiedConfigurationHelper.validateLegacyConfigurationUnsafe(
        PREFIX + ".enable-exporter-execution-metrics",
        enableExporterExecutionMetrics,
        Boolean.class,
        UnifiedConfigurationHelper.BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_ENABLE_EXPORTER_EXECUTION_METRICS_PPROPERTIES);
  }

  public void setEnableExporterExecutionMetrics(final boolean enableExporterExecutionMetrics) {
    this.enableExporterExecutionMetrics = enableExporterExecutionMetrics;
  }

  public JobMetricsConfig getJobMetrics() {
    return jobMetrics;
  }

  public void setJobMetrics(final JobMetricsConfig jobMetrics) {
    this.jobMetrics = jobMetrics;
  }
}
