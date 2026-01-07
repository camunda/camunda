/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.mutable;

import io.camunda.zeebe.engine.state.immutable.JobMetricsState;
import io.camunda.zeebe.engine.state.jobmetrics.JobState;

/** Mutable interface for managing job metrics state. */
public interface MutableJobMetricsState extends JobMetricsState {

  /**
   * Increments the metric for the given combination by 1.
   *
   * <ul>
   *   <li>Encodes strings to integers using STRING_ENCODING column family (auto-creates if new)
   *   <li>Retrieves or creates metrics array in METRICS column family
   *   <li>Increments count by 1 for the specified status
   *   <li>Updates lastUpdatedAt to current timestamp
   *   <li>Updates META column family counters appropriately
   * </ul>
   *
   * <p>IMPORTANT: If this creates a NEW key in METRICS column family, increments __job_metrics_nb
   *
   * @param jobType the job type string
   * @param workerName the worker name string
   * @param tenantId the tenant ID string
   * @param status the job status to increment
   */
  void incrementMetric(String jobType, String tenantId, String workerName, JobState status);

  /**
   * Clears all data:
   *
   * <ul>
   *   <li>Delete ALL keys/values in METRICS column family
   *   <li>Delete ALL keys/values in STRING_ENCODING column family
   *   <li>Reset all values in META column family to 0 (keep the keys)
   * </ul>
   */
  void flush();
}
