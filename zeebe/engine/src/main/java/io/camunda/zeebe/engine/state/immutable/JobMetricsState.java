/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.immutable;

import io.camunda.zeebe.engine.state.jobmetrics.JobMetricsMonitoringValue;
import io.camunda.zeebe.engine.state.jobmetrics.JobMetricsValue;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * Provides read-only access to the job metrics state. Job metrics track counts and timestamps for
 * various job states (CANCELED, COMPLETED, CREATED, etc.) at both job type level and per-worker
 * level.
 */
public interface JobMetricsState {

  /**
   * Gets the job metrics for the given job type and tenant.
   *
   * @param jobType the type of the job
   * @param tenantId the tenant identifier
   * @return an Optional containing the job metrics value if it exists, or empty otherwise
   */
  Optional<JobMetricsValue> getJobMetrics(String jobType, String tenantId);

  /**
   * Checks if job metrics exist for the given job type and tenant.
   *
   * @param jobType the type of the job
   * @param tenantId the tenant identifier
   * @return true if metrics exist, false otherwise
   */
  boolean exists(String jobType, String tenantId);

  /**
   * Gets the monitoring data for job metrics.
   *
   * @return an Optional containing the monitoring value if it exists, or empty otherwise
   */
  Optional<JobMetricsMonitoringValue> getMonitoringData();

  /**
   * Iterates over all job metrics entries.
   *
   * @param visitor a consumer that receives the jobType_tenantId key and the value for each entry
   */
  void forEachJobMetrics(BiConsumer<String, JobMetricsValue> visitor);
}
