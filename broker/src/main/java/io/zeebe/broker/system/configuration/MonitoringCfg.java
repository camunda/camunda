/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.system.configuration;

import io.zeebe.util.Environment;

public class MonitoringCfg implements ConfigurationEntry {
  private boolean tracing = true;

  @Override
  public void init(
      final BrokerCfg globalConfig, final String brokerBase, final Environment environment) {
    applyEnvironment(environment);
  }

  public boolean isTracing() {
    return tracing;
  }

  public void setTracing(final boolean tracing) {
    this.tracing = tracing;
  }

  private void applyEnvironment(final Environment environment) {
    environment.getBool(EnvironmentConstants.ENV_MONITORING_TRACING).ifPresent(this::setTracing);
  }
}
