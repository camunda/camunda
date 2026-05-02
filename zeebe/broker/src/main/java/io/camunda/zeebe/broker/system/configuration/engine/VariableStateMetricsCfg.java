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

public class VariableStateMetricsCfg implements ConfigurationEntry {
  private Duration interval = EngineConfiguration.DEFAULT_VARIABLE_STATE_METRICS_INTERVAL;
  private boolean enabled = EngineConfiguration.DEFAULT_VARIABLE_STATE_METRICS_ENABLED;

  public Duration getInterval() {
    return interval;
  }

  public void setInterval(final Duration interval) {
    this.interval = interval;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  @Override
  public String toString() {
    return "VariableStateMetricsCfg{" + "interval=" + interval + ", enabled=" + enabled + '}';
  }
}
