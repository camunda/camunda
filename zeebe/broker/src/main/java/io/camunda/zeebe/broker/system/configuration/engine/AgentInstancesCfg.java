/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration.engine;

import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.ConfigurationEntry;
import io.camunda.zeebe.engine.EngineConfiguration;

public final class AgentInstancesCfg implements ConfigurationEntry {

  private int completionBatchLimit =
      EngineConfiguration.DEFAULT_AGENT_INSTANCE_COMPLETION_BATCH_LIMIT;

  @Override
  public void init(final BrokerCfg globalConfig, final String brokerBase) {
    if (completionBatchLimit < 1) {
      throw new IllegalArgumentException(
          "Agent instance completion batch limit must be at least 1 but was %d"
              .formatted(completionBatchLimit));
    }
  }

  public int getCompletionBatchLimit() {
    return completionBatchLimit;
  }

  public void setCompletionBatchLimit(final int completionBatchLimit) {
    this.completionBatchLimit = completionBatchLimit;
  }

  @Override
  public String toString() {
    return "AgentInstancesCfg{completionBatchLimit=" + completionBatchLimit + '}';
  }
}
