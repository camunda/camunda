/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.config;

import java.time.Duration;

public class GatewayRestConfiguration {

  private static final int DEFAULT_MAX_NAME_FIELD_LENGTH = 32 * 1024;

  private final JobMetricsConfiguration jobMetrics = new JobMetricsConfiguration();
  private int maxNameFieldLength = DEFAULT_MAX_NAME_FIELD_LENGTH;

  public JobMetricsConfiguration getJobMetrics() {
    return jobMetrics;
  }

  /**
   * Maximum allowed length for name-type fields (e.g. message names, variable names) validated
   * across REST and gRPC gateway requests.
   *
   * <p>Defaults to {@link #DEFAULT_MAX_NAME_FIELD_LENGTH}.
   */
  public int getMaxNameFieldLength() {
    return maxNameFieldLength;
  }

  public void setMaxNameFieldLength(final int maxNameFieldLength) {
    this.maxNameFieldLength = maxNameFieldLength;
  }

  /** Configuration for job metrics export settings. */
  public static class JobMetricsConfiguration {

    private static final Duration DEFAULT_EXPORT_INTERVAL = Duration.ofMinutes(5);
    private static final int DEFAULT_MAX_WORKER_NAME_LENGTH = 100;
    private static final int DEFAULT_MAX_JOB_TYPE_LENGTH = 100;
    private static final int DEFAULT_MAX_TENANT_ID_LENGTH = 30;
    private static final int DEFAULT_MAX_UNIQUE_KEYS = 9500;
    private static final boolean DEFAULT_ENABLED = true;

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

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(final boolean enabled) {
      this.enabled = enabled;
    }
  }
}
