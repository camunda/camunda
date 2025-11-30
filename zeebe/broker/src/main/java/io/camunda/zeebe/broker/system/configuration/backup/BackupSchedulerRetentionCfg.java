/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration.backup;

import io.camunda.zeebe.backup.schedule.Schedule;
import io.camunda.zeebe.broker.system.configuration.ConfigurationEntry;
import java.time.Duration;

public class BackupSchedulerRetentionCfg implements ConfigurationEntry {

  /*
   * The active continuous backup window to be retained at every time
   */
  private Duration window;

  /*
   * The cleanup schedule expression. Can be either a CRON expression or an ISO8601 interval
   * duration. Determines how often the backup clean up job runs.
   */
  private String cleanupSchedule;

  public Duration getWindow() {
    return window;
  }

  public void setWindow(final Duration window) {
    this.window = window;
  }

  public Schedule getCleanupSchedule() throws IllegalArgumentException {
    return Schedule.parseSchedule(cleanupSchedule);
  }

  public void setCleanupSchedule(final String cleanupSchedule) {
    this.cleanupSchedule = cleanupSchedule;
  }

  @Override
  public String toString() {
    return "BackupSchedulerRetentionCfg{"
        + "window="
        + window
        + ", cleanupSchedule='"
        + cleanupSchedule
        + '\''
        + '}';
  }
}
