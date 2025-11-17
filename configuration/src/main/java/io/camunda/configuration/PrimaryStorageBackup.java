/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.configuration.UnifiedConfigurationHelper.BackwardsCompatibilityMode;
import java.time.Duration;
import java.util.Set;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

public class PrimaryStorageBackup {
  private static final String PREFIX = "camunda.data.primary-storage.backup";

  private static final Set<String> LEGACY_CONTINUOUS_BACKUPS_PROPERTIES =
      Set.of(
          "zeebe.broker.experimental.continuous-backups",
          "zeebe.broker.data.backup-scheduler.continuous");
  private static final Set<String> LEGACY_BROKER_SCHEDULER_SCHEDULE_PROPERTY =
      Set.of("zeebe.broker.data.backup-scheduler.schedule");
  private static final Set<String> LEGACY_BROKER_SCHEDULER_CHECKPOINT_INTERVAL_PROPERTY =
      Set.of("zeebe.broker.data.backup-scheduler.checkpoint-interval");
  private static final Set<String> LEGACY_BROKER_SCHEDULER_REQUIRED_PROPERTY =
      Set.of("zeebe.broker.data.backup-scheduler.required");
  private static final Set<String> LEGACY_BROKER_SCHEDULER_ID_OFFSET_PROPERTY =
      Set.of("zeebe.broker.data.backup-scheduler.offset");

  private boolean required = false;
  private boolean continuous = false;
  private String schedule;
  private Duration checkpointInterval;
  private long offset;

  @NestedConfigurationProperty
  private PrimaryStorageBackupRetention retention = new PrimaryStorageBackupRetention();

  public boolean isContinuous() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + "continuous",
        continuous,
        Boolean.class,
        BackwardsCompatibilityMode.SUPPORTED_ONLY_IF_VALUES_MATCH,
        LEGACY_CONTINUOUS_BACKUPS_PROPERTIES);
  }

  public void setContinuous(final boolean continuous) {
    this.continuous = continuous;
  }

  public String getSchedule() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + "schedule",
        schedule,
        String.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_BROKER_SCHEDULER_SCHEDULE_PROPERTY);
  }

  public void setSchedule(final String schedule) {
    this.schedule = schedule;
  }

  public Duration getCheckpointInterval() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + "checkpoint-interval",
        checkpointInterval,
        Duration.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_BROKER_SCHEDULER_CHECKPOINT_INTERVAL_PROPERTY);
  }

  public void setCheckpointInterval(final Duration checkpointInterval) {
    this.checkpointInterval = checkpointInterval;
  }

  public PrimaryStorageBackupRetention getRetention() {
    return retention;
  }

  public void setRetention(final PrimaryStorageBackupRetention retention) {
    this.retention = retention;
  }

  public boolean isRequired() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + "required",
        required,
        Boolean.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_BROKER_SCHEDULER_REQUIRED_PROPERTY);
  }

  public void setRequired(final boolean required) {
    this.required = required;
  }

  public long getOffset() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + "offset",
        offset,
        Long.class,
        BackwardsCompatibilityMode.SUPPORTED_ONLY_IF_VALUES_MATCH,
        LEGACY_BROKER_SCHEDULER_ID_OFFSET_PROPERTY);
  }

  public void setOffset(final long offset) {
    this.offset = offset;
  }
}
