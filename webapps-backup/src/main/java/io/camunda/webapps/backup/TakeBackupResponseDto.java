/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.backup;

import java.util.List;

public class TakeBackupResponseDto {

  private List<String> scheduledSnapshots;

  public List<String> getScheduledSnapshots() {
    return scheduledSnapshots;
  }

  public TakeBackupResponseDto setScheduledSnapshots(final List<String> scheduledSnapshots) {
    this.scheduledSnapshots = scheduledSnapshots;
    return this;
  }
}
