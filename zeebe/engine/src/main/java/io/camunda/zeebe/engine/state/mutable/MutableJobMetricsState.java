/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.mutable;

import io.camunda.zeebe.engine.state.immutable.JobMetricsState;
import io.camunda.zeebe.engine.state.jobmetrics.JobMetricState;

/**
 * Provides mutable access to the job metrics state. Extends JobMetricsState with write operations.
 */
public interface MutableJobMetricsState extends JobMetricsState {

  default void incrementAllJobMetricsCounter(
      final String jobType,
      final String tenantId,
      final String workerName,
      final JobMetricState state,
      final long timestamp) {
    if (workerName == null || workerName.isEmpty()) {
      incrementJobTypeCounter(jobType, tenantId, state, timestamp);
    } else {
      incrementWorkerCounter(jobType, tenantId, workerName, state, timestamp);
    }
  }

  /**
   * Increments the counter for the given job state at the job type level.
   *
   * @param jobType the type of the job
   * @param tenantId the tenant identifier
   * @param state the job metric state to increment
   * @param timestamp the timestamp when this update occurred
   */
  void incrementJobTypeCounter(
      String jobType, String tenantId, JobMetricState state, long timestamp);

  /**
   * Increments the counter for the given job state at the worker level.
   *
   * @param jobType the type of the job
   * @param tenantId the tenant identifier
   * @param workerName the name of the worker
   * @param state the job metric state to increment
   * @param timestamp the timestamp when this update occurred
   */
  void incrementWorkerCounter(
      String jobType, String tenantId, String workerName, JobMetricState state, long timestamp);

  /** Resets all job metrics and monitoring data. */
  void resetAllMetrics();
}
