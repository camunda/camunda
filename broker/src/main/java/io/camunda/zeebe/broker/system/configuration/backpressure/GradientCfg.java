/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.broker.system.configuration.backpressure;

import static com.google.common.base.Preconditions.checkArgument;
import static io.zeebe.broker.system.configuration.ConfigurationUtil.checkPositive;

public class GradientCfg {

  private int minLimit = 10;
  private int initialLimit = 20;
  private double rttTolerance = 2.0;

  public int getMinLimit() {
    return minLimit;
  }

  public void setMinLimit(final int minLimit) {
    checkPositive(minLimit, "minLimit");
    this.minLimit = minLimit;
  }

  public int getInitialLimit() {
    return initialLimit;
  }

  public void setInitialLimit(final int initialLimit) {
    checkPositive(initialLimit, "initialLimit");
    this.initialLimit = initialLimit;
  }

  public double getRttTolerance() {
    return rttTolerance;
  }

  public void setRttTolerance(final double rttTolerance) {
    checkArgument(
        rttTolerance >= 1.0, "Expected rttTolerance to be >= 1.0, but found %s", rttTolerance);
    this.rttTolerance = rttTolerance;
  }

  @Override
  public String toString() {
    return "GradientCfg{"
        + "minLimit="
        + minLimit
        + ", initialLimit="
        + initialLimit
        + ", rttTolerance="
        + rttTolerance
        + '}';
  }
}
