/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.job;

import io.camunda.optimize.service.security.util.LocalDateUtil;
import java.time.OffsetDateTime;
import java.util.UUID;

public class JobRegistryEntryDto {

  private String id;
  private JobType jobType;
  private TargetEntityType targetEntityType;
  private String targetEntityId;
  private JobStatus status;
  private String errorMessage;
  private OffsetDateTime createdAt;
  private OffsetDateTime completedAt;

  public JobRegistryEntryDto(
      final JobType jobType, final TargetEntityType targetEntityType, final String targetEntityId) {
    id = UUID.randomUUID().toString();
    this.jobType = jobType;
    this.targetEntityType = targetEntityType;
    this.targetEntityId = targetEntityId;
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

  public TargetEntityType getTargetEntityType() {
    return targetEntityType;
  }

  public void setTargetEntityType(final TargetEntityType targetEntityType) {
    this.targetEntityType = targetEntityType;
  }

  public String getTargetEntityId() {
    return targetEntityId;
  }

  public void setTargetEntityId(final String targetEntityId) {
    this.targetEntityId = targetEntityId;
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

  public OffsetDateTime getCompletedAt() {
    return completedAt;
  }

  public void setCompletedAt(final OffsetDateTime completedAt) {
    this.completedAt = completedAt;
  }
}
