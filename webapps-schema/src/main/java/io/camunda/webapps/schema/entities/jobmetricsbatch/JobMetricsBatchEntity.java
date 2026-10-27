/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.jobmetricsbatch;

import io.camunda.webapps.schema.entities.ExporterEntity;
import io.camunda.webapps.schema.entities.SinceVersion;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.time.OffsetDateTime;
import java.util.Objects;

public final class JobMetricsBatchEntity
    implements ExporterEntity<JobMetricsBatchEntity>, TenantOwned {

  @SinceVersion(value = "8.9.0", requireDefault = false)
  private String id;

  @SinceVersion(value = "8.9.0", requireDefault = false)
  private int partitionId;

  @SinceVersion(value = "8.9.0", requireDefault = false)
  private OffsetDateTime startTime;

  @SinceVersion(value = "8.9.0", requireDefault = false)
  private OffsetDateTime endTime;

  @SinceVersion(value = "8.9.0", requireDefault = false)
  private boolean incompleteBatch;

  @SinceVersion(value = "8.9.0", requireDefault = false)
  private String tenantId;

  @SinceVersion(value = "8.9.0", requireDefault = false)
  private int failedCount;

  @SinceVersion(value = "8.9.0", requireDefault = false)
  private OffsetDateTime lastFailedAt;

  @SinceVersion(value = "8.9.0", requireDefault = false)
  private int completedCount;

  @SinceVersion(value = "8.9.0", requireDefault = false)
  private OffsetDateTime lastCompletedAt;

  @SinceVersion(value = "8.9.0", requireDefault = false)
  private int createdCount;

  @SinceVersion(value = "8.9.0", requireDefault = false)
  private OffsetDateTime lastCreatedAt;

  @SinceVersion(value = "8.9.0", requireDefault = false)
  private String jobType;

  @SinceVersion(value = "8.9.0", requireDefault = false)
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

  public int getPartitionId() {
    return partitionId;
  }

  public JobMetricsBatchEntity setPartitionId(final int partitionId) {
    this.partitionId = partitionId;
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

  public boolean isIncompleteBatch() {
    return incompleteBatch;
  }

  public JobMetricsBatchEntity setIncompleteBatch(final boolean incompleteBatch) {
    this.incompleteBatch = incompleteBatch;
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
        partitionId,
        startTime,
        endTime,
        incompleteBatch,
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
        && partitionId == that.partitionId
        && Objects.equals(startTime, that.startTime)
        && Objects.equals(endTime, that.endTime)
        && incompleteBatch == that.incompleteBatch
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
    return "JobMetricsBatchEntity[id=%s, partitionId=%d, startTime=%s, endTime=%s, incompleteBatch=%s, tenantId=%s, failedCount=%d, lastFailedAt=%s, completedCount=%d, lastCompletedAt=%s, createdCount=%d, lastCreatedAt=%s, jobType=%s, worker=%s]"
        .formatted(
            id,
            partitionId,
            startTime,
            endTime,
            incompleteBatch,
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
