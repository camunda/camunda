/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.rest;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.BackupState;

import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class BackupInfoDto {
  private int backupId;
  private String failureReason;
  private BackupState state;
  private List<SnapshotInfoDto> details;
}
