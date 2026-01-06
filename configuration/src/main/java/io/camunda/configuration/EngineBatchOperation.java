/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.zeebe.engine.EngineConfiguration;
import java.time.Duration;
import java.util.Set;

public class EngineBatchOperation {

  private static final String PREFIX = "camunda.processing.engine.batch-operations";

  private static final Set<String> LEGACY_SCHEDULER_INTERVAL_PROPERTIES =
      Set.of("zeebe.broker.experimental.engine.batchOperations.schedulerInterval");

  /**
   * The interval at which the batch operation scheduler runs. Defaults to {@link
   * EngineConfiguration#DEFAULT_BATCH_OPERATION_SCHEDULER_INTERVAL}.
   */
  private Duration schedulerInterval =
      EngineConfiguration.DEFAULT_BATCH_OPERATION_SCHEDULER_INTERVAL;

  public Duration getSchedulerInterval() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".scheduler-interval",
        schedulerInterval,
        Duration.class,
        UnifiedConfigurationHelper.BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_SCHEDULER_INTERVAL_PROPERTIES);
  }

  public void setSchedulerInterval(final Duration schedulerInterval) {
    this.schedulerInterval = schedulerInterval;
  }
}
