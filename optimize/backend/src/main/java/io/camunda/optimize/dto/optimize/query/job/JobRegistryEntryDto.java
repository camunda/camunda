/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.job;

import io.camunda.optimize.service.security.util.LocalDateUtil;
import io.camunda.optimize.service.util.IdGenerator;
import java.time.OffsetDateTime;

public class JobRegistryEntryDto {

  private String id;
  private JobType jobType;
  private EntityType entityType;
  private String entityId;
  private JobStatus status;
  private String errorMessage;
  private OffsetDateTime createdAt;
  private OffsetDateTime updatedAt;

  public JobRegistryEntryDto(
      final JobType jobType, final EntityType entityType, final String entityId) {
    id = IdGenerator.getNextId();
    this.jobType = jobType;
    this.entityType = entityType;
    this.entityId = entityId;
    status = JobStatus.QUEUED;
    createdAt = LocalDateUtil.getCurrentDateTime();
  }

  protected JobRegistryEntryDto() {}

  public String getId() {
    return id;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public JobType getJobType() {
    return jobType;
  }

  public void setJobType(final JobType jobType) {
    this.jobType = jobType;
  }

  public EntityType getEntityType() {
    return entityType;
  }

  public void setEntityType(final EntityType entityType) {
    this.entityType = entityType;
  }

  public String getEntityId() {
    return entityId;
  }

  public void setEntityId(final String entityId) {
    this.entityId = entityId;
  }

  public JobStatus getStatus() {
    return status;
  }

  public void setStatus(final JobStatus status) {
    this.status = status;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(final String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(final OffsetDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(final OffsetDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }
}
