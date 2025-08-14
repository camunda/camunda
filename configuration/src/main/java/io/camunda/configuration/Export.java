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
import java.util.Set;

public class Export {
  private static final String PREFIX = "camunda.data.export";
  private static final Set<String> LEGACY_DISTRIBUTION_INTERVAL_PROPERTIES =
      Set.of("zeebe.broker.exporting.distributionInterval");
  private static final Set<String> LEGACY_SKIP_RECORDS_PROPERTIES =
      Set.of("zeebe.broker.exporting.skipRecords");

  /**
   * Configures the rate at which exporter positions are distributed to the followers. This is
   * useful for fail-over and taking snapshots. The follower is able to take snapshots based on
   * replayed and distributed export position. When a follower takes over it can recover from the
   * snapshot, it doesn't need to replay and export everything. It can for example can start from
   * the last exported position it has received by the distribution mechanism.
   */
  private Duration distributionInterval = DEFAULT_DISTRIBUTION_INTERVAL;

  /**
   * Enable the exporters to skip record position. Allows to skip certain records by their position.
   * This is useful for debugging or skipping a record that is preventing processing or exporting to
   * continue. Record positions defined to skip in this definition will be skipped in all exporters.
   * The value is a comma-separated list of records ids to skip. Whitespace is ignored.
   */
  private Set<Long> skipRecords = Set.of();

  public Duration getDistributionInterval() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".distribution-interval",
        distributionInterval,
        Duration.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_DISTRIBUTION_INTERVAL_PROPERTIES);
  }

  public void setDistributionInterval(final Duration distributionInterval) {
    this.distributionInterval = distributionInterval;
  }

  public Set<Long> getSkipRecords() {
    return skipRecords;
  }

  public void setSkipRecords(final Set<Long> skipRecords) {
    this.skipRecords = skipRecords;
  }
}
