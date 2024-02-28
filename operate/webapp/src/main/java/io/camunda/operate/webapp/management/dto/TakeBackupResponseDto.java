/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.management.dto;

import java.util.List;

public class TakeBackupResponseDto {

  private List<String> scheduledSnapshots;

  public List<String> getScheduledSnapshots() {
    return scheduledSnapshots;
  }

  public TakeBackupResponseDto setScheduledSnapshots(List<String> scheduledSnapshots) {
    this.scheduledSnapshots = scheduledSnapshots;
    return this;
  }
}
