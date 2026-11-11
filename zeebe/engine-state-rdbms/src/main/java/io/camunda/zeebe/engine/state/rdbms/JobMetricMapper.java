/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.rdbms;

import io.camunda.zeebe.engine.state.jobmetrics.StatusMetrics;
import io.camunda.zeebe.protocol.record.value.JobMetricsExportState;
import java.util.List;
import org.apache.ibatis.annotations.Param;

interface JobMetricMapper {

  /** The committed job metrics of the current batch. */
  List<JobMetricRow> selectJobMetrics(@Param("partitionId") int partitionId);

  /** The metrics of a batch are identified by the job type, tenant and worker. */
  record JobMetricKey(String jobType, String tenantId, String workerName) {}

  /**
   * The count of the jobs per status and the time of the status' last update, of a job type, tenant
   * and worker. A status without jobs has a count of 0 and no update time (-1).
   */
  record JobMetricRow(
      String jobType,
      String tenantId,
      String workerName,
      int createdCount,
      long createdLastUpdatedAt,
      int completedCount,
      long completedLastUpdatedAt,
      int failedCount,
      long failedLastUpdatedAt) {

    static JobMetricRow empty(final JobMetricKey key) {
      return new JobMetricRow(key.jobType(), key.tenantId(), key.workerName(), 0, -1, 0, -1, 0, -1);
    }

    JobMetricKey key() {
      return new JobMetricKey(jobType, tenantId, workerName);
    }

    /** The row with the status' count incremented; the switch breaks when a status is added. */
    JobMetricRow incremented(final JobMetricsExportState status, final long timestamp) {
      return switch (status) {
        case CREATED ->
            new JobMetricRow(
                jobType,
                tenantId,
                workerName,
                createdCount + 1,
                timestamp,
                completedCount,
                completedLastUpdatedAt,
                failedCount,
                failedLastUpdatedAt);
        case COMPLETED ->
            new JobMetricRow(
                jobType,
                tenantId,
                workerName,
                createdCount,
                createdLastUpdatedAt,
                completedCount + 1,
                timestamp,
                failedCount,
                failedLastUpdatedAt);
        case FAILED ->
            new JobMetricRow(
                jobType,
                tenantId,
                workerName,
                createdCount,
                createdLastUpdatedAt,
                completedCount,
                completedLastUpdatedAt,
                failedCount + 1,
                timestamp);
      };
    }

    /** The statuses' metrics, indexed by {@link JobMetricsExportState#getIndex()}. */
    StatusMetrics[] statusMetrics() {
      final var metrics = new StatusMetrics[JobMetricsExportState.count()];
      metrics[JobMetricsExportState.CREATED.getIndex()] =
          new StatusMetrics(createdCount, createdLastUpdatedAt);
      metrics[JobMetricsExportState.COMPLETED.getIndex()] =
          new StatusMetrics(completedCount, completedLastUpdatedAt);
      metrics[JobMetricsExportState.FAILED.getIndex()] =
          new StatusMetrics(failedCount, failedLastUpdatedAt);
      return metrics;
    }
  }
}
