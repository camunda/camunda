/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static io.camunda.zeebe.engine.EngineConfiguration.DEFAULT_MESSAGE_START_ASK_RETRY_INTERVAL;
import static io.camunda.zeebe.engine.EngineConfiguration.DEFAULT_MESSAGE_START_DEDUP_EXPIRATION_SWEEP_BATCH_LIMIT;
import static io.camunda.zeebe.engine.EngineConfiguration.DEFAULT_MESSAGE_START_DEDUP_EXPIRATION_SWEEP_INTERVAL;

import io.camunda.configuration.UnifiedConfigurationHelper.BackwardsCompatibilityMode;
import java.time.Duration;
import java.util.Set;

public class ProcessInstanceCreation {
  private static final String PREFIX = "camunda.process-instance-creation";
  private static final boolean DEFAULT_BUSINESS_ID_UNIQUENESS_ENABLED = false;
  private static final Set<String> LEGACY_BUSINESS_ID_UNIQUENESS_ENABLED_PROPERTIES =
      Set.of(
          "zeebe.broker.experimental.engine.processInstanceCreation.businessIdUniquenessEnabled");
  private static final Set<String> LEGACY_MESSAGE_START_DEDUP_EXPIRATION_SWEEP_INTERVAL_PROPERTIES =
      Set.of(
          "zeebe.broker.experimental.engine.processInstanceCreation.messageStartDedupExpirationSweepInterval");
  private static final Set<String>
      LEGACY_MESSAGE_START_DEDUP_EXPIRATION_SWEEP_BATCH_LIMIT_PROPERTIES =
          Set.of(
              "zeebe.broker.experimental.engine.processInstanceCreation.messageStartDedupExpirationSweepBatchLimit");
  private static final Set<String> LEGACY_MESSAGE_START_ASK_RETRY_INTERVAL_PROPERTIES =
      Set.of(
          "zeebe.broker.experimental.engine.processInstanceCreation.messageStartAskRetryInterval");

  /**
   * Controls uniqueness enforcement of business IDs across active process instances.
   *
   * <ul>
   *   <li><b>Disabled (default):</b> Multiple active process instances can share the same business
   *       ID. No tracking or validation is performed.
   *   <li><b>Enabled:</b> Creating a process instance with a business ID that is already in use by
   *       an active process instance will be rejected. Business IDs of process instances created
   *       before enabling this setting are not tracked, so duplicates with those are not detected.
   * </ul>
   */
  private boolean businessIdUniquenessEnabled = DEFAULT_BUSINESS_ID_UNIQUENESS_ENABLED;

  /**
   * Interval between scheduled expired-dedup-entry sweeps of the cross-partition message-start
   * dedup state on {@code P_B}. The sweep removes dedup rows whose deletion deadline has passed; it
   * is correctness-irrelevant and only bounds storage growth.
   */
  private Duration messageStartDedupExpirationSweepInterval =
      DEFAULT_MESSAGE_START_DEDUP_EXPIRATION_SWEEP_INTERVAL;

  /**
   * Upper bound on the number of expired dedup entries removed per sweep cycle on {@code P_B}. A
   * follow-up sweep is scheduled when more entries remain.
   */
  private int messageStartDedupExpirationSweepBatchLimit =
      DEFAULT_MESSAGE_START_DEDUP_EXPIRATION_SWEEP_BATCH_LIMIT;

  /**
   * Retry cadence for the cross-partition message-start pending-ask scheduler on {@code P_K}. Every
   * tick re-emits each pending ask whose last-sent time is older than this interval; the same value
   * also drives the scheduler's tick frequency.
   */
  private Duration messageStartAskRetryInterval = DEFAULT_MESSAGE_START_ASK_RETRY_INTERVAL;

  public boolean isBusinessIdUniquenessEnabled() {
    return UnifiedConfigurationHelper.validateLegacyConfigurationUnsafe(
        PREFIX + ".business-id-uniqueness-enabled",
        businessIdUniquenessEnabled,
        Boolean.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_BUSINESS_ID_UNIQUENESS_ENABLED_PROPERTIES);
  }

  public void setBusinessIdUniquenessEnabled(final boolean businessIdUniquenessEnabled) {
    this.businessIdUniquenessEnabled = businessIdUniquenessEnabled;
  }

  public Duration getMessageStartDedupExpirationSweepInterval() {
    return UnifiedConfigurationHelper.validateLegacyConfigurationUnsafe(
        PREFIX + ".message-start-dedup-expiration-sweep-interval",
        messageStartDedupExpirationSweepInterval,
        Duration.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_MESSAGE_START_DEDUP_EXPIRATION_SWEEP_INTERVAL_PROPERTIES);
  }

  public void setMessageStartDedupExpirationSweepInterval(
      final Duration messageStartDedupExpirationSweepInterval) {
    this.messageStartDedupExpirationSweepInterval = messageStartDedupExpirationSweepInterval;
  }

  public int getMessageStartDedupExpirationSweepBatchLimit() {
    return UnifiedConfigurationHelper.validateLegacyConfigurationUnsafe(
        PREFIX + ".message-start-dedup-expiration-sweep-batch-limit",
        messageStartDedupExpirationSweepBatchLimit,
        Integer.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_MESSAGE_START_DEDUP_EXPIRATION_SWEEP_BATCH_LIMIT_PROPERTIES);
  }

  public void setMessageStartDedupExpirationSweepBatchLimit(
      final int messageStartDedupExpirationSweepBatchLimit) {
    this.messageStartDedupExpirationSweepBatchLimit = messageStartDedupExpirationSweepBatchLimit;
  }

  public Duration getMessageStartAskRetryInterval() {
    return UnifiedConfigurationHelper.validateLegacyConfigurationUnsafe(
        PREFIX + ".message-start-ask-retry-interval",
        messageStartAskRetryInterval,
        Duration.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_MESSAGE_START_ASK_RETRY_INTERVAL_PROPERTIES);
  }

  public void setMessageStartAskRetryInterval(final Duration messageStartAskRetryInterval) {
    this.messageStartAskRetryInterval = messageStartAskRetryInterval;
  }
}
