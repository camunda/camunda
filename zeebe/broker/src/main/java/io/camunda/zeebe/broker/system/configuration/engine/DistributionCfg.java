/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration.engine;

import static io.camunda.zeebe.engine.EngineConfiguration.DEFAULT_COMMAND_DISTRIBUTION_PAUSED;

import io.camunda.zeebe.broker.system.configuration.ConfigurationEntry;

public class DistributionCfg implements ConfigurationEntry {

  private boolean isCommandDistributionPaused = DEFAULT_COMMAND_DISTRIBUTION_PAUSED;

  public boolean isCommandDistributionPaused() {
    return isCommandDistributionPaused;
  }

  public void setCommandDistributionPaused(final boolean commandDistributionPaused) {
    isCommandDistributionPaused = commandDistributionPaused;
  }

  @Override
  public String toString() {
    return "DistributionCfg{" + "isCommandDistributionPaused=" + isCommandDistributionPaused + '}';
  }
}
