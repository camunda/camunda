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
import java.util.LinkedHashSet;
import java.util.Set;

public class DocumentBasedSecondaryStorageBackup implements Cloneable {
  private static final Set<String> LEGACY_OPERATE_SNAPSHOT_TIMEOUT =
      Set.of("camunda.operate.backup.snapshotTimeout");
  private static final Set<Set<String>> LEGACY_UNIFIED_CONFIG_SNAPSHOT_TIMEOUT =
      LinkedHashSet.newLinkedHashSet(2);
  private static final Set<String> LEGACY_OPERATE_INCOMPLETE_CHECK_TIMEOUT_IN_SECONDS =
      Set.of("camunda.operate.backup.incompleteCheckTimeoutInSeconds");
  private static final Set<String> LEGACY_UNIFIED_CONFIG_INCOMPLETE_CHECK_TIMEOUT =
      Set.of("camunda.data.backup.incomplete-check-timeout");
  private static final Set<String> LEGACY_REPOSITORY_NAME_PROPERTIES =
      Set.of("camunda.tasklist.backup.repositoryName", "camunda.operate.backup.repositoryName");
  private static final Set<Set<String>> LEGACY_UNIFIED_REPOSITORY_NAME_PROPERTIES =
      LinkedHashSet.newLinkedHashSet(2);
  private static final Duration INCOMPLETE_CHECK_TIMEOUT_DEFAULT = Duration.ofMinutes(5);

  static {
    LEGACY_UNIFIED_REPOSITORY_NAME_PROPERTIES.add(LEGACY_REPOSITORY_NAME_PROPERTIES);
    LEGACY_UNIFIED_REPOSITORY_NAME_PROPERTIES.add(Set.of("camunda.data.backup.repository-name"));

    LEGACY_UNIFIED_CONFIG_SNAPSHOT_TIMEOUT.add(LEGACY_OPERATE_SNAPSHOT_TIMEOUT);
    LEGACY_UNIFIED_CONFIG_SNAPSHOT_TIMEOUT.add(Set.of("camunda.data.backup.snapshot-timeout"));
  }

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
  private Duration incompleteCheckTimeout = INCOMPLETE_CHECK_TIMEOUT_DEFAULT;

  private final String prefix;

  public DocumentBasedSecondaryStorageBackup(final String databaseName) {
    prefix = "camunda.data.secondary-storage.%s.backup".formatted(databaseName);
  }

  public String getRepositoryName() {
    return UnifiedConfigurationHelper.validateLegacyConfigurationWithOrdering(
        prefix + ".repository-name",
        repositoryName,
        String.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_UNIFIED_REPOSITORY_NAME_PROPERTIES);
  }

  public void setRepositoryName(final String repositoryName) {
    this.repositoryName = repositoryName;
  }

  public int getSnapshotTimeout() {
    return UnifiedConfigurationHelper.validateLegacyConfigurationWithOrdering(
        prefix + ".snapshot-timeout",
        snapshotTimeout,
        Integer.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_UNIFIED_CONFIG_SNAPSHOT_TIMEOUT);
  }

  public void setSnapshotTimeout(final int snapshotTimeout) {
    this.snapshotTimeout = snapshotTimeout;
  }

  public Duration getIncompleteCheckTimeout() {

    // Have to handle both Long (legacy operate) and Duration (legacy unified config) separately
    // due to type difference. Test legacy with old unified config key first and then against the
    // updated unified config key.
    final long legacyIncompleteCheckTimeout =
        UnifiedConfigurationHelper.validateLegacyConfiguration(
            // Old unified config key kept for backwards compatibility
            "camunda.data.backup.incomplete-check-timeout",
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

    // legacy or old unified config value exists
    if (legacyIncompleteCheckTimeout != incompleteCheckTimeoutInDuration.getSeconds()) {
      // New or old unified config value also exists, has higher precedence
      if (incompleteCheckTimeoutInDuration.getSeconds()
          != INCOMPLETE_CHECK_TIMEOUT_DEFAULT.getSeconds()) {
        return incompleteCheckTimeoutInDuration;
      }
      return Duration.ofSeconds(legacyIncompleteCheckTimeout);
    }
    return incompleteCheckTimeoutInDuration;
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
