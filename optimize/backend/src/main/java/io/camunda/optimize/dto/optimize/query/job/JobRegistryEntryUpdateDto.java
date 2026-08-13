/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.job;

import java.time.OffsetDateTime;

// Nulls must serialize - a retry that completes successfully needs to clear a stale
// errorMessage left by a prior FAILED state, so this class deliberately has no
// @JsonInclude(NON_NULL).
public class JobRegistryEntryUpdateDto {

  private JobStatus status;
  private String errorMessage;
  private OffsetDateTime completedAt;

  public JobRegistryEntryUpdateDto(
      final JobStatus status, final String errorMessage, final OffsetDateTime completedAt) {
    this.status = status;
    this.errorMessage = errorMessage;
    this.completedAt = completedAt;
  }

  protected JobRegistryEntryUpdateDto() {}

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

  public OffsetDateTime getCompletedAt() {
    return completedAt;
  }

  public void setCompletedAt(final OffsetDateTime completedAt) {
    this.completedAt = completedAt;
  }
}
