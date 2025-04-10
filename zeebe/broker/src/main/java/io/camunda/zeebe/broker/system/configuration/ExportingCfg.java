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
import java.util.Set;

/**
 * Exporting component configuration. This configuration pertains to configurations that are common
 * to all exporters.
 */
public record ExportingCfg(Set<Long> skipRecords, Duration distributionInterval) {

  public ExportingCfg(final Set<Long> skipRecords, final Duration distributionInterval) {
    this.skipRecords = skipRecords == null ? Set.of() : skipRecords;
    this.distributionInterval =
        distributionInterval == null ? DEFAULT_DISTRIBUTION_INTERVAL : distributionInterval;
  }

  public static ExportingCfg defaultExportingCfg() {
    return new ExportingCfg(null, null);
  }
}
