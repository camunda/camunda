/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.system.configuration;

import io.zeebe.util.Environment;

public class VegasCfg {

  private int alpha = 3;
  private int beta = 6;
  private int initialLimit = 20;

  public int getAlpha() {
    return alpha;
  }

  public void setAlpha(final int alpha) {
    this.alpha = alpha;
  }

  public int getBeta() {
    return beta;
  }

  public void setBeta(final int beta) {
    this.beta = beta;
  }

  public int getInitialLimit() {
    return initialLimit;
  }

  public void setInitialLimit(final int initialLimit) {
    this.initialLimit = initialLimit;
  }

  public void init(final Environment environment) {
    environment
        .getInt(EnvironmentConstants.ENV_BROKER_BACKPRESSURE_VEGAS_ALPHA)
        .ifPresent(this::setAlpha);
    environment
        .getInt(EnvironmentConstants.ENV_BROKER_BACKPRESSURE_VEGAS_BETA)
        .ifPresent(this::setBeta);
    environment
        .getInt(EnvironmentConstants.ENV_BROKER_BACKPRESSURE_VEGAS_INITIALLIMIT)
        .ifPresent(this::setInitialLimit);
  }

  @Override
  public String toString() {
    return "VegasCfg{"
        + "alpha="
        + alpha
        + ", beta="
        + beta
        + ", initialLimit="
        + initialLimit
        + '}';
  }
}
