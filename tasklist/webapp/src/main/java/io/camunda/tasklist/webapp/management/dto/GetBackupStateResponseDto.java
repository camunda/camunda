/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.management.dto;

import java.util.List;
import java.util.Objects;

public class GetBackupStateResponseDto {

  private Long backupId;
  private BackupStateDto state;
  private String failureReason;
  private List<GetBackupStateResponseDetailDto> details;

  public GetBackupStateResponseDto() {}

  public GetBackupStateResponseDto(final Long backupId) {
    this.backupId = backupId;
  }

  public Long getBackupId() {
    return backupId;
  }

  public GetBackupStateResponseDto setBackupId(final Long backupId) {
    this.backupId = backupId;
    return this;
  }

  public BackupStateDto getState() {
    return state;
  }

  public GetBackupStateResponseDto setState(final BackupStateDto state) {
    this.state = state;
    return this;
  }

  public String getFailureReason() {
    return failureReason;
  }

  public GetBackupStateResponseDto setFailureReason(final String failureReason) {
    this.failureReason = failureReason;
    return this;
  }

  public List<GetBackupStateResponseDetailDto> getDetails() {
    return details;
  }

  public GetBackupStateResponseDto setDetails(final List<GetBackupStateResponseDetailDto> details) {
    this.details = details;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(backupId, state, failureReason, details);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final GetBackupStateResponseDto that = (GetBackupStateResponseDto) o;
    return Objects.equals(backupId, that.backupId)
        && state == that.state
        && Objects.equals(failureReason, that.failureReason)
        && Objects.equals(details, that.details);
  }

  @Override
  public String toString() {
    return "GetBackupStateResponseDto{"
        + "backupId="
        + backupId
        + ", state="
        + state
        + ", failureReason='"
        + failureReason
        + '\''
        + ", details="
        + details
        + '}';
  }
}
