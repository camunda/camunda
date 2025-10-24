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
import java.util.Map;
import java.util.Set;

public class PostExport {

  private static final int DEFAULT_BATCH_SIZE = 100;
  private static final Duration DEFAULT_DELAY_BETWEEN_RUNS = Duration.ofSeconds(2);
  private static final Duration DEFAULT_MAX_DELAY_BETWEEN_RUNS = Duration.ofSeconds(60);
  private static final boolean DEFAULT_IGNORE_MISSING_DATA = false;

  private static final Map<String, String> LEGACY_BROKER_PROPERTIES =
      Map.of(
          "batch-size",
          "zeebe.broker.exporters.camundaexporter.args.postExport.batchSize",
          "delay-between-runs",
          "zeebe.broker.exporters.camundaexporter.args.postExport.delayBetweenRuns",
          "max-delay-between-runs",
          "zeebe.broker.exporters.camundaexporter.args.postExport.maxDelayBetweenRuns",
          "ignore-missing-data",
          "zeebe.broker.exporters.camundaexporter.args.postExport.ignoreMissingData");

  private final String prefix;

  private int batchSize = DEFAULT_BATCH_SIZE;
  private Duration delayBetweenRuns = DEFAULT_DELAY_BETWEEN_RUNS;
  private Duration maxDelayBetweenRuns = DEFAULT_MAX_DELAY_BETWEEN_RUNS;
  private boolean ignoreMissingData = DEFAULT_IGNORE_MISSING_DATA;

  public PostExport(final String databaseName) {
    prefix = "camunda.data.secondary-storage.%s.post-export".formatted(databaseName);
  }

  public int getBatchSize() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        prefix + ".batch-size",
        batchSize,
        Integer.class,
        BackwardsCompatibilityMode.SUPPORTED,
        Set.of(LEGACY_BROKER_PROPERTIES.get("batch-size")));
  }

  public void setBatchSize(final int batchSize) {
    this.batchSize = batchSize;
  }

  public Duration getDelayBetweenRuns() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        prefix + ".delay-between-runs",
        delayBetweenRuns,
        Duration.class,
        BackwardsCompatibilityMode.SUPPORTED,
        Set.of(LEGACY_BROKER_PROPERTIES.get("delay-between-runs")));
  }

  public void setDelayBetweenRuns(final Duration delayBetweenRuns) {
    this.delayBetweenRuns = delayBetweenRuns;
  }

  public Duration getMaxDelayBetweenRuns() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        prefix + ".max-delay-between-runs",
        maxDelayBetweenRuns,
        Duration.class,
        BackwardsCompatibilityMode.SUPPORTED,
        Set.of(LEGACY_BROKER_PROPERTIES.get("max-delay-between-runs")));
  }

  public void setMaxDelayBetweenRuns(final Duration maxDelayBetweenRuns) {
    this.maxDelayBetweenRuns = maxDelayBetweenRuns;
  }

  public boolean isIgnoreMissingData() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        prefix + ".ignore-missing-data",
        ignoreMissingData,
        Boolean.class,
        BackwardsCompatibilityMode.SUPPORTED,
        Set.of(LEGACY_BROKER_PROPERTIES.get("ignore-missing-data")));
  }

  public void setIgnoreMissingData(final boolean ignoreMissingData) {
    this.ignoreMissingData = ignoreMissingData;
  }
}
