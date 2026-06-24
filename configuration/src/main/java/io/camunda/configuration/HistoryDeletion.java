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

  private static final Duration DEFAULT_DELAY_BETWEEN_RUNS = Duration.ofSeconds(1);
  private static final Duration DEFAULT_MAX_DELAY_BETWEEN_RUNS = Duration.ofSeconds(15);
  private static final int DEFAULT_QUEUE_BATCH_SIZE = 100;
  private static final int DEFAULT_DEPENDENT_ROW_LIMIT = 10000;

  private Duration delayBetweenRuns = DEFAULT_DELAY_BETWEEN_RUNS;
  private Duration maxDelayBetweenRuns = DEFAULT_MAX_DELAY_BETWEEN_RUNS;
  private int queueBatchSize = DEFAULT_QUEUE_BATCH_SIZE;
  private int dependentRowLimit = DEFAULT_DEPENDENT_ROW_LIMIT;

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
