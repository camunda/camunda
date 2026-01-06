/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.metrics;

import io.camunda.webapps.schema.entities.ExporterEntity;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.time.OffsetDateTime;
import java.util.Objects;

public final class JobMetricsBatchEntity
    implements ExporterEntity<JobMetricsBatchEntity>, TenantOwned {

  private String id;
  private OffsetDateTime startTime;
  private OffsetDateTime endTime;
  private String tenantId;
  private int failedCount;
  private OffsetDateTime lastFailedAt;
  private int completedCount;
  private OffsetDateTime lastCompletedAt;
  private int createdCount;
  private OffsetDateTime lastCreatedAt;
  private String jobType;
  private String worker;

  @Override
  public String getId() {
    return id;
  }

  @Override
  public JobMetricsBatchEntity setId(final String id) {
    this.id = id;
    return this;
  }

  public OffsetDateTime getStartTime() {
    return startTime;
  }

  public JobMetricsBatchEntity setStartTime(final OffsetDateTime startTime) {
    this.startTime = startTime;
    return this;
  }

  public OffsetDateTime getEndTime() {
    return endTime;
  }

  public JobMetricsBatchEntity setEndTime(final OffsetDateTime endTime) {
    this.endTime = endTime;
    return this;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  public JobMetricsBatchEntity setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public int getFailedCount() {
    return failedCount;
  }

  public JobMetricsBatchEntity setFailedCount(final int failedCount) {
    this.failedCount = failedCount;
    return this;
  }

  public OffsetDateTime getLastFailedAt() {
    return lastFailedAt;
  }

  public JobMetricsBatchEntity setLastFailedAt(final OffsetDateTime lastFailedAt) {
    this.lastFailedAt = lastFailedAt;
    return this;
  }

  public int getCompletedCount() {
    return completedCount;
  }

  public JobMetricsBatchEntity setCompletedCount(final int completedCount) {
    this.completedCount = completedCount;
    return this;
  }

  public OffsetDateTime getLastCompletedAt() {
    return lastCompletedAt;
  }

  public JobMetricsBatchEntity setLastCompletedAt(final OffsetDateTime lastCompletedAt) {
    this.lastCompletedAt = lastCompletedAt;
    return this;
  }

  public int getCreatedCount() {
    return createdCount;
  }

  public JobMetricsBatchEntity setCreatedCount(final int createdCount) {
    this.createdCount = createdCount;
    return this;
  }

  public OffsetDateTime getLastCreatedAt() {
    return lastCreatedAt;
  }

  public JobMetricsBatchEntity setLastCreatedAt(final OffsetDateTime lastCreatedAt) {
    this.lastCreatedAt = lastCreatedAt;
    return this;
  }

  public String getJobType() {
    return jobType;
  }

  public JobMetricsBatchEntity setJobType(final String jobType) {
    this.jobType = jobType;
    return this;
  }

  public String getWorker() {
    return worker;
  }

  public JobMetricsBatchEntity setWorker(final String worker) {
    this.worker = worker;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        id,
        startTime,
        endTime,
        tenantId,
        failedCount,
        lastFailedAt,
        completedCount,
        lastCompletedAt,
        createdCount,
        lastCreatedAt,
        jobType,
        worker);
  }

  @Override
  public boolean equals(final Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj == null || obj.getClass() != getClass()) {
      return false;
    }
    final var that = (JobMetricsBatchEntity) obj;
    return Objects.equals(id, that.id)
        && Objects.equals(startTime, that.startTime)
        && Objects.equals(endTime, that.endTime)
        && Objects.equals(tenantId, that.tenantId)
        && failedCount == that.failedCount
        && Objects.equals(lastFailedAt, that.lastFailedAt)
        && completedCount == that.completedCount
        && Objects.equals(lastCompletedAt, that.lastCompletedAt)
        && createdCount == that.createdCount
        && Objects.equals(lastCreatedAt, that.lastCreatedAt)
        && Objects.equals(jobType, that.jobType)
        && Objects.equals(worker, that.worker);
  }

  @Override
  public String toString() {
    return "JobMetricsEntity[id=%s, startTime=%s, endTime=%s, tenantId=%s, failedCount=%d, lastFailedAt=%s, completedCount=%d, lastCompletedAt=%s, createdCount=%d, lastCreatedAt=%s, jobType=%s, worker=%s]"
        .formatted(
            id,
            startTime,
            endTime,
            tenantId,
            failedCount,
            lastFailedAt,
            completedCount,
            lastCompletedAt,
            createdCount,
            lastCreatedAt,
            jobType,
            worker);
  }
}
