/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static io.camunda.zeebe.engine.EngineConfiguration.DEFAULT_MESSAGES_TTL_CHECKER_BATCH_LIMIT;
import static io.camunda.zeebe.engine.EngineConfiguration.DEFAULT_MESSAGES_TTL_CHECKER_INTERVAL;

import io.camunda.configuration.UnifiedConfigurationHelper.BackwardsCompatibilityMode;
import java.time.Duration;
import java.util.Set;

public class EngineMessages {
  private static final String PREFIX = "camunda.processing.engine.messages";

  private static final Set<String> LEGACY_TTL_CHECKER_BATCH_LIMIT_PROPERTIES =
      Set.of("zeebe.broker.experimental.engine.messages.ttlCheckerBatchLimit");
  private static final Set<String> LEGACY_TTL_CHECKER_INTERVAL_PROPERTIES =
      Set.of("zeebe.broker.experimental.engine.messages.ttlCheckerInterval");

  /** Configures the maximum number of expired messages processed per Time-To-Live check. */
  private int ttlCheckerBatchLimit = DEFAULT_MESSAGES_TTL_CHECKER_BATCH_LIMIT;

  /** Configures the interval at which buffered messages are checked for expiry. */
  private Duration ttlCheckerInterval = DEFAULT_MESSAGES_TTL_CHECKER_INTERVAL;

  public int getTtlCheckerBatchLimit() {
    return UnifiedConfigurationHelper.validateLegacyConfigurationUnsafe(
        PREFIX + ".ttl-checker-batch-limit",
        ttlCheckerBatchLimit,
        Integer.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_TTL_CHECKER_BATCH_LIMIT_PROPERTIES);
  }

  public void setTtlCheckerBatchLimit(final int ttlCheckerBatchLimit) {
    this.ttlCheckerBatchLimit = ttlCheckerBatchLimit;
  }

  public Duration getTtlCheckerInterval() {
    return UnifiedConfigurationHelper.validateLegacyConfigurationUnsafe(
        PREFIX + ".ttl-checker-interval",
        ttlCheckerInterval,
        Duration.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_TTL_CHECKER_INTERVAL_PROPERTIES);
  }

  public void setTtlCheckerInterval(final Duration ttlCheckerInterval) {
    this.ttlCheckerInterval = ttlCheckerInterval;
  }
}
