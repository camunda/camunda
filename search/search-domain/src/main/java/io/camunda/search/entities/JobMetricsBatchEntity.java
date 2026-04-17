/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.entities;

import io.camunda.util.ObjectBuilder;
import java.time.OffsetDateTime;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Represents a single row in the {@code JOB_METRICS_BATCH} table.
 *
 * <p>Each row captures aggregated job-state counters (created / completed / failed) for a specific
 * combination of job type, worker and tenant, within a time window defined by {@code startTime} and
 * {@code endTime}.
 *
 * @param key unique identifier of the batch row
 * @param partitionId Zeebe partition that produced the record
 * @param startTime start of the batch time window (inclusive)
 * @param endTime end of the batch time window (inclusive)
 * @param incompleteBatch {@code true} if the record-size limit was exceeded during export
 * @param tenantId tenant that owns this record
 * @param failedCount number of job-failed events in the window
 * @param lastFailedAt timestamp of the latest failed event, or {@code null} if none
 * @param completedCount number of job-completed events in the window
 * @param lastCompletedAt timestamp of the latest completed event, or {@code null} if none
 * @param createdCount number of job-created events in the window
 * @param lastCreatedAt timestamp of the latest created event, or {@code null} if none
 * @param jobType job type name
 * @param worker worker name, or {@code null} for anonymous workers
 */
public record JobMetricsBatchEntity(
    String key,
    Integer partitionId,
    OffsetDateTime startTime,
    OffsetDateTime endTime,
    Boolean incompleteBatch,
    String tenantId,
    Integer failedCount,
    @Nullable OffsetDateTime lastFailedAt,
    Integer completedCount,
    @Nullable OffsetDateTime lastCompletedAt,
    Integer createdCount,
    @Nullable OffsetDateTime lastCreatedAt,
    String jobType,
    @Nullable String worker)
    implements TenantOwnedEntity {

  public JobMetricsBatchEntity {
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(partitionId, "partitionId");
    Objects.requireNonNull(startTime, "startTime");
    Objects.requireNonNull(endTime, "endTime");
    Objects.requireNonNull(incompleteBatch, "incompleteBatch");
    Objects.requireNonNull(tenantId, "tenantId");
    Objects.requireNonNull(failedCount, "failedCount");
    Objects.requireNonNull(completedCount, "completedCount");
    Objects.requireNonNull(createdCount, "createdCount");
    Objects.requireNonNull(jobType, "jobType");
  }

  public static class Builder implements ObjectBuilder<JobMetricsBatchEntity> {

    private String key;
    private Integer partitionId;
    private OffsetDateTime startTime;
    private OffsetDateTime endTime;
    private Boolean incompleteBatch;
    private String tenantId;
    private Integer failedCount;
    private OffsetDateTime lastFailedAt;
    private Integer completedCount;
    private OffsetDateTime lastCompletedAt;
    private Integer createdCount;
    private OffsetDateTime lastCreatedAt;
    private String jobType;
    private String worker;

    public Builder key(final String key) {
      this.key = key;
      return this;
    }

    public Builder partitionId(final Integer partitionId) {
      this.partitionId = partitionId;
      return this;
    }

    public Builder startTime(final OffsetDateTime startTime) {
      this.startTime = startTime;
      return this;
    }

    public Builder endTime(final OffsetDateTime endTime) {
      this.endTime = endTime;
      return this;
    }

    public Builder incompleteBatch(final Boolean incompleteBatch) {
      this.incompleteBatch = incompleteBatch;
      return this;
    }

    public Builder tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    public Builder failedCount(final Integer failedCount) {
      this.failedCount = failedCount;
      return this;
    }

    public Builder lastFailedAt(final OffsetDateTime lastFailedAt) {
      this.lastFailedAt = lastFailedAt;
      return this;
    }

    public Builder completedCount(final Integer completedCount) {
      this.completedCount = completedCount;
      return this;
    }

    public Builder lastCompletedAt(final OffsetDateTime lastCompletedAt) {
      this.lastCompletedAt = lastCompletedAt;
      return this;
    }

    public Builder createdCount(final Integer createdCount) {
      this.createdCount = createdCount;
      return this;
    }

    public Builder lastCreatedAt(final OffsetDateTime lastCreatedAt) {
      this.lastCreatedAt = lastCreatedAt;
      return this;
    }

    public Builder jobType(final String jobType) {
      this.jobType = jobType;
      return this;
    }

    public Builder worker(final String worker) {
      this.worker = worker;
      return this;
    }

    @Override
    public JobMetricsBatchEntity build() {
      return new JobMetricsBatchEntity(
          key,
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
}
