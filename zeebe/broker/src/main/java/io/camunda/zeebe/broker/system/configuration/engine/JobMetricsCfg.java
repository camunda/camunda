/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration.engine;

import io.camunda.zeebe.broker.system.configuration.ConfigurationEntry;
import io.camunda.zeebe.engine.EngineConfiguration;
import java.time.Duration;

public class JobMetricsCfg implements ConfigurationEntry {
  private Duration exportInterval = EngineConfiguration.DEFAULT_JOB_METRICS_EXPORT_INTERVAL;
  private int maxWorkerNameLength = EngineConfiguration.DEFAULT_MAX_WORKER_NAME_LENGTH;
  private int maxJobTypeLength = EngineConfiguration.DEFAULT_MAX_JOB_TYPE_LENGTH;
  private int maxTenantIdLength = EngineConfiguration.DEFAULT_MAX_TENANT_ID_LENGTH;
  private int maxUniqueKeys = EngineConfiguration.DEFAULT_MAX_UNIQUE_JOB_METRICS_KEYS;

  public Duration getExportInterval() {
    return exportInterval;
  }

  public void setExportInterval(final Duration exportInterval) {
    this.exportInterval = exportInterval;
  }

  @Override
  public String toString() {
    return "JobMetricsCfg{" + "exportInterval=" + exportInterval + '}';
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
}
