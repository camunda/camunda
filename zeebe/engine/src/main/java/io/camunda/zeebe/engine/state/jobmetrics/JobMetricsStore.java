/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.jobmetrics;

import io.camunda.zeebe.protocol.record.value.JobMetricsExportState;

/**
 * Storage of the job metrics of the current batch, without any domain decision: a counter per job
 * status and the time of its last update, for each combination of job type, tenant and worker, and
 * a few named numbers about the batch.
 */
public interface JobMetricsStore {

  /** The number of distinct combinations of job type, tenant and worker that have metrics. */
  int countKeys();

  /** Whether the combination has metrics. */
  boolean containsKey(String jobType, String tenantId, String workerName);

  /**
   * Increments the counter of the status of the combination by one and sets its last update time,
   * creating the combination's metrics if needed.
   */
  void increment(
      String jobType,
      String tenantId,
      String workerName,
      JobMetricsExportState status,
      long timestamp);

  /**
   * Visits the metrics of all combinations, in no particular order. The metrics array is indexed by
   * {@link JobMetricsExportState#getIndex()} and must not be modified.
   */
  void forEach(MetricsVisitor visitor);

  /** Returns the named number, or 0 if it was never set. */
  long getMetadata(String key);

  void setMetadata(String key, long value);

  /** Deletes the metrics of all combinations. The named numbers are left to the caller. */
  void deleteMetrics();

  @FunctionalInterface
  interface MetricsVisitor {

    void accept(String jobType, String tenantId, String workerName, StatusMetrics[] metrics);
  }
}
