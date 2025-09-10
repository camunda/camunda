/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.configuration.engine;

import static io.camunda.zeebe.engine.EngineConfiguration.DEFAULT_COMMAND_DISTRIBUTION_PAUSED;
import static io.camunda.zeebe.engine.EngineConfiguration.DEFAULT_COMMAND_REDISTRIBUTION_INTERVAL;
import static io.camunda.zeebe.engine.EngineConfiguration.DEFAULT_COMMAND_REDISTRIBUTION_MAX_BACKOFF_DURATION;

import io.camunda.zeebe.broker.system.configuration.ConfigurationEntry;
import java.time.Duration;

public class DistributionCfg implements ConfigurationEntry {

  private boolean pauseCommandDistribution = DEFAULT_COMMAND_DISTRIBUTION_PAUSED;
  private Duration redistributionInterval = DEFAULT_COMMAND_REDISTRIBUTION_INTERVAL;
  private Duration maxBackoffDuration = DEFAULT_COMMAND_REDISTRIBUTION_MAX_BACKOFF_DURATION;

  public boolean isPauseCommandDistribution() {
    return pauseCommandDistribution;
  }

  public void setPauseCommandDistribution(final boolean pauseCommandDistribution) {
    this.pauseCommandDistribution = pauseCommandDistribution;
  }

  public Duration getRedistributionInterval() {
    return redistributionInterval;
  }

  public void setRedistributionInterval(final Duration redistributionInterval) {
    this.redistributionInterval = redistributionInterval;
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
        + ", redistributionInterval="
        + redistributionInterval
        + ", maxBackoffDuration="
        + maxBackoffDuration
        + '}';
  }
}
