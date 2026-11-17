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
  private static final int DEFAULT_MAX_RECOVERABLE_RETRIES = 1000;
  private static final int DEFAULT_MAX_PENDING_SIDE_EFFECTS = 1000;
  private Integer maxCommandsInBatch = DEFAULT_PROCESSING_BATCH_LIMIT;
  private int maxRecoverableRetries = DEFAULT_MAX_RECOVERABLE_RETRIES;
  private int maxPendingSideEffects = DEFAULT_MAX_PENDING_SIDE_EFFECTS;
  private Duration scheduledTaskCheckInterval = Duration.ofSeconds(1);
  private Set<Long> skipPositions;

  @Override
  public void init(final BrokerCfg globalConfig, final String brokerBase) {
    if (maxCommandsInBatch < 1) {
      throw new IllegalArgumentException(
          "maxCommandsInBatch must be >= 1 but was %s".formatted(maxCommandsInBatch));
    }
    if (maxRecoverableRetries < 1) {
      throw new IllegalArgumentException(
          "maxRecoverableRetries must be >= 1 but was %s".formatted(maxRecoverableRetries));
    }
    if (maxPendingSideEffects < 1) {
      throw new IllegalArgumentException(
          "maxPendingSideEffects must be >= 1 but was %s".formatted(maxPendingSideEffects));
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

  /**
   * How many processing results may wait for their records to be committed before processing stops
   * reading new records. Bounds the memory held by queued responses and post-commit tasks when
   * commits are slow. Raise it only to trade memory for throughput on a partition whose commit
   * latency is high.
   */
  public int getMaxPendingSideEffects() {
    return maxPendingSideEffects;
  }

  public void setMaxPendingSideEffects(final int maxPendingSideEffects) {
    this.maxPendingSideEffects = maxPendingSideEffects;
  }

  public int getMaxRecoverableRetries() {
    return maxRecoverableRetries;
  }

  public void setMaxRecoverableRetries(final int maxRecoverableRetries) {
    this.maxRecoverableRetries = maxRecoverableRetries;
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
        + ", maxRecoverableRetries="
        + maxRecoverableRetries
        + ", maxPendingSideEffects="
        + maxPendingSideEffects
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
