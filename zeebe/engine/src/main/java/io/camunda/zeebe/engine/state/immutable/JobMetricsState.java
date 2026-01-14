/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.immutable;

import static io.camunda.zeebe.engine.state.jobmetrics.DbJobMetricsState.META_BATCH_ENDING_TIME;
import static io.camunda.zeebe.engine.state.jobmetrics.DbJobMetricsState.META_BATCH_STARTING_TIME;

import io.camunda.zeebe.engine.state.jobmetrics.MetricsConsumer;
import java.util.Set;

/** Immutable interface for reading job metrics state. */
public interface JobMetricsState {

  /**
   * Iterates over all metrics in the METRICS column family.
   *
   * @param consumer receives (jobTypeIndex, tenantIdIndex, workerNameIndex, StatusMetrics[8])
   */
  void forEach(MetricsConsumer consumer);

  /**
   * Returns all encoded strings sorted by their integer value (ascending).
   *
   * @return Set where index = encoded integer, value = original string. Example: ["jobType1",
   *     "tenant1", "worker1"] means "jobType1"=0, "tenant1"=1, "worker1"=2
   */
  Set<String> getEncodedStrings();

  /**
   * Gets metadata value from the META column family.
   *
   * @param key the metadata key
   * @return the metadata value, or 0 if not found
   */
  long getMetadata(String key);

  /**
   * Gets the start time of the current metrics batch.
   *
   * @return the batch start time in epoch milliseconds, or 0 if not set
   */
  default long getBatchStartTime() {
    return getMetadata(META_BATCH_STARTING_TIME);
  }

  /**
   * Gets the end time of the current metrics batch.
   *
   * @return the batch end time in epoch milliseconds, or 0 if not set
   */
  default long getBatchEndTime() {
    return getMetadata(META_BATCH_ENDING_TIME);
  }

  /**
   * Checks if the metrics collection has been truncated due to size limits.
   *
   * @return true if the batch record total size exceeded the maximum threshold
   */
  boolean isIncompleteBatch();
}
