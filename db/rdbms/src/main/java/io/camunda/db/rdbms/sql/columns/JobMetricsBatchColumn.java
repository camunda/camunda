/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql.columns;

import io.camunda.search.entities.JobMetricsBatchEntity;

/**
 * Maps every column of the {@code JOB_METRICS_BATCH} table to the corresponding field in {@link
 * JobMetricsBatchEntity}.
 *
 * <p>The string passed to each constant is the exact Java property name on the record (used by
 * {@link SearchColumn#getPropertyValue} via reflection to retrieve the value from an entity
 * instance).
 */
public enum JobMetricsBatchColumn implements SearchColumn<JobMetricsBatchEntity> {
  KEY("key"),
  PARTITION_ID("partitionId"),
  START_TIME("startTime"),
  END_TIME("endTime"),
  INCOMPLETE_BATCH("incompleteBatch"),
  TENANT_ID("tenantId"),
  FAILED_COUNT("failedCount"),
  LAST_FAILED_AT("lastFailedAt"),
  COMPLETED_COUNT("completedCount"),
  LAST_COMPLETED_AT("lastCompletedAt"),
  CREATED_COUNT("createdCount"),
  LAST_CREATED_AT("lastCreatedAt"),
  JOB_TYPE("jobType"),
  WORKER("worker");

  private final String property;

  JobMetricsBatchColumn(final String property) {
    this.property = property;
  }

  @Override
  public String property() {
    return property;
  }

  @Override
  public Class<JobMetricsBatchEntity> getEntityClass() {
    return JobMetricsBatchEntity.class;
  }
}
