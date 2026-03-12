/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration;

import static io.camunda.zeebe.broker.exporter.stream.ExporterDirectorContext.DEFAULT_DISTRIBUTION_INTERVAL;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

/**
 * Exporting component configuration. This configuration pertains to configurations that are common
 * to all exporters.
 *
 * <p><b> Backwards compatibility with the legacy `zeebe.broker.exporting.skip-records` is broken
 * deliberately as this configuration should only be used for recovery purposes</b>
 */
<<<<<<< HEAD
public record ExportingCfg(Map<Integer, Set<Long>> skipRecords, Duration distributionInterval) {

  public ExportingCfg(
      final Map<Integer, Set<Long>> skipRecords, final Duration distributionInterval) {
    this.skipRecords = skipRecords == null ? Map.of() : skipRecords;
=======
public record ExportingCfg(
    Set<Long> skipRecords,
    Duration distributionInterval,
    Map<Integer, Set<Long>> skipRecordsForPartitions) {

  public ExportingCfg(
      final Set<Long> skipRecords,
      final Duration distributionInterval,
      final Map<Integer, Set<Long>> skipRecordsForPartitions) {
    this.skipRecords = skipRecords == null ? Set.of() : skipRecords;
>>>>>>> 33733d74 (feat: support per-partition skip record positions for ExporterDirector)
    this.distributionInterval =
        distributionInterval == null ? DEFAULT_DISTRIBUTION_INTERVAL : distributionInterval;
    this.skipRecordsForPartitions =
        skipRecordsForPartitions == null ? Map.of() : skipRecordsForPartitions;
  }

  public static ExportingCfg defaultExportingCfg() {
    return new ExportingCfg(null, null, null);
  }
}
