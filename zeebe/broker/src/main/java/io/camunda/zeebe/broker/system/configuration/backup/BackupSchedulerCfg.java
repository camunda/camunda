/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration.backup;

import io.camunda.zeebe.broker.system.configuration.ConfigurationEntry;
import java.time.Duration;

public class BackupSchedulerCfg implements ConfigurationEntry {

  /*
   *  Whether a backup store's presence is required for the broker to start
   */
  private boolean required = false;

  /*
   * Whether continuous backups are enabled
   */
  private boolean continuous = false;

  /*
   * Backup's schedule expression. Can be either a CRON expression or an ISO8601 interval duration.
   */
  private String schedule;

  /*
   * Checkpoint interval for restore granularity
   */
  private Duration checkpointInterval;

  /*
   * Offset to be applied on the generated backup ids if existing ones are not in the format of unix timestamp
   */
  private long offset;

  /// Retention configuration for automated backups
  private BackupSchedulerRetentionCfg retention = new BackupSchedulerRetentionCfg();

  public boolean isContinuous() {
    return continuous;
  }

  public void setContinuous(final boolean continuous) {
    this.continuous = continuous;
  }

  public String getSchedule() {
    return schedule;
  }

  public void setSchedule(final String schedule) {
    this.schedule = schedule;
  }

  public Duration getCheckpointInterval() {
    return checkpointInterval;
  }

  public void setCheckpointInterval(final Duration checkpointInterval) {
    this.checkpointInterval = checkpointInterval;
  }

  public BackupSchedulerRetentionCfg getRetention() {
    return retention;
  }

  public void setRetention(final BackupSchedulerRetentionCfg retention) {
    this.retention = retention;
  }

  public boolean isRequired() {
    return required;
  }

  public void setRequired(final boolean required) {
    this.required = required;
  }

  public long getOffset() {
    return offset;
  }

  public void setOffset(final long offset) {
    this.offset = offset;
  }

  @Override
  public String toString() {
    return "BackupSchedulerCfg{"
        + "required="
        + required
        + ", continuous="
        + continuous
        + ", schedule='"
        + schedule
        + '\''
        + ", checkpointInterval="
        + checkpointInterval
        + ", offset="
        + offset
        + ", retention="
        + retention
        + '}';
  }
}
