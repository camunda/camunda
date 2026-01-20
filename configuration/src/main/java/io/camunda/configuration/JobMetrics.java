/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import java.time.Duration;

/** Configuration properties for job metrics collection and export. */
public class JobMetrics {

  private static final Duration DEFAULT_EXPORT_INTERVAL = Duration.ofMinutes(1);
  private static final boolean DEFAULT_ENABLED = true;
  private static final int DEFAULT_MAX_WORKER_NAME_LENGTH = 64;
  private static final int DEFAULT_MAX_JOB_TYPE_LENGTH = 64;
  private static final int DEFAULT_MAX_TENANT_ID_LENGTH = 64;
  private static final int DEFAULT_MAX_UNIQUE_KEYS = 100;

  /** Whether job metrics collection is enabled. */
  private boolean enabled = DEFAULT_ENABLED;

  /** The interval at which job metrics are exported. */
  private Duration exportInterval = DEFAULT_EXPORT_INTERVAL;

  /** Maximum length of worker names to track. Longer names will be truncated. */
  private int maxWorkerNameLength = DEFAULT_MAX_WORKER_NAME_LENGTH;

  /** Maximum length of job types to track. Longer types will be truncated. */
  private int maxJobTypeLength = DEFAULT_MAX_JOB_TYPE_LENGTH;

  /** Maximum length of tenant IDs to track. Longer IDs will be truncated. */
  private int maxTenantIdLength = DEFAULT_MAX_TENANT_ID_LENGTH;

  /** Maximum number of unique keys (jobType_tenantId combinations) to track. */
  private int maxUniqueKeys = DEFAULT_MAX_UNIQUE_KEYS;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public Duration getExportInterval() {
    return exportInterval;
  }

  public void setExportInterval(final Duration exportInterval) {
    this.exportInterval = exportInterval;
  }

  public int getMaxWorkerNameLength() {
    return maxWorkerNameLength;
  }

  public void setMaxWorkerNameLength(final int maxWorkerNameLength) {
    this.maxWorkerNameLength = maxWorkerNameLength;
  }

  public int getMaxJobTypeLength() {
    return maxJobTypeLength;
  }

  public void setMaxJobTypeLength(final int maxJobTypeLength) {
    this.maxJobTypeLength = maxJobTypeLength;
  }

  public int getMaxTenantIdLength() {
    return maxTenantIdLength;
  }

  public void setMaxTenantIdLength(final int maxTenantIdLength) {
    this.maxTenantIdLength = maxTenantIdLength;
  }

  public int getMaxUniqueKeys() {
    return maxUniqueKeys;
  }

  public void setMaxUniqueKeys(final int maxUniqueKeys) {
    this.maxUniqueKeys = maxUniqueKeys;
  }

  /** Returns the export interval in minutes. */
  public long getResolutionMinutes() {
    return exportInterval.toMinutes();
  }
}
