/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration.engine;

import static io.camunda.zeebe.engine.EngineConfiguration.*;

import io.camunda.zeebe.broker.system.configuration.ConfigurationEntry;
import java.time.Duration;

public class DistributionCfg implements ConfigurationEntry {

  private boolean pauseCommandDistribution = DEFAULT_COMMAND_DISTRIBUTION_PAUSED;
  private Duration commandRedistributionInterval = DEFAULT_COMMAND_REDISTRIBUTION_INTERVAL;
  private Duration retryMaxBackoffDuration =
      DEFAULT_COMMAND_REDISTRIBUTION_RETRY_MAX_BACKOFF_DURATION;

  public boolean isPauseCommandDistribution() {
    return pauseCommandDistribution;
  }

  public void setPauseCommandDistribution(final boolean pauseCommandDistribution) {
    this.pauseCommandDistribution = pauseCommandDistribution;
  }

  public Duration getCommandRedistributionInterval() {
    return commandRedistributionInterval;
  }

  public void setCommandRedistributionInterval(final Duration commandRedistributionInterval) {
    this.commandRedistributionInterval = commandRedistributionInterval;
  }

  public Duration getRetryMaxBackoffDuration() {
    return retryMaxBackoffDuration;
  }

  public void setRetryMaxBackoffDuration(final Duration retryMaxBackoffDuration) {
    this.retryMaxBackoffDuration = retryMaxBackoffDuration;
  }

  @Override
  public String toString() {
    return "DistributionCfg{"
        + "pauseCommandDistribution="
        + pauseCommandDistribution
        + ", commandRedistributionInterval="
        + commandRedistributionInterval
        + ", retryMaxBackoffDuration="
        + retryMaxBackoffDuration
        + '}';
  }
}
