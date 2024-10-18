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
    final int PRIME = 59;
    int result = 1;
    final long $backupId = getBackupId();
    result = result * PRIME + (int) ($backupId >>> 32 ^ $backupId);
    final Object $failureReason = getFailureReason();
    result = result * PRIME + ($failureReason == null ? 43 : $failureReason.hashCode());
    final Object $state = getState();
    result = result * PRIME + ($state == null ? 43 : $state.hashCode());
    final Object $details = getDetails();
    result = result * PRIME + ($details == null ? 43 : $details.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof BackupInfoDto)) {
      return false;
    }
    final BackupInfoDto other = (BackupInfoDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (getBackupId() != other.getBackupId()) {
      return false;
    }
    final Object this$failureReason = getFailureReason();
    final Object other$failureReason = other.getFailureReason();
    if (this$failureReason == null
        ? other$failureReason != null
        : !this$failureReason.equals(other$failureReason)) {
      return false;
    }
    final Object this$state = getState();
    final Object other$state = other.getState();
    if (this$state == null ? other$state != null : !this$state.equals(other$state)) {
      return false;
    }
    final Object this$details = getDetails();
    final Object other$details = other.getDetails();
    if (this$details == null ? other$details != null : !this$details.equals(other$details)) {
      return false;
    }
    return true;
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
