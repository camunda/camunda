/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class BackupRequestDto {

  @NotNull
  @Min(0)
  private Long backupId;

  public BackupRequestDto(@NotNull @Min(0) final Long backupId) {
    this.backupId = backupId;
  }

  protected BackupRequestDto() {}

  public @NotNull @Min(0) Long getBackupId() {
    return backupId;
  }

  public void setBackupId(@NotNull @Min(0) final Long backupId) {
    this.backupId = backupId;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof BackupRequestDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $backupId = getBackupId();
    result = result * PRIME + ($backupId == null ? 43 : $backupId.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof BackupRequestDto)) {
      return false;
    }
    final BackupRequestDto other = (BackupRequestDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$backupId = getBackupId();
    final Object other$backupId = other.getBackupId();
    if (this$backupId == null ? other$backupId != null : !this$backupId.equals(other$backupId)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "BackupRequestDto(backupId=" + getBackupId() + ")";
  }
}
