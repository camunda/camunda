/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration;

import static io.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.JOB_REGISTRY_DISPATCHER;

import io.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import java.util.Objects;

public class JobRegistryDispatcherConfiguration {

  private boolean enabled;
  private int intervalSeconds;
  private int batchSize;
  private int threadCount;

  public JobRegistryDispatcherConfiguration(
      final boolean enabled,
      final int intervalSeconds,
      final int batchSize,
      final int threadCount) {
    this.enabled = enabled;
    this.intervalSeconds = intervalSeconds;
    this.batchSize = batchSize;
    this.threadCount = threadCount;
  }

  protected JobRegistryDispatcherConfiguration() {}

  public void validate() {
    if (intervalSeconds <= 0) {
      throw new OptimizeConfigurationException(
          JOB_REGISTRY_DISPATCHER + ".intervalSeconds must be greater than 0");
    }
    if (batchSize <= 0) {
      throw new OptimizeConfigurationException(
          JOB_REGISTRY_DISPATCHER + ".batchSize must be greater than 0");
    }
    if (threadCount <= 0) {
      throw new OptimizeConfigurationException(
          JOB_REGISTRY_DISPATCHER + ".threadCount must be greater than 0");
    }
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public int getIntervalSeconds() {
    return intervalSeconds;
  }

  public void setIntervalSeconds(final int intervalSeconds) {
    this.intervalSeconds = intervalSeconds;
  }

  public int getBatchSize() {
    return batchSize;
  }

  public void setBatchSize(final int batchSize) {
    this.batchSize = batchSize;
  }

  public int getThreadCount() {
    return threadCount;
  }

  public void setThreadCount(final int threadCount) {
    this.threadCount = threadCount;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof JobRegistryDispatcherConfiguration;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final JobRegistryDispatcherConfiguration that = (JobRegistryDispatcherConfiguration) o;
    return enabled == that.enabled
        && intervalSeconds == that.intervalSeconds
        && batchSize == that.batchSize
        && threadCount == that.threadCount;
  }

  @Override
  public int hashCode() {
    return Objects.hash(enabled, intervalSeconds, batchSize, threadCount);
  }

  @Override
  public String toString() {
    return "JobRegistryDispatcherConfiguration(enabled="
        + isEnabled()
        + ", intervalSeconds="
        + getIntervalSeconds()
        + ", batchSize="
        + getBatchSize()
        + ", threadCount="
        + getThreadCount()
        + ")";
  }
}
