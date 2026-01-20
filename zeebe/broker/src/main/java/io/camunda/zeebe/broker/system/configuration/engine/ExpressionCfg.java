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
import java.time.Duration;

public final class ExpressionCfg implements ConfigurationEntry {

  private Duration timeout = EngineConfiguration.DEFAULT_EXPRESSION_EVALUATION_TIMEOUT;

  @Override
  public void init(final BrokerCfg globalConfig, final String brokerBase) {
    if (timeout == null || !timeout.isPositive()) {
      throw new IllegalArgumentException(
          "Expression timeout must be positive but was %s".formatted(timeout));
    }
  }

  public Duration getTimeout() {
    return timeout;
  }

  public void setTimeout(final Duration timeout) {
    this.timeout = timeout;
  }

  @Override
  public String toString() {
    return "ExpressionCfg{" + "timeout=" + timeout + '}';
  }
}
