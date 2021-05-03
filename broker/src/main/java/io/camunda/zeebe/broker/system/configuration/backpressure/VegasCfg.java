/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.broker.system.configuration.backpressure;

import static io.zeebe.broker.system.configuration.ConfigurationUtil.checkPositive;

public class VegasCfg {

  private int alpha = 3;
  private int beta = 6;
  private int initialLimit = 20;

  public int getAlpha() {
    return alpha;
  }

  public void setAlpha(final int alpha) {
    checkPositive(alpha, "alpha");
    this.alpha = alpha;
  }

  public int getBeta() {
    return beta;
  }

  public void setBeta(final int beta) {
    checkPositive(beta, "beta");
    this.beta = beta;
  }

  public int getInitialLimit() {
    return initialLimit;
  }

  public void setInitialLimit(final int initialLimit) {
    checkPositive(initialLimit, "initialLimit");
    this.initialLimit = initialLimit;
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
