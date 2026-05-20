/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static io.camunda.zeebe.broker.exporter.stream.ExporterDirectorContext.DEFAULT_DISTRIBUTION_INTERVAL;

import io.camunda.configuration.UnifiedConfigurationHelper.BackwardsCompatibilityMode;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

public class Export {
  private static final String PREFIX = "camunda.data.export";
  private static final Set<String> LEGACY_DISTRIBUTION_INTERVAL_PROPERTIES =
      Set.of("zeebe.broker.exporting.distributionInterval");

  /**
   * Configures the rate at which exporter positions are distributed to the followers. This is
   * useful for fail-over and taking snapshots. The follower is able to take snapshots based on
   * replayed and distributed export position. When a follower takes over it can recover from the
   * snapshot, it doesn't need to replay and export everything. It can for example can start from
   * the last exported position it has received by the distribution mechanism.
   */
  private Duration distributionInterval = DEFAULT_DISTRIBUTION_INTERVAL;

  /**
   * Enable the exporters to skip record positions per partition. Allows to skip certain records by
   * their position for a specific partition. This is useful for debugging or skipping a record that
   * is preventing processing or exporting to continue. Record positions defined to skip in this
   * definition will be skipped only for the specified partition. The value is a map of partition id
   * to a comma-separated list of record positions to skip. Whitespace is ignored.
   *
   * <p><b> Backwards compatibility with the legacy `zeebe.broker.exporting.skip-records` is broken
   * deliberately as this configuration should only be used for recovery purposes</b>
   */
  private Map<Integer, Set<Long>> skipRecords = Map.of();

  public Duration getDistributionInterval() {
    return UnifiedConfigurationHelper.validateLegacyConfigurationUnsafe(
        PREFIX + ".distribution-interval",
        distributionInterval,
        Duration.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_DISTRIBUTION_INTERVAL_PROPERTIES);
  }

  public void setDistributionInterval(final Duration distributionInterval) {
    this.distributionInterval = distributionInterval;
  }

  public Map<Integer, Set<Long>> getSkipRecords() {
    return skipRecords;
  }

  public void setSkipRecords(final Map<Integer, Set<Long>> skipRecords) {
    this.skipRecords = skipRecords;
  }
}
