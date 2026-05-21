/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import java.time.Duration;
import java.util.Set;

public class JobMetricsConfig {

  private static final String PREFIX = "camunda.monitoring.metrics.job-metrics";

  private static final Duration DEFAULT_EXPORT_INTERVAL = Duration.ofMinutes(5);
  private static final int DEFAULT_MAX_WORKER_NAME_LENGTH = 100;
  private static final int DEFAULT_MAX_JOB_TYPE_LENGTH = 100;
  private static final int DEFAULT_MAX_TENANT_ID_LENGTH = 30;
  private static final int DEFAULT_MAX_UNIQUE_KEYS = 9500;
  private static final boolean DEFAULT_ENABLED = true;

  private static final Set<String> LEGACY_EXPORT_INTERVAL_PROPERTIES =
      Set.of("zeebe.broker.experimental.engine.jobMetrics.exportInterval");

  private static final Set<String> LEGACY_MAX_WORKER_NAME_LENGTH_PROPERTIES =
      Set.of("zeebe.broker.experimental.engine.jobMetrics.maxWorkerNameLength");

  private static final Set<String> LEGACY_MAX_JOB_TYPE_LENGTH_PROPERTIES =
      Set.of("zeebe.broker.experimental.engine.jobMetrics.maxJobTypeLength");

  private static final Set<String> LEGACY_MAX_TENANT_ID_LENGTH_PROPERTIES =
      Set.of("zeebe.broker.experimental.engine.jobMetrics.maxTenantIdLength");

  private static final Set<String> LEGACY_MAX_UNIQUE_KEYS_PROPERTIES =
      Set.of("zeebe.broker.experimental.engine.jobMetrics.maxUniqueKeys");

  private static final Set<String> LEGACY_ENABLED_PROPERTIES =
      Set.of("zeebe.broker.experimental.engine.jobMetrics.enabled");

  /** The interval at which job metrics are exported. */
  private Duration exportInterval = DEFAULT_EXPORT_INTERVAL;

  /** The maximum length of the worker name used in job metrics labels. */
  private int maxWorkerNameLength = DEFAULT_MAX_WORKER_NAME_LENGTH;

  /** The maximum length of the job type used in job metrics labels. */
  private int maxJobTypeLength = DEFAULT_MAX_JOB_TYPE_LENGTH;

  /** The maximum length of the tenant ID used in job metrics labels. */
  private int maxTenantIdLength = DEFAULT_MAX_TENANT_ID_LENGTH;

  /** The maximum number of unique metric keys tracked for job metrics. */
  private int maxUniqueKeys = DEFAULT_MAX_UNIQUE_KEYS;

  /** Whether job metrics export is enabled. */
  private boolean enabled = DEFAULT_ENABLED;

  public Duration getExportInterval() {
    return UnifiedConfigurationHelper.validateLegacyConfigurationUnsafe(
        PREFIX + ".export-interval",
        exportInterval,
        Duration.class,
        UnifiedConfigurationHelper.BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_EXPORT_INTERVAL_PROPERTIES);
  }

  public void setExportInterval(final Duration exportInterval) {
    this.exportInterval = exportInterval;
  }

  public int getMaxWorkerNameLength() {
    return UnifiedConfigurationHelper.validateLegacyConfigurationUnsafe(
        PREFIX + ".max-worker-name-length",
        maxWorkerNameLength,
        Integer.class,
        UnifiedConfigurationHelper.BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_MAX_WORKER_NAME_LENGTH_PROPERTIES);
  }

  public void setMaxWorkerNameLength(final int maxWorkerNameLength) {
    this.maxWorkerNameLength = maxWorkerNameLength;
  }

  public int getMaxJobTypeLength() {
    return UnifiedConfigurationHelper.validateLegacyConfigurationUnsafe(
        PREFIX + ".max-job-type-length",
        maxJobTypeLength,
        Integer.class,
        UnifiedConfigurationHelper.BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_MAX_JOB_TYPE_LENGTH_PROPERTIES);
  }

  public void setMaxJobTypeLength(final int maxJobTypeLength) {
    this.maxJobTypeLength = maxJobTypeLength;
  }

  public int getMaxTenantIdLength() {
    return UnifiedConfigurationHelper.validateLegacyConfigurationUnsafe(
        PREFIX + ".max-tenant-id-length",
        maxTenantIdLength,
        Integer.class,
        UnifiedConfigurationHelper.BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_MAX_TENANT_ID_LENGTH_PROPERTIES);
  }

  public void setMaxTenantIdLength(final int maxTenantIdLength) {
    this.maxTenantIdLength = maxTenantIdLength;
  }

  public int getMaxUniqueKeys() {
    return UnifiedConfigurationHelper.validateLegacyConfigurationUnsafe(
        PREFIX + ".max-unique-keys",
        maxUniqueKeys,
        Integer.class,
        UnifiedConfigurationHelper.BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_MAX_UNIQUE_KEYS_PROPERTIES);
  }

  public void setMaxUniqueKeys(final int maxUniqueKeys) {
    this.maxUniqueKeys = maxUniqueKeys;
  }

  public boolean isEnabled() {
    return UnifiedConfigurationHelper.validateLegacyConfigurationUnsafe(
        PREFIX + ".enabled",
        enabled,
        Boolean.class,
        UnifiedConfigurationHelper.BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_ENABLED_PROPERTIES);
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }
}
