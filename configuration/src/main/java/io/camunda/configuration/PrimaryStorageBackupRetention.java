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

public class PrimaryStorageBackupRetention {
  private static final String PREFIX = "camunda.data.primary-storage.backup.retention";

  private static final Set<String> LEGACY_BROKER_SCHEDULER_RETENTION_CLEANUP_SCHEDULE =
      Set.of("zeebe.broker.data.backup-scheduler.retention.cleanup-schedule");

  private static final Set<String> LEGACY_BROKER_SCHEDULER_RETENTION_WINDOW_PROPERTY =
      Set.of("zeebe.broker.data.backup-scheduler.retention.window");

  private Duration window;
  private String cleanupSchedule;

  public Duration getWindow() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + "window",
        window,
        Duration.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_BROKER_SCHEDULER_RETENTION_WINDOW_PROPERTY);
  }

  public void setWindow(final Duration window) {
    this.window = window;
  }

  public String getCleanupSchedule() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + "cleanup-schedule",
        cleanupSchedule,
        String.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_BROKER_SCHEDULER_RETENTION_CLEANUP_SCHEDULE);
  }

  public void setCleanupSchedule(final String cleanupSchedule) {
    this.cleanupSchedule = cleanupSchedule;
  }
}
