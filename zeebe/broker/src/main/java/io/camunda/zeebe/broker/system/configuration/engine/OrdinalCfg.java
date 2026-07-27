/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration.engine;

import io.camunda.zeebe.broker.system.configuration.ConfigurationEntry;
import io.camunda.zeebe.engine.EngineConfiguration;
import java.time.Duration;

public class OrdinalCfg implements ConfigurationEntry {

  private Duration rolloverEvaluationInterval =
      EngineConfiguration.DEFAULT_ORDINAL_ROLLOVER_EVALUATION_INTERVAL;

  public Duration getRolloverEvaluationInterval() {
    return rolloverEvaluationInterval;
  }

  public void setRolloverEvaluationInterval(final Duration rolloverEvaluationInterval) {
    this.rolloverEvaluationInterval = rolloverEvaluationInterval;
  }

  @Override
  public String toString() {
    return "OrdinalCfg{" + "rolloverEvaluationInterval=" + rolloverEvaluationInterval + '}';
  }
}
