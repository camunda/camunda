/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.zeebe.exporter.common.historydeletion.HistoryDeletionConfiguration;
import java.time.Duration;

/** Configuration for history deletion. */
public class HistoryDeletion {

  private Duration delayBetweenRuns = Duration.ofSeconds(1);
  private Duration maxDelayBetweenRuns = Duration.ofMinutes(5);
  private int queueBatchSize = 100;
  private int dependentRowLimit = 10000;

  public Duration getDelayBetweenRuns() {
    return delayBetweenRuns;
  }

  public void setDelayBetweenRuns(final Duration delayBetweenRuns) {
    this.delayBetweenRuns = delayBetweenRuns;
  }

  public Duration getMaxDelayBetweenRuns() {
    return maxDelayBetweenRuns;
  }

  public void setMaxDelayBetweenRuns(final Duration maxDelayBetweenRuns) {
    this.maxDelayBetweenRuns = maxDelayBetweenRuns;
  }

  public int getQueueBatchSize() {
    return queueBatchSize;
  }

  public void setQueueBatchSize(final int queueBatchSize) {
    this.queueBatchSize = queueBatchSize;
  }

  public int getDependentRowLimit() {
    return dependentRowLimit;
  }

  public void setDependentRowLimit(final int dependentRowLimit) {
    this.dependentRowLimit = dependentRowLimit;
  }

  /** Converts this configuration to an {@link HistoryDeletionConfiguration} for the exporter. */
  public HistoryDeletionConfiguration toConfiguration() {
    final HistoryDeletionConfiguration historyDeletionConfiguration =
        new HistoryDeletionConfiguration();
    historyDeletionConfiguration.setDelayBetweenRuns(delayBetweenRuns);
    historyDeletionConfiguration.setMaxDelayBetweenRuns(maxDelayBetweenRuns);
    historyDeletionConfiguration.setQueueBatchSize(queueBatchSize);
    historyDeletionConfiguration.setDependentRowLimit(dependentRowLimit);
    return historyDeletionConfiguration;
  }
}
