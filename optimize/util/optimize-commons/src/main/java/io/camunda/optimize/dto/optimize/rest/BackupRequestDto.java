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
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  @Override
  public String toString() {
    return "BackupRequestDto(backupId=" + getBackupId() + ")";
  }
}
