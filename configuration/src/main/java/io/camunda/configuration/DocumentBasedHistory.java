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

public class DocumentBasedHistory {

  public static final Duration DEFAULT_HISTORY_MAX_DELAY_BETWEEN_RUNS = Duration.ofMillis(60000);
  private static final Duration DEFAULT_HISTORY_DELAY_BETWEEN_RUNS = Duration.ofMillis(2000);
  private static final boolean DEFAULT_HISTORY_PROCESS_INSTANCE_ENABLED = true;
  private static final String DEFAULT_HISTORY_POLICY_NAME = "camunda-retention-policy";
  private static final String DEFAULT_HISTORY_ELS_ROLLOVER_DATE_FORMAT = "date";
  private static final String DEFAULT_HISTORY_ROLLOVER_INTERVAL = "1d";
  private static final int DEFAULT_HISTORY_ROLLOVER_BATCH_SIZE = 100;
  private static final String DEFAULT_HISTORY_WAIT_PERIOD_BEFORE_ARCHIVING = "1h";
  private static final Map<String, String> LEGACY_BROKER_PROPERTIES =
      Map.of(
          "process-instance-enabled",
          "zeebe.broker.exporters.camundaexporter.args.history.process-instance-enabled",
          "els-rollover-date-format",
          "zeebe.broker.exporters.camundaexporter.args.history.elsRolloverDateFormat",
          "rollover-interval",
          "zeebe.broker.exporters.camundaexporter.args.history.rolloverInterval",
          "rollover-batch-size",
          "zeebe.broker.exporters.camundaexporter.args.history.rolloverBatchSize",
          "wait-period-before-archiving",
          "zeebe.broker.exporters.camundaexporter.args.history.waitPeriodBeforeArchiving",
          "delay-between-runs",
          "zeebe.broker.exporters.camundaexporter.args.history.delayBetweenRuns",
          "max-delay-between-runs",
          "zeebe.broker.exporters.camundaexporter.args.history.maxDelayBetweenRuns");
  private final String prefix;

  private boolean processInstanceEnabled = DEFAULT_HISTORY_PROCESS_INSTANCE_ENABLED;

  /** Date format for historical indices in Java DateTimeFormatter syntax */
  private String elsRolloverDateFormat = DEFAULT_HISTORY_ELS_ROLLOVER_DATE_FORMAT;

  /** Time range for creating dated indices (e.g., 1d creates daily indices). */
  private String rolloverInterval = DEFAULT_HISTORY_ROLLOVER_INTERVAL;

  /** Maximum number of process instances per archiving batch */
  private int rolloverBatchSize = DEFAULT_HISTORY_ROLLOVER_BATCH_SIZE;

  /**
   * Grace period before archiving completed processes. Processes finished within this window are
   * not yet archived.
   */
  private String waitPeriodBeforeArchiving = DEFAULT_HISTORY_WAIT_PERIOD_BEFORE_ARCHIVING;

  /** Millisecond interval between archiver runs */
  private Duration delayBetweenRuns = DEFAULT_HISTORY_DELAY_BETWEEN_RUNS;

  /** Maximum millisecond interval between archiver runs due to failure backoffs */
  private Duration maxDelayBetweenRuns = DEFAULT_HISTORY_MAX_DELAY_BETWEEN_RUNS;

  /** Defines the name of the created and applied ILM policy. */
  private String policyName = DEFAULT_HISTORY_POLICY_NAME;

  public DocumentBasedHistory(final String databaseName) {
    prefix = "camunda.data.secondary-storage.%s.history".formatted(databaseName);
  }

  public boolean isProcessInstanceEnabled() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        prefix + ".process-instance-enabled",
        processInstanceEnabled,
        Boolean.class,
        BackwardsCompatibilityMode.SUPPORTED_ONLY_IF_VALUES_MATCH,
        Set.of(LEGACY_BROKER_PROPERTIES.get("process-instance-enabled")));
  }

  public void setProcessInstanceEnabled(final boolean processInstanceEnabled) {
    this.processInstanceEnabled = processInstanceEnabled;
  }

  public String getPrefix() {
    return prefix;
  }

  public String getElsRolloverDateFormat() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        prefix + ".els-rollover-date-format",
        elsRolloverDateFormat,
        String.class,
        BackwardsCompatibilityMode.SUPPORTED,
        Set.of(LEGACY_BROKER_PROPERTIES.get("els-rollover-date-format")));
  }

  public void setElsRolloverDateFormat(final String elsRolloverDateFormat) {
    this.elsRolloverDateFormat = elsRolloverDateFormat;
  }

  public String getRolloverInterval() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        prefix + ".rollover-interval",
        rolloverInterval,
        String.class,
        BackwardsCompatibilityMode.SUPPORTED,
        Set.of(LEGACY_BROKER_PROPERTIES.get("rollover-interval")));
  }

  public void setRolloverInterval(final String rolloverInterval) {
    this.rolloverInterval = rolloverInterval;
  }

  public int getRolloverBatchSize() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        prefix + ".rollover-batch-size",
        rolloverBatchSize,
        Integer.class,
        BackwardsCompatibilityMode.SUPPORTED,
        Set.of(LEGACY_BROKER_PROPERTIES.get("rollover-batch-size")));
  }

  public void setRolloverBatchSize(final int rolloverBatchSize) {
    this.rolloverBatchSize = rolloverBatchSize;
  }

  public String getWaitPeriodBeforeArchiving() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        prefix + ".wait-period-before-archiving",
        waitPeriodBeforeArchiving,
        String.class,
        BackwardsCompatibilityMode.SUPPORTED,
        Set.of(LEGACY_BROKER_PROPERTIES.get("wait-period-before-archiving")));
  }

  public void setWaitPeriodBeforeArchiving(final String waitPeriodBeforeArchiving) {
    this.waitPeriodBeforeArchiving = waitPeriodBeforeArchiving;
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

  public String getPolicyName() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        prefix + ".policy-name",
        policyName,
        String.class,
        BackwardsCompatibilityMode.SUPPORTED_ONLY_IF_VALUES_MATCH,
        legacyPolicyNameProperties());
  }

  public void setPolicyName(final String policyName) {
    this.policyName = policyName;
  }

  private Set<String> legacyPolicyNameProperties() {
    return Set.of(
        "camunda.database.retention.policyName",
        "zeebe.broker.exporters.camundaexporter.args.history.retention.policyName");
  }
}
