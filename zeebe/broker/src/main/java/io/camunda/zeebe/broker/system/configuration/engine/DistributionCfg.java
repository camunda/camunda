/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.configuration.engine;

import static io.camunda.zeebe.engine.EngineConfiguration.DEFAULT_COMMAND_DISTRIBUTION_PAUSED;

import io.camunda.zeebe.broker.system.configuration.ConfigurationEntry;

public class DistributionCfg implements ConfigurationEntry {

  private boolean pauseCommandDistribution = DEFAULT_COMMAND_DISTRIBUTION_PAUSED;

  public boolean isPauseCommandDistribution() {
    return pauseCommandDistribution;
  }

  public void setPauseCommandDistribution(final boolean pauseCommandDistribution) {
    this.pauseCommandDistribution = pauseCommandDistribution;
  }

  @Override
  public String toString() {
    return "DistributionCfg{" + "pauseCommandDistribution=" + pauseCommandDistribution + '}';
  }
}
