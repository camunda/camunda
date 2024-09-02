/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration;

import java.time.Duration;
import java.util.Set;

public final class ProcessingCfg implements ConfigurationEntry {

  private static final int DEFAULT_PROCESSING_BATCH_LIMIT = 100;
  private Integer maxCommandsInBatch = DEFAULT_PROCESSING_BATCH_LIMIT;
  private boolean enableAsyncScheduledTasks = true;
  private Duration scheduledTaskCheckInterval = Duration.ofSeconds(1);
  private Set<Long> skipPositions;

  @Override
  public void init(final BrokerCfg globalConfig, final String brokerBase) {
    if (maxCommandsInBatch < 1) {
      throw new IllegalArgumentException(
          "maxCommandsInBatch must be >= 1 but was %s".formatted(maxCommandsInBatch));
    }
    if (!scheduledTaskCheckInterval.isPositive()) {
      throw new IllegalArgumentException(
          "scheduledTaskCheckInterval must be positive but was %s"
              .formatted(scheduledTaskCheckInterval));
    }
  }

  public int getMaxCommandsInBatch() {
    return maxCommandsInBatch;
  }

  public void setMaxCommandsInBatch(final int maxCommandsInBatch) {
    this.maxCommandsInBatch = maxCommandsInBatch;
  }

  public boolean isEnableAsyncScheduledTasks() {
    return enableAsyncScheduledTasks;
  }

  public void setEnableAsyncScheduledTasks(final boolean enableAsyncScheduledTasks) {
    this.enableAsyncScheduledTasks = enableAsyncScheduledTasks;
  }

  public Set<Long> skipPositions() {
    return skipPositions != null ? skipPositions : Set.of();
  }

  public void setSkipPositions(final Set<Long> skipPositions) {
    this.skipPositions = skipPositions;
  }

  @Override
  public String toString() {
    return "ProcessingCfg{"
        + "maxCommandsInBatch="
        + maxCommandsInBatch
        + ", enableAsyncScheduledTasks="
        + enableAsyncScheduledTasks
        + ", scheduledTaskCheckInterval="
        + scheduledTaskCheckInterval
        + '}';
  }

  public Duration getScheduledTaskCheckInterval() {
    return scheduledTaskCheckInterval;
  }

  public void setScheduledTaskCheckInterval(final Duration scheduledTaskCheckInterval) {
    this.scheduledTaskCheckInterval = scheduledTaskCheckInterval;
  }
}
