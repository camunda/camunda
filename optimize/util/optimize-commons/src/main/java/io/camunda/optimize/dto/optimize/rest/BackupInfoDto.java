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
import lombok.Data;

@Data
public class BackupInfoDto {

  private long backupId;
  private String failureReason;
  private BackupState state;
  private List<SnapshotInfoDto> details;

  public BackupInfoDto(
      long backupId, String failureReason, BackupState state, List<SnapshotInfoDto> details) {
    this.backupId = backupId;
    this.failureReason = failureReason;
    this.state = state;
    this.details = details;
  }

  public BackupInfoDto() {}
}
