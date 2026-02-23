/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.domain;

import io.camunda.util.ObjectBuilder;
import java.time.OffsetDateTime;

public record JobMetricsBatchDbModel(
    String key,
    int partitionId,
    OffsetDateTime startTime,
    OffsetDateTime endTime,
    boolean incompleteBatch,
    String tenantId,
    int failedCount,
    OffsetDateTime lastFailedAt,
    int completedCount,
    OffsetDateTime lastCompletedAt,
    int createdCount,
    OffsetDateTime lastCreatedAt,
    String jobType,
    String worker) {

  public static class Builder implements ObjectBuilder<JobMetricsBatchDbModel> {

    private String key;
    private int partitionId;
    private OffsetDateTime startTime;
    private OffsetDateTime endTime;
    private boolean incompleteBatch;
    private String tenantId;
    private int failedCount;
    private OffsetDateTime lastFailedAt;
    private int completedCount;
    private OffsetDateTime lastCompletedAt;
    private int createdCount;
    private OffsetDateTime lastCreatedAt;
    private String jobType;
    private String worker;

    public Builder key(final String key) {
      this.key = key;
      return this;
    }

    public Builder partitionId(final int partitionId) {
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

    public Builder incompleteBatch(final boolean incompleteBatch) {
      this.incompleteBatch = incompleteBatch;
      return this;
    }

    public Builder tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    public Builder failedCount(final int failedCount) {
      this.failedCount = failedCount;
      return this;
    }

    public Builder lastFailedAt(final OffsetDateTime lastFailedAt) {
      this.lastFailedAt = lastFailedAt;
      return this;
    }

    public Builder completedCount(final int completedCount) {
      this.completedCount = completedCount;
      return this;
    }

    public Builder lastCompletedAt(final OffsetDateTime lastCompletedAt) {
      this.lastCompletedAt = lastCompletedAt;
      return this;
    }

    public Builder createdCount(final int createdCount) {
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
    public JobMetricsBatchDbModel build() {
      return new JobMetricsBatchDbModel(
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
