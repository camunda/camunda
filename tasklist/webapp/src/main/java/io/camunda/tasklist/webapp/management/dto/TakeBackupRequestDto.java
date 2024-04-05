/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.management.dto;

public class TakeBackupRequestDto {

  private Long backupId;

  public Long getBackupId() {
    return backupId;
  }

  public TakeBackupRequestDto setBackupId(Long backupId) {
    this.backupId = backupId;
    return this;
  }
}
