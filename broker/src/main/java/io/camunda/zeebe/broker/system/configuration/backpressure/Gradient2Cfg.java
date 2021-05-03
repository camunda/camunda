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

public class Gradient2Cfg {

  private int minLimit = 10;
  private int initialLimit = 20;
  private double rttTolerance = 2.0;
  private int longWindow = 600;

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

  public int getLongWindow() {
    return longWindow;
  }

  public void setLongWindow(final int longWindow) {
    checkPositive(longWindow, "longWindow");
    this.longWindow = longWindow;
  }

  @Override
  public String toString() {
    return "Gradient2Cfg{"
        + "minLimit="
        + minLimit
        + ", initialLimit="
        + initialLimit
        + ", rttTolerance="
        + rttTolerance
        + ", longWindow="
        + longWindow
        + '}';
  }
}
