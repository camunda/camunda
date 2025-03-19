/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest;

import io.camunda.optimize.dto.optimize.BackupState;
import java.util.List;

public class BackupInfoDto {

  private long backupId;
  private String failureReason;
  private BackupState state;
  private List<SnapshotInfoDto> details;

  public BackupInfoDto(
      final long backupId,
      final String failureReason,
      final BackupState state,
      final List<SnapshotInfoDto> details) {
    this.backupId = backupId;
    this.failureReason = failureReason;
    this.state = state;
    this.details = details;
  }

  public BackupInfoDto() {}

  public long getBackupId() {
    return backupId;
  }

  public void setBackupId(final long backupId) {
    this.backupId = backupId;
  }

  public String getFailureReason() {
    return failureReason;
  }

  public void setFailureReason(final String failureReason) {
    this.failureReason = failureReason;
  }

  public BackupState getState() {
    return state;
  }

  public void setState(final BackupState state) {
    this.state = state;
  }

  public List<SnapshotInfoDto> getDetails() {
    return details;
  }

  public void setDetails(final List<SnapshotInfoDto> details) {
    this.details = details;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof BackupInfoDto;
  }

  @Override
  public int hashCode() {
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  @Override
  public String toString() {
    return "BackupInfoDto(backupId="
        + getBackupId()
        + ", failureReason="
        + getFailureReason()
        + ", state="
        + getState()
        + ", details="
        + getDetails()
        + ")";
  }
}
