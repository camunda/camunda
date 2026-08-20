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
import java.util.Objects;

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
    return Objects.hash(backupId);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final BackupRequestDto that = (BackupRequestDto) o;
    return Objects.equals(backupId, that.backupId);
  }

  @Override
  public String toString() {
    return "BackupRequestDto(backupId=" + getBackupId() + ")";
  }
}
