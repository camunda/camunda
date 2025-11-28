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

public class DocumentBasedSecondaryStorageBackup implements Cloneable {

  private static final Set<String> LEGACY_OPERATE_SNAPSHOT_TIMEOUT =
      Set.of("camunda.operate.backup.snapshotTimeout");
  private static final Set<String> LEGACY_UNIFIED_CONFIG_SNAPSHOT_TIMEOUT =
      Set.of("camunda.data.backup.snapshot-timeout");
  private static final Set<String> LEGACY_OPERATE_INCOMPLETE_CHECK_TIMEOUT_IN_SECONDS =
      Set.of("camunda.operate.backup.incompleteCheckTimeoutInSeconds");
  private static final Set<String> LEGACY_UNIFIED_CONFIG_INCOMPLETE_CHECK_TIMEOUT =
      Set.of("camunda.data.backup.incomplete-check-timeout");
  private static final Set<String> LEGACY_REPOSITORY_NAME_PROPERTIES =
      Set.of(
          "camunda.data.backup.repository-name",
          "camunda.tasklist.backup.repositoryName",
          "camunda.operate.backup.repositoryName");

  /**
   * Set the ES / OS snapshot repository name.
   *
   * <p>Note: This setting applies to backups of secondary storage.
   */
  private String repositoryName;

  /**
   * A backup of history data consists of multiple Elasticsearch/Opensearch snapshots.
   * snapshotTimeout controls the maximum time to wait for a snapshot operation to complete during
   * backup creation. When set to 0, the system will wait indefinitely for snapshots to finish.
   *
   * <p>Note: This setting applies to backups of secondary storage.
   */
  private int snapshotTimeout = 0;

  /**
   * Defines the timeout period for determining whether an incomplete backup should be considered as
   * failed or still in progress. This property helps distinguish between backups that are actively
   * running versus those that may have stalled or failed silently.
   *
   * <p>Note: This setting applies to backups of secondary storage.
   */
  private Duration incompleteCheckTimeout = Duration.ofMinutes(5);

  private final String prefix;

  public DocumentBasedSecondaryStorageBackup(final String databaseName) {
    prefix = "camunda.data.secondary-storage.%s.backup".formatted(databaseName);
  }

  public String getRepositoryName() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        prefix + ".repository-name",
        repositoryName,
        String.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_REPOSITORY_NAME_PROPERTIES);
  }

  public void setRepositoryName(final String repositoryName) {
    this.repositoryName = repositoryName;
  }

  public int getSnapshotTimeout() {
    final int legacyOperateSnapshotTimeout =
        UnifiedConfigurationHelper.validateLegacyConfiguration(
            prefix + ".snapshot-timeout",
            snapshotTimeout,
            Integer.class,
            BackwardsCompatibilityMode.SUPPORTED,
            LEGACY_OPERATE_SNAPSHOT_TIMEOUT);

    final int legacyUnifiedSnapshotTimeout =
        UnifiedConfigurationHelper.validateLegacyConfiguration(
            prefix + ".snapshot-timeout",
            snapshotTimeout,
            Integer.class,
            BackwardsCompatibilityMode.SUPPORTED,
            LEGACY_UNIFIED_CONFIG_SNAPSHOT_TIMEOUT);

    // Give precedence to legacy unified configuration value
    if (legacyUnifiedSnapshotTimeout != snapshotTimeout) {
      return legacyUnifiedSnapshotTimeout;
    }
    return legacyOperateSnapshotTimeout;
  }

  public void setSnapshotTimeout(final int snapshotTimeout) {
    this.snapshotTimeout = snapshotTimeout;
  }

  public Duration getIncompleteCheckTimeout() {
    final long incompleteCheckTimeoutInSeconds =
        UnifiedConfigurationHelper.validateLegacyConfiguration(
            prefix + ".incomplete-check-timeout",
            incompleteCheckTimeout.getSeconds(),
            Long.class,
            BackwardsCompatibilityMode.SUPPORTED,
            LEGACY_OPERATE_INCOMPLETE_CHECK_TIMEOUT_IN_SECONDS);

    final Duration incompleteCheckTimeoutInDuration =
        UnifiedConfigurationHelper.validateLegacyConfiguration(
            prefix + ".incomplete-check-timeout",
            incompleteCheckTimeout,
            Duration.class,
            BackwardsCompatibilityMode.SUPPORTED,
            LEGACY_UNIFIED_CONFIG_INCOMPLETE_CHECK_TIMEOUT);

    // Give precedence to legacy unified configuration value
    if (incompleteCheckTimeoutInDuration.getSeconds() != incompleteCheckTimeout.getSeconds()) {
      return incompleteCheckTimeoutInDuration;
    }

    return Duration.ofSeconds(incompleteCheckTimeoutInSeconds);
  }

  public void setIncompleteCheckTimeout(final Duration incompleteCheckTimeout) {
    this.incompleteCheckTimeout = incompleteCheckTimeout;
  }

  @Override
  public Object clone() {
    try {
      return super.clone();
    } catch (final CloneNotSupportedException e) {
      throw new AssertionError("Unexpected: Class must implement Cloneable", e);
    }
  }
}
