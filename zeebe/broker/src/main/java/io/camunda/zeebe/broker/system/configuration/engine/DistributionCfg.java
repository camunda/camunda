/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration.engine;

import static io.camunda.zeebe.engine.EngineConfiguration.DEFAULT_COMMAND_DISTRIBUTION_PAUSED;
import static io.camunda.zeebe.engine.EngineConfiguration.DEFAULT_COMMAND_REDISTRIBUTION_INTERVAL;
import static io.camunda.zeebe.engine.EngineConfiguration.DEFAULT_COMMAND_REDISTRIBUTION_MAX_BACKOFF;

import io.camunda.zeebe.broker.system.configuration.ConfigurationEntry;
import java.time.Duration;

public class DistributionCfg implements ConfigurationEntry {

  private boolean pauseCommandDistribution = DEFAULT_COMMAND_DISTRIBUTION_PAUSED;
  private Duration retryInterval = DEFAULT_COMMAND_REDISTRIBUTION_INTERVAL;
  private Duration maxBackoffDuration = DEFAULT_COMMAND_REDISTRIBUTION_MAX_BACKOFF;

  public boolean isPauseCommandDistribution() {
    return pauseCommandDistribution;
  }

  public void setPauseCommandDistribution(final boolean pauseCommandDistribution) {
    this.pauseCommandDistribution = pauseCommandDistribution;
  }

  public Duration getRetryInterval() {
    return retryInterval;
  }

  public void setRetryInterval(final Duration retryInterval) {
    this.retryInterval = retryInterval;
  }

  public Duration getMaxBackoffDuration() {
    return maxBackoffDuration;
  }

  public void setMaxBackoffDuration(final Duration maxBackoffDuration) {
    this.maxBackoffDuration = maxBackoffDuration;
  }

  @Override
  public String toString() {
    return "DistributionCfg{"
        + "pauseCommandDistribution="
        + pauseCommandDistribution
        + ", retryInterval="
        + retryInterval
        + ", maxBackoffDuration="
        + maxBackoffDuration
        + '}';
  }
}
